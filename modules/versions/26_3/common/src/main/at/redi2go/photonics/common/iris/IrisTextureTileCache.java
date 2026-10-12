package at.redi2go.photonics.common.iris;

import at.redi2go.photonics.engine.rendering.world.IgnoredInterruptedException;
import at.redi2go.photonics.engine.rendering.world.StaleResourceGenerationException;
import com.mojang.renderpearl.api.textures.GpuTexture;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** A shared, byte-bounded cache for the CPU tiles used by Photonics voxel baking. */
final class IrisTextureTileCache {
    static final int TILE_SIZE = 256;
    static final long MAX_CPU_BYTES = 256L << 20;
    static final int MAX_READBACK_BYTES = 16 << 20;

    private final Map<TileKey, Tile> tiles = new HashMap<>();
    private final LinkedHashMap<TileKey, Tile> accessOrder = new LinkedHashMap<>(64, 0.75f, true);
    private final ReadbackBudget readbackBudget = new ReadbackBudget(MAX_READBACK_BYTES);
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    private volatile long generation;
    private long reservedCpuBytes;

    long generation() {
        return generation;
    }

    int sample(
            GpuTexture texture,
            long textureGeneration,
            int pixelX,
            int pixelY,
            int textureWidth,
            int textureHeight,
            TileLoader loader
    ) {
        if (textureWidth <= 0 || textureHeight <= 0) return 0;
        if (pixelX < 0 || pixelY < 0 || pixelX >= textureWidth || pixelY >= textureHeight)
            throw new IndexOutOfBoundsException("Texture pixel outside atlas");

        int tileX = pixelX / TILE_SIZE * TILE_SIZE;
        int tileY = pixelY / TILE_SIZE * TILE_SIZE;
        int tileWidth = Math.min(TILE_SIZE, textureWidth - tileX);
        int tileHeight = Math.min(TILE_SIZE, textureHeight - tileY);
        int byteSize = Math.multiplyExact(Math.multiplyExact(tileWidth, tileHeight), Integer.BYTES);
        var key = new TileKey(texture, textureGeneration, tileX, tileY);

        Tile tile;
        boolean startLoad = false;
        synchronized (this) {
            while (true) {
                if (textureGeneration != generation)
                    throw new StaleResourceGenerationException("Texture atlas belongs to a stale resource generation");

                tile = tiles.get(key);
                if (tile != null) {
                    tile.users++;
                    accessOrder.get(key);
                    hits.incrementAndGet();
                    break;
                }

                evictFor(byteSize);
                if (reservedCpuBytes + byteSize <= MAX_CPU_BYTES) {
                    tile = new Tile(key, tileX, tileY, tileWidth, tileHeight, byteSize);
                    tile.users = 1;
                    tiles.put(key, tile);
                    accessOrder.put(key, tile);
                    reservedCpuBytes += byteSize;
                    misses.incrementAndGet();
                    startLoad = true;
                    break;
                }

                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IgnoredInterruptedException();
                }
            }
        }

        if (startLoad) {
            Tile loadingTile = tile;
            try {
                loader.load(tileX, tileY, tileWidth, tileHeight, byteSize)
                        .whenComplete((pixels, failure) -> finishLoad(loadingTile, pixels, failure));
            } catch (Throwable failure) {
                finishLoad(loadingTile, null, failure);
            }
        }

