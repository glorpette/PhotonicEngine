package at.redi2go.photonics.engine.rendering;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.engine.PrioritizedTask;
import at.redi2go.photonics.engine.RenderingComponent;
import at.redi2go.photonics.game.minecraft.IMinecraft;
import at.redi2go.photonics.game.minecraft.world.level.ILevel;
import at.redi2go.photonics.game.minecraft.world.level.chunk.IChunkSection;
import at.redi2go.photonics.engine.config.PhConfig;
import at.redi2go.photonics.engine.collect.EmptyQueue;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

public class SectionManager implements RenderingComponent {
    private Set<Vector2i> loadedChunks = Set.of();
    private final Set<Vector3i> notEmptySections = ConcurrentHashMap.newKeySet();
    private final Queue<Vector3i> retryQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vector3i> pendingCopyPositions = ConcurrentHashMap.newKeySet();
    private final Set<Vector3i> queuedRetries = ConcurrentHashMap.newKeySet();

    private final List<Queue<Vector3i>> unloadQueues = new ArrayList<>();
    private final List<TaskQueue<?>> taskQueues = new ArrayList<>();

    private final IntSupplier renderDistanceSupplier;
    private long remeshCount = 0;
    private volatile boolean closed;

    public SectionManager(IntSupplier renderDistanceSupplier) {
        this.renderDistanceSupplier = renderDistanceSupplier;
    }

    private Vector3i lastCameraPos = null;
    private final Vector3i cameraPosScratch = new Vector3i();
    private int lastRenderDistance = -1;

