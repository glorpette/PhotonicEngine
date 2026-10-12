package at.redi2go.photonics.common.meshing;

import com.mojang.blaze3d.systems.RenderSystem;
import at.redi2go.photonics.engine.rendering.world.IgnoredInterruptedException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Runs modded block-entity rendering on Minecraft's render thread. */
public final class BlockEntityRenderQueue {
    private static final int MAX_PENDING = 8;
    private static final int MAX_PER_FRAME = 2;
    private static final ArrayBlockingQueue<Request> PENDING = new ArrayBlockingQueue<>(MAX_PENDING);
    private static volatile boolean active;

    public static void start() {
        active = true;
    }

    public static void stop() {
        active = false;
        Request request;
        while ((request = PENDING.poll()) != null)
            request.result.completeExceptionally(new CancellationException("Photonics block-entity capture stopped"));
    }

    public static void runOnRenderThread(Runnable action) {
        if (RenderSystem.isOnRenderThread()) {
            action.run();
            return;
        }

        Request request = new Request(action);
        try {
            while (active && !PENDING.offer(request, 50, TimeUnit.MILLISECONDS)) {
                // Apply backpressure until a frame drains the bounded queue.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException(e);
        }

        if (!active && PENDING.remove(request))
            throw new CancellationException("Photonics block-entity capture stopped");
        if (!active && !request.result.isDone())
            throw new CancellationException("Photonics block-entity capture stopped");

        try {
            request.result.get();
        } catch (InterruptedException e) {
            PENDING.remove(request);
            request.result.cancel(false);
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Block-entity render-thread capture failed", cause);
        }
    }

    public static void drainFrame() {
        if (!active || !RenderSystem.isOnRenderThread()) return;

        for (int i = 0; i < MAX_PER_FRAME; i++) {
            Request request = PENDING.poll();
            if (request == null) return;
            if (request.result.isCancelled()) continue;

            try {
                request.action.run();
                request.result.complete(null);
            } catch (Throwable failure) {
                request.result.completeExceptionally(failure);
            }
        }
    }

    private static final class Request {
        private final Runnable action;
        private final CompletableFuture<Void> result = new CompletableFuture<>();

        private Request(Runnable action) {
            this.action = action;
        }
    }

    private BlockEntityRenderQueue() {
    }
}