        try {
            int[] pixels = tile.pixels.get();
            int value = pixels[(pixelY - tileY) * tileWidth + pixelX - tileX];
            return Integer.reverseBytes(Integer.rotateLeft(value, 8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IgnoredInterruptedException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (StaleResourceGenerationException.causedBy(cause))
                throw new StaleResourceGenerationException("Texture resources were reloaded during atlas sampling", cause);
            throw new IllegalStateException("Could not read Photonics atlas tile", cause);
        } finally {
            release(tile);
        }
    }

    private synchronized void finishLoad(Tile tile, @Nullable int[] pixels, @Nullable Throwable failure) {
        tile.loading = false;
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        boolean current = generation == tile.key.generation && tiles.get(tile.key) == tile;

        if (cause != null || pixels == null || !current) {
            if (tiles.remove(tile.key, tile)) accessOrder.remove(tile.key);
            if (cause == null) cause = new StaleResourceGenerationException("Atlas was reloaded during texture readback");
            tile.pixels.completeExceptionally(cause);
            releaseReservation(tile);
        } else {
            tile.pixels.complete(pixels);
        }
        notifyAll();
    }

    private synchronized void release(Tile tile) {
        tile.users--;
        if (tile.users < 0) throw new IllegalStateException("Texture tile reference count underflow");
        if (!tile.loading && !tile.active)
            releaseReservation(tile);
        notifyAll();
    }

    private void evictFor(long requestedBytes) {
        Iterator<Map.Entry<TileKey, Tile>> iterator = accessOrder.entrySet().iterator();
        while (reservedCpuBytes + requestedBytes > MAX_CPU_BYTES && iterator.hasNext()) {
            var candidate = iterator.next();
            Tile tile = candidate.getValue();
            if (tile.loading || tile.users != 0) continue;

            iterator.remove();
            tiles.remove(candidate.getKey(), tile);
            tile.active = false;
            releaseReservation(tile);
            evictions.incrementAndGet();
        }
    }

    private void releaseReservation(Tile tile) {
        if (!tile.reserved) return;
        tile.reserved = false;
        reservedCpuBytes -= tile.byteSize;
    }

    synchronized long clear() {
        generation++;
        for (Tile tile : tiles.values()) {
            tile.active = false;
            if (tile.loading) {
                tile.pixels.completeExceptionally(new IllegalStateException("Texture resources were reloaded"));
            } else if (tile.users == 0) {
                releaseReservation(tile);
            }
        }
        tiles.clear();
        accessOrder.clear();
        notifyAll();
        readbackBudget.wakeWaiters();
        return generation;
    }

    synchronized String stats() {
        return "texture tiles: " + (reservedCpuBytes >> 20) + "/" + (MAX_CPU_BYTES >> 20)
                + " MiB, in-flight readbacks: " + (readbackBudget.usedBytes() >> 20)
                + "/" + (MAX_READBACK_BYTES >> 20) + " MiB, hits=" + hits.get()
                + ", misses=" + misses.get() + ", evictions=" + evictions.get();
    }

    ReadbackBudget readbackBudget() {
        return readbackBudget;
    }

    @FunctionalInterface
    interface TileLoader {
        CompletableFuture<int[]> load(int x, int y, int width, int height, int byteSize);
    }

    private static final class Tile {
        private final TileKey key;
        private final int x, y, width, height, byteSize;
        private final CompletableFuture<int[]> pixels = new CompletableFuture<>();
        private int users;
        private boolean loading = true;
        private boolean active = true;
        private boolean reserved = true;

        private Tile(TileKey key, int x, int y, int width, int height, int byteSize) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.byteSize = byteSize;
        }
    }

    private static final class TileKey {
        private final GpuTexture texture;
        private final long generation;
        private final int x, y;

        private TileKey(GpuTexture texture, long generation, int x, int y) {
            this.texture = texture;
            this.generation = generation;
            this.x = x;
            this.y = y;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(texture);
            result = 31 * result + Long.hashCode(generation);
            result = 31 * result + x;
            return 31 * result + y;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TileKey key && texture == key.texture && generation == key.generation
                    && x == key.x && y == key.y;
        }
    }

    static final class ReadbackBudget {
        private final int maximumBytes;
        private int usedBytes;

        private ReadbackBudget(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        synchronized void acquire(int bytes, BooleanSupplier stillCurrent) throws InterruptedException {
            if (bytes > maximumBytes)
                throw new IllegalArgumentException("Texture tile exceeds the readback budget");
            while (usedBytes + bytes > maximumBytes) {
                if (!stillCurrent.getAsBoolean())
                    throw new StaleResourceGenerationException("Texture resources changed while waiting for readback memory");
                wait();
            }
            if (!stillCurrent.getAsBoolean())
                throw new StaleResourceGenerationException("Texture resources changed before readback memory was reserved");
            usedBytes += bytes;
        }

        synchronized void wakeWaiters() {
            notifyAll();
        }

        synchronized void release(int bytes) {
            usedBytes -= bytes;
            if (usedBytes < 0) throw new IllegalStateException("Readback budget underflow");
            notifyAll();
        }

        synchronized int usedBytes() {
            return usedBytes;
        }
    }
}