    private void queueUnload(Vector3i section) {
        for (var unloadQueue : unloadQueues)
            unloadQueue.add(section);

        for (var taskQueue : taskQueues) {
            if (taskQueue.tracksUnloads()) {
                try {
                    taskQueue.offerUnload(section);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void queueSection(Vector3i sectionPos) {
        if (!notEmptySections.contains(sectionPos)) return;
        pendingCopyPositions.remove(sectionPos);
        pendingCopyPositions.add(new Vector3i(sectionPos));
    }

    private void enqueuePendingCopies(ILevel level) throws InterruptedException {
        int queued = 0;
        for (var taskQueue : taskQueues) {
            if (taskQueue instanceof SectionQueue sectionQueue) {
                while (queued < MAX_SECTION_COPIES_PER_FRAME && !pendingCopyPositions.isEmpty() && sectionQueue.hasAvailableCapacity()) {
                    var pos = pendingCopyPositions.iterator().next();
                    pendingCopyPositions.remove(pos);
                    if (!notEmptySections.contains(pos)) continue;
                    var copy = createCopy(pos, level);
                    if (copy.isPresent()) {
                        sectionQueue.offer(pos, copy.get());
                        queued++;
                    } else if (notEmptySections.remove(pos)) {
                        queueUnload(pos);
                    }
                }
            }
        }
    }

    private static final int MAX_SECTION_COPIES_PER_FRAME = 4;

    private void refreshSections( ILevel level) throws InterruptedException {
        getCameraChunkPos(cameraPosScratch);
        var cameraPos = cameraPosScratch;
        int rd = renderDistanceSupplier.getAsInt();

        if (rd == lastRenderDistance && Objects.equals(lastCameraPos, cameraPos)) return;

        Set<Vector2i> discoveredChunks = new HashSet<>();
        Set<Vector3i> discoveredSections = new HashSet<>();
        Set<Vector3i> unloadedSections = new HashSet<>();

        for (int px = -rd; px <= rd; px++) {
            for (int pz = -rd; pz <= rd; pz++) {
                int sectionX = cameraPos.x + px;
                int sectionZ = cameraPos.z + pz;

                discoveredChunks.add(new Vector2i(sectionX, sectionZ));

                var chunk = level.ph$getChunkOrNull(sectionX, sectionZ);
                if (chunk == null) continue;

                IChunkSection[] sections = chunk.ph$sections();

                for (int py = -rd; py <= rd; py++) {
                    int sectionY = cameraPos.y + py;
                    int sectionIndex = level.ph$getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0) continue;
                    if (sectionIndex >= sections.length) break;

                    var section = sections[sectionIndex];
                    if (section.ph$hasOnlyAir()) continue;

                    var sectionCoord = new Vector3i(sectionX, sectionY, sectionZ);

                    discoveredSections.add(sectionCoord);

                    if (!notEmptySections.contains(sectionCoord))
                        pendingCopyPositions.add(sectionCoord);
                }
            }
        }

        int maxSections = PhConfig.getMaxRaytraceSections();
        if (discoveredSections.size() > maxSections) {
            var nearestSections = new ArrayList<>(discoveredSections);
            nearestSections.sort(Comparator
                    .comparingLong((Vector3i section) -> section.gridDistance(cameraPos))
                    .thenComparingInt(section -> section.x)
                    .thenComparingInt(section -> section.y)
                    .thenComparingInt(section -> section.z));
            discoveredSections.clear();
            discoveredSections.addAll(nearestSections.subList(0, maxSections));
        }

        for (var itr = notEmptySections.iterator(); itr.hasNext(); ) {
            var section = itr.next();
            if (discoveredSections.contains(section)) continue;

            itr.remove();
            unloadedSections.add(section);
            pendingCopyPositions.remove(section);
            queuedRetries.remove(section);
        }

        notEmptySections.addAll(discoveredSections);

        loadedChunks = discoveredChunks;

        if (lastCameraPos == null) lastCameraPos = new Vector3i(cameraPos);
        else lastCameraPos.set(cameraPos);
        lastRenderDistance = rd;

        for (var unloadQueue : unloadQueues)
            unloadQueue.addAll(unloadedSections);

        for (var taskQueue : taskQueues) {
            taskQueue.lock.lockInterruptibly();

            try {
                if (taskQueue.tracksUnloads())
                    taskQueue.offerUnloads(unloadedSections);
            } finally {
                taskQueue.lock.unlock();
            }
        }
    }

    public Queue<Vector3i> newUnloadQueue() {
        var queue = new ConcurrentLinkedQueue<Vector3i>();
        unloadQueues.add(queue);

        return queue;
    }

    public SectionQueue newSectionQueue(boolean trackUnloads) {
        var queue = new SectionQueue(64, trackUnloads);
        taskQueues.add(queue);

        return queue;
    }

    public <T> TaskQueue<T> newTaskQueue(int maxCapacity, boolean trackUnloads) {
        var queue = new TaskQueue<T>(maxCapacity, trackUnloads);
        taskQueues.add(queue);

        return queue;
    }

    public void requestRetry(Vector3i sectionPos) {
        if (closed) return;
        var key = new Vector3i(sectionPos);
        if (queuedRetries.add(key))
            retryQueue.add(key);
    }

    public void requestRetryAll() {
        if (closed) return;
        for (var sectionPos : notEmptySections)
            requestRetry(sectionPos);
    }

    private Optional<SectionCopy> createCopy(Vector3i sectionPos, ILevel level) {
        var chunk = level.ph$getChunkOrNull(sectionPos.x, sectionPos.z);
        if (chunk == null) return Optional.empty();

        var sections = chunk.ph$sections();

        var sectionIndex = level.ph$getSectionIndexFromSectionY(sectionPos.y);
        if (sectionIndex < 0) return Optional.empty();
        if (sectionIndex >= sections.length) return Optional.empty();

        var section = sections[sectionIndex];
        if (section.ph$hasOnlyAir()) return Optional.empty();

        return Optional.of(new SectionCopy(sectionPos, section, remeshCount++));
    }

    @Override
    public void onFrameBegin() {
        ILevel level = IMinecraft.getLevel();
        if (level == null) return;

        try {
            refreshSections(level);
            int retried = 0;
            Vector3i sectionPos;
            while (retried < MAX_SECTION_COPIES_PER_FRAME && (sectionPos = retryQueue.peek()) != null) {
                if (taskQueues.stream().filter(SectionQueue.class::isInstance).map(SectionQueue.class::cast).noneMatch(SectionQueue::hasAvailableCapacity))
                    break;
                retryQueue.poll();
                queuedRetries.remove(sectionPos);
                if (!notEmptySections.contains(sectionPos)) continue;

                var copy = createCopy(sectionPos, level);
                if (copy.isPresent()) {
                    queueSection(sectionPos);
                    pendingCopyPositions.remove(sectionPos);
                    var section = copy.get();
                    for (var taskQueue : taskQueues) {
                        if (taskQueue instanceof SectionQueue sectionQueue && sectionQueue.hasAvailableCapacity()) {
                            sectionQueue.offer(sectionPos, section);
                            retried++;
                        }
                    }
                }
                else if (notEmptySections.remove(sectionPos))
                    queueUnload(sectionPos);
            }
            enqueuePendingCopies(level);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        closed = true;
        retryQueue.clear();
        queuedRetries.clear();
        pendingCopyPositions.clear();
        for (var queue : taskQueues)
            queue.close();
        taskQueues.clear();
        unloadQueues.clear();
        notEmptySections.clear();
        loadedChunks = Set.of();
    }

    @Override
    public void onSectionAdded(int x, int y, int z) {
        ILevel level = IMinecraft.getLevel();
        if (level == null) return;

        try {
            refreshSections(level);
            Vector3i sectionPos = new Vector3i(x, y, z);

            if (!loadedChunks.contains(new Vector2i(x, z))) return;
            if (notEmptySections.contains(sectionPos)) return;
            if (notEmptySections.size() >= PhConfig.getMaxRaytraceSections()) return;

            notEmptySections.add(sectionPos);
            queueSection(sectionPos);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onSectionChanged(int x, int y, int z) {
        ILevel level = IMinecraft.getLevel();
        if (level == null) return;

        Vector3i sectionPos = new Vector3i(x, y, z);
        if (!notEmptySections.contains(sectionPos)) {
            onSectionAdded(x, y, z);
            return;
        }

        queueSection(sectionPos);
    }

    public static Vector3i getCameraChunkPos() {
        return getCameraChunkPos(new Vector3i());
    }

    private static Vector3i getCameraChunkPos(Vector3i destination) {
        var cameraPos = IMinecraft.getCameraPos();
        return destination.set(
                (int) cameraPos.x >> 4,
                (int) cameraPos.y >> 4,
                (int) cameraPos.z >> 4
        );
    }

    public class TaskQueue<V> {
        private final Lock lock = new ReentrantLock();

        private final Condition notEmptyCondition = lock.newCondition();
        private final Condition notFullCondition = lock.newCondition();

        private int size = 0;
        private final int capacity;

        private SectionInfo[] sectionQueue;
        private final Map<Vector3i, V> sectionValues = new HashMap<>();

        private final Queue<Vector3i> unloadedQueue;

        private Vector3i lastCameraPos = null;
        private final Vector3i cameraPosScratch = new Vector3i();
        private int lastRenderDistance = 0;
        private boolean newPending = false;
        private int mod = 0;
        private boolean closed = false;

        private TaskQueue(int maxSize, boolean trackUnloads) {
            this.capacity = maxSize;
            this.sectionQueue = new SectionInfo[maxSize > 0 ? maxSize : 24];

            unloadedQueue = trackUnloads ? new ArrayDeque<>() : EmptyQueue.of();
        }

        private boolean tracksUnloads() {
            return unloadedQueue != EmptyQueue.<Vector3i>of();
        }

        public void awaitTask() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    if (closed || size != 0 || !unloadedQueue.isEmpty()) return;

                    notEmptyCondition.await();
                }
            } finally {
                lock.unlock();
            }
        }

        public void close() {
            lock.lock();
            try {
                if (closed) return;
                closed = true;
                for (var value : sectionValues.values())
                    tryClose(value);
                sectionValues.clear();
                Arrays.fill(sectionQueue, null);
                size = 0;
                unloadedQueue.clear();
                notEmptyCondition.signalAll();
                notFullCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public boolean hasAvailableCapacity() {
            lock.lock();
            try {
                return !closed && (capacity <= 0 || size < capacity);
            } finally {
                lock.unlock();
            }
        }


        public List<Vector3i> drainUnloadQueue() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                if (unloadedQueue.isEmpty()) return List.of();

                List<Vector3i> result = new ArrayList<>(unloadedQueue.size());

                while (!unloadedQueue.isEmpty()) {
                    var section = unloadedQueue.remove();
                    if (notEmptySections.contains(section)) continue;

                    result.add(section);
                }

                return result;
            } finally {
                lock.unlock();
            }
        }

        public Optional<V> take() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                if (size == 0) return Optional.empty();

                sortSections();
                if (size == 0) return Optional.empty();

                notFullCondition.signalAll();
                return Optional.of(removeFirst());
            } finally {
                lock.unlock();
            }
        }

        public List<V> drain(int maxCount) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                if (size == 0) return List.of();

                sortSections();
                if (size == 0) return List.of();

                int count = Math.min(maxCount, size);
                var result = new ArrayList<V>(count);

                for (int i = 0; i < count; i++)
                    result.add(removeFirst());

                notFullCondition.signalAll();

                return result;
            } finally {
                lock.unlock();
            }
        }


