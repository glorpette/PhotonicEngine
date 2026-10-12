package at.redi2go.photonics.engine.rendering.world.allocator.heap;

import at.redi2go.photonics.game.blaze3d.buffers.BufferUsage;
import at.redi2go.photonics.game.blaze3d.buffers.IGpuBuffer;
import at.redi2go.photonics.game.blaze3d.systems.ICommandEncoder;
import at.redi2go.photonics.game.blaze3d.systems.IGpuDevice;
import at.redi2go.photonics.game.blaze3d.systems.IRenderSystem;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.lwjgl.system.MemoryUtil;

public class DefaultGpuBufferHeap extends AbstractGpuBufferHeap {
    private final IGpuBuffer gpuBuffer;
    private final ByteBuffer buffer;

    private final Queue<MemorySlice> uploadQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;

    public DefaultGpuBufferHeap(
            IGpuDevice device,
            @Nullable Supplier<String> label,
            long byteSize,
            @BufferUsage int usage
    ) {
        // A heap is uploaded through ICommandEncoder.writeToBuffer, so it always needs COPY_DST.
        // Preserve the caller's binding flags (for example UNIFORM) while adding upload support.
        var gpuBuffer = device.ph$createBuffer(label, usage | BufferUsage.COPY_DST, byteSize);
        ByteBuffer stagingBuffer;
        try {
            stagingBuffer = MemoryUtil.memAlloc(Math.toIntExact(byteSize))
                    .order(ByteOrder.nativeOrder());
        } catch (Throwable failure) {
            gpuBuffer.close();
            throw failure;
        }
        this.gpuBuffer = gpuBuffer;
        this.buffer = stagingBuffer;
    }

    public IGpuBuffer buffer() {
        checkHeapOpen();
        return gpuBuffer;
    }

    @Override
    public long capacity() {
        return gpuBuffer.ph$size();
    }

    @Nullable
    @Override
    public MemoryView allocate(long byteSize) {
        if (closed) return null;
        long ptr = allocatePtr(byteSize);

        return ptr == -1 ? null : new Allocation(ptr, byteSize);
    }

    @Nullable
    @Override
    public IGpuBufferHeap allocateHeap(long byteSize) {
        if (closed) return null;
        long ptr = allocatePtr(byteSize);

        return ptr == -1 ? null : new SubHeap(this, ptr, byteSize, ptr);
    }

    @Override
    public void upload() {
        if (closed) return;
        List<MemorySlice> pending = new ArrayList<>();
        MemorySlice slice;
        while ((slice = uploadQueue.poll()) != null)
            pending.add(slice);
        var regionsToUpload = MemorySlice.mergeNeighbors(pending);
        ICommandEncoder encoder = IRenderSystem.getDevice().ph$createCommandEncoder();

        for (var region : regionsToUpload) {
            encoder.ph$writeToBuffer(
                    gpuBuffer.ph$slice(region.begin(), region.length()),
                    buffer.slice((int) region.begin(), (int) region.length())
            );
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        uploadQueue.clear();
        try {
            gpuBuffer.close();
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Override
    protected int getIntAt(long byteOffset) {
        checkHeapOpen();
        return buffer.getInt(Math.toIntExact(byteOffset));
    }

    @Override
    protected void putIntAt(long byteOffset, int value) {
        checkHeapOpen();
        buffer.putInt(Math.toIntExact(byteOffset), value);
    }

    @Override
    protected void checkHeapOpen() {
        if (closed) throw new IllegalStateException("GPU buffer heap is closed");
    }

    @Override
    protected void initializeRange(long byteOffset, long byteLength) {
        checkHeapOpen();
        MemoryUtil.memSet(
                MemoryUtil.memAddress(buffer, Math.toIntExact(byteOffset)),
                0,
                Math.toIntExact(byteLength)
        );
    }

    private class Allocation extends AbstractAllocation {
        private Allocation(long begin, long length) {
            super(begin, length);
        }

        @Override
        public ByteBuffer buffer() {
            checkHeapOpen();
            return buffer.slice(Math.toIntExact(begin()), Math.toIntExact(size()))
                    .order(buffer.order());
        }

        @Override
        public void upload() {
            uploadQueue.add(new UploadRange(begin(), end()));
        }
    }

    private class SubHeap extends AbstractSubheap implements Region, IGpuBufferHeap {
        protected SubHeap(AbstractGpuBufferHeap root, long begin, long length, long freeBegin) {
            super(root, begin, length, freeBegin);
        }

        @Override
        public IGpuBuffer buffer() {
            checkHeapOpen();
            return gpuBuffer;
        }

        @Override
        protected ByteBuffer createSlice(long begin, long length) {
            checkHeapOpen();
            return buffer.slice(Math.toIntExact(begin), Math.toIntExact(length))
                    .order(buffer.order());
        }

        @Override
        protected void uploadSlice(Region region) {
            uploadQueue.add(new UploadRange(region.begin(), region.end()));
        }

        @Override
        protected IGpuBufferHeap createHeap(long begin, long length, long freeBegin) {
            return new SubHeap(this, begin, length, freeBegin);
        }
    }

    private record UploadRange(long begin, long end) implements MemorySlice { }
}
