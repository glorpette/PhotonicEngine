package at.redi2go.photonics.engine.rendering.world.compiler;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.game.minecraft.IMinecraft;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import at.redi2go.photonics.game.minecraft.world.level.ILevel;
import at.redi2go.photonics.engine.Photonics;
import at.redi2go.photonics.engine.PrioritizedTask;
import at.redi2go.photonics.engine.RenderingComponent;
import at.redi2go.photonics.engine.rendering.SectionManager;
import at.redi2go.photonics.engine.rendering.world.bakery.BlockMesher;
import at.redi2go.photonics.engine.rendering.world.IgnoredInterruptedException;
import at.redi2go.photonics.engine.rendering.world.block.BlockModel;
import at.redi2go.photonics.engine.rendering.world.registry.WorldRegistry;
import at.redi2go.photonics.engine.rendering.world.registry.light.WorldLight;
import at.redi2go.photonics.engine.rendering.world.registry.light.WorldLightRegistry;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.TriConsumer;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkCompiler implements Runnable, RenderingComponent {
    private static final int THREAD_COUNT = 2;
    private static final long ALLOCATION_ERROR_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong LAST_ALLOCATION_ERROR_LOG_NANOS = new AtomicLong(Long.MIN_VALUE);

    private final Queue<Vector3i> unloadQueue;
    private final SectionManager.SectionQueue sectionQueue;
    private final SectionManager.TaskQueue<ChunkCompiler.BuildResult> builtSectionQueue;

    private final WorldRegistry worldRegistry;

    private final ConcurrentMap<Vector3i, Long> latestSection = new ConcurrentHashMap<>();
    private final ConcurrentMap<Vector3i, Long> sectionHashes = new ConcurrentHashMap<>();

    private final Thread[] threads = new Thread[THREAD_COUNT];

    public ChunkCompiler(
            SectionManager sectionManager,
            SectionManager.TaskQueue<ChunkCompiler.BuildResult> builtSectionQueue,
            WorldRegistry worldRegistry
    ) {
        this.unloadQueue = sectionManager.newUnloadQueue();
        this.sectionQueue = sectionManager.newSectionQueue(false);
        this.builtSectionQueue = builtSectionQueue;

        this.worldRegistry = worldRegistry;

        for (int i = 0; i < THREAD_COUNT; i++) {
            var thread = new Thread(this, "Photonic Chunk Compiler #" + i);
            threads[i] = thread;

            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                sectionQueue.awaitTask();
                var result = sectionQueue.take();

                if (result.isEmpty()) continue;

                var section = result.get();
                unloadChunks();

                ILevel level = IMinecraft.getLevel();
                if (level == null) continue;

                if (!isLatestSection(section.pos(), section.priority())) continue;

                // Computing the hash immediately is cheaper than meshing an entire section just to discard it
                long hash = section.computeSectionHash(level);
                if (isDuplicateSection(section.pos(), hash)) continue;

                var buildResult = new BuildResult(section.pos(), section.blockPos(), hash, section.priority());
                boolean setup = false;
                try {
                    BlockMesher.REGISTRY.setup();
                    setup = true;

                    section.forEachBlock(() -> !buildResult.failed(), (blockChunkOffset, blockPos, block) -> {
                        if (block.ph$isAir()) return;

                        BlockMesher.REGISTRY.get(block.ph$block())
                                .ifPresent(mesher -> {
                                    CompletionStage<@Nullable BlockModel> model;
                                    try {
                                        model = worldRegistry.blockModelRegistry().getBlockModel(
                                                mesher,
                                                new Vector3i(blockChunkOffset),
                                                blockPos,
                                                block,
                                                level
                                        );
                                    } catch (Throwable failure) {
                                        buildResult.recordFailure(failure);
                                        return;
                                    }

                                    buildResult.submitBlockFuture(
                                            blockChunkOffset.x(),
                                            blockChunkOffset.y(),
                                            blockChunkOffset.z(),
                                            block,
                                            model
                                    );
                                });
                    });
                } catch (RuntimeException | Error failure) {
                    buildResult.close();
                    throw failure;
                } finally {
                    if (setup)
                        BlockMesher.REGISTRY.teardown();
                }

                buildResult.awaitSubmission();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                if (IgnoredInterruptedException.shouldIgnore(t)) return;
                Photonics.LOGGER.warn("An exception was thrown during chunk compilation; the worker will continue", t);
            }
        }
    }

    private boolean isLatestSection(Vector3i pos, long priority) {
        return priority > latestSection.getOrDefault(pos, Long.MIN_VALUE);
    }

    private boolean setLatestSection(Vector3i pos, long priority) {
        return latestSection.compute(pos, (ignored, previous) -> {
            if (previous == null) return priority;
            if (priority > previous) return priority;

            return previous;
        }).equals(priority);
    }

    private boolean isDuplicateSection(Vector3i pos, long hash) {
        return Objects.equals(sectionHashes.get(pos), hash);
    }

    private boolean setSectionHash(Vector3i pos, long hash) {
        var previousHash = sectionHashes.put(pos, hash);
        return !Objects.equals(previousHash, hash);
    }

    private void unloadChunks() {
        while (!unloadQueue.isEmpty()) {
            var section = unloadQueue.poll();
            if (section == null) continue;

            sectionHashes.remove(section);
            latestSection.remove(section);
        }
    }

    @Override
    public void close() {
        for (var thread : threads)
            thread.interrupt();
        sectionQueue.close();

        boolean interrupted = false;
        for (var thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                    thread.interrupt();
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    public class BuildResult implements PrioritizedTask, Disposable {
        private final Vector3i chunkPos;
        private final Vector3i chunkBlockPos;
        private final long hash;
        private final long priority;

        private final List<BlockResult> blocks = new ArrayList<>();

        private final AtomicInteger pendingBlocks = new AtomicInteger();
        private final CompletableFuture<Void> future = new CompletableFuture<>();
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean allocationFailed = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        public BuildResult(
                Vector3i chunkPos,
                Vector3i chunkBlockPos,
                long hash,
                long priority
        ) {
            this.chunkPos = chunkPos;
            this.chunkBlockPos = chunkBlockPos;
            this.hash = hash;
            this.priority = priority;
        }

        public Vector3i chunkPos() {
            return chunkPos;
        }

        public Vector3i chunkBlockPos() {
            return chunkBlockPos;
        }

        @Override
        public long priority() {
            return priority;
        }

        private void submitBlockFuture(
                int x, int y, int z,
                IBlockState blockState,
                CompletionStage<@Nullable BlockModel> block
        ) {
            while (true) {
                int pending = pendingBlocks.get();
                if (pending == -1) throw new IllegalStateException();

                int remaining = (pending & Integer.MAX_VALUE) + 1;
                if (pendingBlocks.compareAndSet(pending, remaining | (pending & Integer.MIN_VALUE))) {
                    block.whenComplete((result, t) -> {
                        try {
                            if (t != null) {
                                failed.set(true);
                                if (isBufferAllocationFailure(t))
                                    allocationFailed.set(true);
                                if (!IgnoredInterruptedException.shouldIgnore(t))
                                    reportMeshFailure(t);
                            } else if (result != null) {
                                addBlock(new BlockResult(x, y, z, blockState, result));
                            }
                        } catch (Throwable callbackFailure) {
                            if (result != null) {
                                try {
                                    result.close();
                                } catch (RuntimeException | Error cleanupFailure) {
                                    callbackFailure.addSuppressed(cleanupFailure);
                                }
                            }
                            failed.set(true);
                            reportMeshFailure(callbackFailure);
                        } finally {
                            completeBlock();
                        }
                    });

                    return;
                }
            }
        }

        private void addBlock(BlockResult block) {
            synchronized (blocks) {
                if (closed.get()) {
                    block.model.close();
                    return;
                }
                blocks.add(block);
            }
        }

        private void completeBlock() {
            while (true) {
                int pending = pendingBlocks.get();
                if (pending == -1) throw new IllegalStateException();

                int remaining = (pending & Integer.MAX_VALUE) - 1;
                if (pendingBlocks.compareAndSet(pending, remaining | (pending & Integer.MIN_VALUE))) {
                    trySubmit();

                    return;
                }
            }
        }

        private void trySubmit() {
            while (true) {
                int pending = pendingBlocks.get();
                if (pending == -1 || (pending & Integer.MIN_VALUE) == 0) return;

                int remaining = pending & Integer.MAX_VALUE;
                if (remaining > 0) return;

                // This is safe because the max possible pending is 4096
                if (pendingBlocks.compareAndSet(pending, -1)) {
                    future.complete(null);

                    return;
                }
            }
        }

        private void awaitSubmission() throws InterruptedException, ExecutionException {
            while (true) {
                int pending = pendingBlocks.get();
                if ((pending & Integer.MIN_VALUE) != 0) return;

                if (pendingBlocks.compareAndSet(pending, pending | Integer.MIN_VALUE)) {
                    trySubmit();
                    break;
                }
            }


            try {
                future.get();

                if (!setLatestSection(chunkPos, priority)) {
                    close();
                    return;
                }

                if (!builtSectionQueue.offer(chunkPos, this)) {
                    close();
                    return;
                }

                if (!failed.get())
                    setSectionHash(chunkPos, hash);
            } catch (InterruptedException e) {
                close();
                throw e;
            }
        }

        public boolean failed() {
            return failed.get();
        }

        private void recordFailure(Throwable failure) {
            failed.set(true);
            if (isBufferAllocationFailure(failure))
                allocationFailed.set(true);
            if (!IgnoredInterruptedException.shouldIgnore(failure))
                reportMeshFailure(failure);
        }

        public boolean allocationFailed() {
            return allocationFailed.get();
        }

        public void forEachBlock(TriConsumer<Vector3i, IBlockState, BlockModel> blockConsumer) {
            synchronized (blocks) {
                for (var block : blocks)
                    blockConsumer.accept(block, block.blockState, block.model);
            }
        }

        @Override
        public void close() {
            synchronized (blocks) {
                if (!closed.compareAndSet(false, true)) return;
                for (var block : blocks)
                    block.model.close();
                blocks.clear();
            }
        }

        private void reportMeshFailure(Throwable error) {
            if (isBufferAllocationFailure(error)) {
                long now = System.nanoTime();
                long last = LAST_ALLOCATION_ERROR_LOG_NANOS.get();
                if (last != Long.MIN_VALUE && now - last < ALLOCATION_ERROR_LOG_INTERVAL_NANOS) return;
                if (!LAST_ALLOCATION_ERROR_LOG_NANOS.compareAndSet(last, now)) return;
                Runtime runtime = Runtime.getRuntime();
                long heapUsed = runtime.totalMemory() - runtime.freeMemory();
                Photonics.LOGGER.error(
                        "Photonics world-buffer allocation failed (Java heap used={} MiB, committed={} MiB, max={} MiB); later failures are suppressed for 10 seconds",
                        heapUsed >> 20,
                        runtime.totalMemory() >> 20,
                        runtime.maxMemory() >> 20,
                        error
                );
                return;
            }
            Photonics.LOGGER.error("An error was thrown while meshing a block", error);
        }

        private boolean isBufferAllocationFailure(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current instanceof OutOfMemoryError && current.getMessage() != null &&
                        current.getMessage().startsWith("Could not allocate"))
                    return true;
            }
            return false;
        }
    }

    private static class BlockResult extends Vector3i {
        public final BlockModel model;
        public final IBlockState blockState;

        public BlockResult(
                int x, int y, int z,
                IBlockState blockState,
                BlockModel model
        ) {
            super(x, y, z);

            this.blockState = blockState;
            this.model = model;
        }
    }
}