        public boolean offer(Vector3i sectionCoord, V element) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (!closed && size == capacity)
                    notFullCondition.await();

                if (closed) {
                    tryClose(element);
                    return false;
                }

                if (!notEmptySections.contains(sectionCoord)) {
                    tryClose(element);
                    return false;
                }

                boolean[] shouldAppendSection = {false};
                sectionValues.compute(sectionCoord, (k, previous) -> {
                    if (previous == null) {
                        shouldAppendSection[0] = true;
                        return element;
                    }

                    long previousPriority = previous instanceof PrioritizedTask p1 ? p1.priority() : Long.MIN_VALUE;
                    long newPriority = element instanceof PrioritizedTask p2 ? p2.priority() : Long.MIN_VALUE;

                    if (previousPriority > newPriority) {
                        tryClose(element);
                        return previous;
                    } else {
                        tryClose(previous);
                        return element;
                    }
                });

                if (!shouldAppendSection[0]) return true;

                requireCapacity(size + 1);

                sectionQueue[size++] = new SectionInfo(sectionCoord);

                newPending = true;
                notEmptyCondition.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }

        public void offerMany(List<Pair<Vector3i, V>> elements) throws InterruptedException {
            if (elements.isEmpty()) return;
            lock.lockInterruptibly();

            try {
                if (closed) {
                    elements.forEach(element -> tryClose(element.right()));
                    return;
                }
                int index = 0;
                while (index < elements.size()) {
                    while (size == capacity)
                        notFullCondition.await();

                    int maxRemainingSlots = capacity > 0
                            ? Math.min(capacity - size, elements.size() - index)
                            : elements.size() - index;
                    requireCapacity(size + maxRemainingSlots);

                    boolean changed = false;
                    for (int i = 0; i < maxRemainingSlots; i++) {
                        var elementPair = elements.get(index + i);

                        var sectionCoord = elementPair.left();
                        var element = elementPair.right();

                        if (!notEmptySections.contains(sectionCoord)) {
                            tryClose(element);
                            continue;
                        }

                        var previousValue = sectionValues.putIfAbsent(sectionCoord, element);
                        if (previousValue != null) {
                            long previousPriority = previousValue instanceof PrioritizedTask p1 ? p1.priority() : Long.MIN_VALUE;
                            long newPriority = element instanceof PrioritizedTask p2 ? p2.priority() : Long.MIN_VALUE;

                            if (previousPriority > newPriority) {
                                tryClose(element);
                            } else if (sectionValues.replace(sectionCoord, previousValue, element)) {
                                tryClose(previousValue);
                            } else {
                                tryClose(element);
                            }
                            continue;
                        }

                        sectionQueue[size++] = new SectionInfo(sectionCoord);
                        changed = true;
                    }

                    if (changed) {
                        newPending = true;
                        notEmptyCondition.signalAll();
                    }

                    index += maxRemainingSlots;
                }
            } finally {
                lock.unlock();
            }
        }

        private void offerUnload(Vector3i section) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                if (closed) return;
                if (unloadedQueue.add(section))
                    notEmptyCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void offerUnloads(Collection<Vector3i> sections) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                if (closed) return;
                if (unloadedQueue.addAll(sections))
                    notEmptyCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void requireCapacity(int newSize) {
            if (capacity > 0) return;
            if (newSize < sectionQueue.length) return;

            var newCapacity = Math.max(newSize, sectionQueue.length << 1);
            sectionQueue = Arrays.copyOf(sectionQueue, newCapacity);
        }

        private void trimUnloadedSections() {
            int newSize = 0;

            for (int i = 0; i < size; i++) {
                var entry = sectionQueue[i];
                sectionQueue[i] = null;

                if (!notEmptySections.contains(entry.pos)) {
                    tryClose(sectionValues.remove(entry.pos));
                    continue;
                }

                sectionQueue[newSize++] = entry;
            }

            if (size != newSize && capacity > 0)
                notFullCondition.signalAll();

            size = newSize;
        }

        private void sortSections() {
            getCameraChunkPos(cameraPosScratch);
            var cameraChunkPos = cameraPosScratch;

            if (
                    !newPending &&
                            SectionManager.this.lastRenderDistance == lastRenderDistance &&
                            Objects.equals(cameraChunkPos, lastCameraPos)
            ) return;

            newPending = false;
            if (lastCameraPos == null) lastCameraPos = new Vector3i(cameraChunkPos);
            else lastCameraPos.set(cameraChunkPos);
            lastRenderDistance = SectionManager.this.lastRenderDistance;

            trimUnloadedSections();

            mod++;
            Arrays.parallelSort(
                    sectionQueue,
                    0,
                    size,
                    (p1, p2) -> Long.compare(p2.distance(cameraChunkPos, mod), p1.distance(cameraChunkPos, mod))
            );
        }

        private V removeFirst() {
            var top = sectionQueue[--size];
            sectionQueue[size] = null;

            return Objects.requireNonNull(sectionValues.remove(top.pos));
        }

        private static void tryClose(Object value) {
            if (value instanceof Disposable disposable)
                disposable.close();
        }

    }

    public class SectionQueue extends TaskQueue<SectionCopy> {
        private SectionQueue(int maxSize, boolean trackUnloads) {
            super(maxSize, trackUnloads);
        }
    }

    private static class SectionInfo {
        private int mod = -1;
        private long distance = 0;
        public final Vector3i pos;

        public SectionInfo(Vector3i section) {
            this.pos = section;
        }

        public long distance(Vector3i cameraPos, int mod) {
            if (this.mod == mod) return distance;

            this.distance = pos.gridDistance(cameraPos);
            this.mod = mod;

            return distance;
        }
    }
}

