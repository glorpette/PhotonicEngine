package at.redi2go.photonics.engine.rendering.world.allocator.heap;

import at.redi2go.photonics.game.Disposable;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * A buffer heap with best-fit allocation and adjacent-region coalescing.
 */
public abstract class AbstractGpuBufferHeap implements IGpuBufferHeap {
    private long index = 0;
    private final NavigableMap<Long, Long> freeRegions = new TreeMap<>();
    private final NavigableMap<Long, TreeSet<Long>> freeRegionsBySize = new TreeMap<>();
    private long allocatedBytes = 0;
    private long highWaterBytes = 0;
    private long failedAllocations = 0;

    protected void checkHeapOpen() { }

    protected void initializeRange(long byteOffset, long byteLength) { }

    protected int getIntAt(long byteOffset) {
        throw new UnsupportedOperationException("Heap does not support direct indexed reads");
    }

    protected void putIntAt(long byteOffset, int value) {
        throw new UnsupportedOperationException("Heap does not support direct indexed writes");
    }

    protected synchronized long allocatePtr(long byteSize) {
        checkHeapOpen();
        if (byteSize <= 0)
            throw new IllegalArgumentException("Allocation size must be positive");

        var bestFit = freeRegionsBySize.ceilingEntry(byteSize);
        if (bestFit != null) {
            long bestBegin = bestFit.getValue().first();
            long end = removeFreeRegion(bestBegin);
            long allocatedEnd = bestBegin + byteSize;
            if (allocatedEnd < end)
                addFreeRegion(allocatedEnd, end);
            allocatedBytes += byteSize;
            highWaterBytes = Math.max(highWaterBytes, usedBytes());
            return bestBegin;
        }

        long begin = index;
        long end = index + byteSize;

        if (end < begin || end > capacity()) {
            failedAllocations++;
            return -1;
        }
        index = end;
        allocatedBytes += byteSize;
        highWaterBytes = Math.max(highWaterBytes, usedBytes());

        return begin;
    }

    private long usedBytes() {
        return allocatedBytes;
    }

    private synchronized void freeRegion(long begin, long end) {
        if (begin < 0 || end < begin || end > index)
            throw new IllegalArgumentException("Freed region is outside this heap");

        var lower = freeRegions.floorEntry(begin);
        if (lower != null) {
            if (lower.getValue() > begin)
                throw new IllegalStateException("Freed region overlaps an existing free region");
        }
        var firstFollowing = freeRegions.higherEntry(begin);
        if (firstFollowing != null && firstFollowing.getKey() < end)
            throw new IllegalStateException("Freed region overlaps an existing free region");

        allocatedBytes -= end - begin;
        if (lower != null) {
            if (lower.getValue() == begin) {
                begin = lower.getKey();
                removeFreeRegion(lower.getKey());
            }
        }

        var next = freeRegions.ceilingEntry(begin);
        while (next != null && next.getKey() <= end) {
            end = next.getValue();
            removeFreeRegion(next.getKey());
            next = freeRegions.ceilingEntry(begin);
        }

        if (end == index) {
            index = begin;
            while (true) {
                var previous = freeRegions.floorEntry(index);
                if (previous == null || previous.getValue() != index) break;
                index = previous.getKey();
                removeFreeRegion(previous.getKey());
            }
        } else {
            addFreeRegion(begin, end);
        }
    }

    private long removeFreeRegion(long begin) {
        long end = freeRegions.remove(begin);
        long size = end - begin;
        var starts = freeRegionsBySize.get(size);
        starts.remove(begin);
        if (starts.isEmpty())
            freeRegionsBySize.remove(size);
        return end;
    }

    private void addFreeRegion(long begin, long end) {
        freeRegions.put(begin, end);
        freeRegionsBySize.computeIfAbsent(end - begin, ignored -> new TreeSet<>()).add(begin);
    }

    @Override
    public synchronized Stats stats() {
        long largestFreeRegion = 0;
        for (var entry : freeRegions.entrySet()) {
            long size = entry.getValue() - entry.getKey();
            largestFreeRegion = Math.max(largestFreeRegion, size);
        }
        long tailBytes = capacity() - index;
        return new Stats(capacity(), allocatedBytes, capacity() - allocatedBytes,
                Math.max(largestFreeRegion, tailBytes), highWaterBytes, failedAllocations);
    }

    protected interface Region extends MemorySlice, Disposable {
        AbstractGpuBufferHeap owner();

        long _begin();

        void _setBegin(long value);

        static List<? extends MemorySlice> takeFrom(Queue<Region> queue) {
            var regions = new ArrayList<Region>();
            Region region;
            while ((region = queue.poll()) != null)
                regions.add(region);
            return MemorySlice.mergeNeighbors(regions);
        }

        default long freeBegin() {
            return begin();
        }

        @Override
        default long begin() {
            return _begin() & ~SIGN_BIT;
        }

        default boolean isClosed() {
            return (_begin() & SIGN_BIT) != 0;
        }

        @Override
        @SuppressWarnings("resource")
        default void close() {
            synchronized (this) {
                if ((_begin() & SIGN_BIT) != 0) return;
                _setBegin(_begin() | SIGN_BIT);
            }

            owner().freeRegion(freeBegin(), freeBegin() + length());
        }
    }

    protected abstract class AbstractAllocation implements Region, MemoryView {
        private long begin;
        private final long end;

        protected AbstractAllocation(long begin, long length) {
            this(begin, length, begin);
        }

        protected AbstractAllocation(long begin, long length, long freeBegin) {
            this.begin = begin;
            this.end = begin + length;
            this.freeBegin = freeBegin;
            initializeRange(begin, length);
        }

        private final long freeBegin;

        @Override
        public AbstractGpuBufferHeap owner() {
            return AbstractGpuBufferHeap.this;
        }

        @Override
        public long _begin() {
            return begin;
        }

        @Override
        public void _setBegin(long value) {
            begin = value;
        }

        @Override
        public long begin() {
            return Region.super.begin();
        }

        @Override
        public long end() {
            return end;
        }

        @Override
        public long freeBegin() {
            return freeBegin;
        }

        @Override
        public int getInt(int byteOffset) {
            if (byteOffset < 0 || byteOffset + Integer.BYTES > size())
                throw new IndexOutOfBoundsException(byteOffset);
            return AbstractGpuBufferHeap.this.getIntAt(begin() + byteOffset);
        }

        @Override
        public void putInt(int byteOffset, int value) {
            if (byteOffset < 0 || byteOffset + Integer.BYTES > size())
                throw new IndexOutOfBoundsException(byteOffset);
            AbstractGpuBufferHeap.this.putIntAt(begin() + byteOffset, value);
        }
    }

    protected abstract class AbstractSubheap extends AbstractGpuBufferHeap implements Region {
        private long begin;
        private final long end;

        private final AbstractGpuBufferHeap root;
        private final long ownerBegin;

        protected AbstractSubheap(AbstractGpuBufferHeap root, long begin, long length, long ownerBegin) {
            this.begin = begin;
            this.end = begin + length;

            this.root = root;
            this.ownerBegin = ownerBegin;
        }

        protected abstract ByteBuffer createSlice(long begin, long length);

        protected abstract void uploadSlice(Region region);

        protected abstract IGpuBufferHeap createHeap(long begin, long length, long freeBegin);

        @Override
        protected void checkHeapOpen() {
            if (isClosed()) throw new IllegalStateException("GPU buffer subheap is closed");
            root.checkHeapOpen();
        }

        @Override
        protected void initializeRange(long byteOffset, long byteLength) {
            root.initializeRange(byteOffset, byteLength);
        }

        @Override
        public AbstractGpuBufferHeap owner() {
            return root;
        }

        @Override
        public long _begin() {
            return begin;
        }

        @Override
        public void _setBegin(long value) {
            begin = value;
        }

        @Override
        public long end() {
            return end;
        }

        @Override
        public long freeBegin() {
            return ownerBegin;
        }

        @Override
        public long capacity() {
            return end - begin();
        }

        @Nullable
        @Override
        public MemoryView allocate(long byteSize) {
            long ptr = allocatePtr(byteSize);

            return ptr == -1 ? null : new HeapAllocation(ptr + begin, byteSize, ptr);
        }

        @Nullable
        @Override
        public IGpuBufferHeap allocateHeap(long byteSize) {
            long ptr = allocatePtr(byteSize);

            return ptr == -1 ? null : createHeap(ptr + begin, byteSize, ptr);
        }

        @Override
        public void upload() {
            root.upload();
        }

        @Override
        protected int getIntAt(long byteOffset) {
            return root.getIntAt(byteOffset);
        }

        @Override
        protected void putIntAt(long byteOffset, int value) {
            root.putIntAt(byteOffset, value);
        }

        private class HeapAllocation extends AbstractAllocation {
            private HeapAllocation(long begin, long length, long freeBegin) {
                super(begin, length, freeBegin);
            }

            @Override
            public ByteBuffer buffer() {
                return createSlice(begin(), Math.toIntExact(size()));
            }

            @Override
            public AbstractGpuBufferHeap owner() {
                return AbstractSubheap.this;
            }

            @Override
            public void upload() {
                uploadSlice(this);
            }
        }
    }

    private static final long SIGN_BIT = Long.MIN_VALUE;
}
