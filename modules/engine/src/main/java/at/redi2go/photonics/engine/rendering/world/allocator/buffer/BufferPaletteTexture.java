package at.redi2go.photonics.engine.rendering.world.allocator.buffer;

import at.redi2go.photonics.engine.rendering.world.allocator.heap.DefaultGpuBufferHeap;
import at.redi2go.photonics.engine.rendering.world.allocator.heap.IGpuBufferHeap;
import at.redi2go.photonics.engine.rendering.world.allocator.heap.MemoryView;
import at.redi2go.photonics.game.blaze3d.systems.IRenderSystem;
import at.redi2go.photonics.engine.iris.pipeline.buffers.IrisBufferHolder;
import at.redi2go.photonics.engine.rendering.world.block.palette.PaletteTexture;
import at.redi2go.photonics.engine.rendering.world.block.palette.PaletteTextureView;
import org.joml.Vector4i;

public class BufferPaletteTexture implements PaletteTexture {
    // 4 ints per entry, with 6 faces.
    private static final long ENTRY_BYTE_SIZE = (4 * 4) * 6;

    private final IGpuBufferHeap heap;

    public BufferPaletteTexture(
            int width,
            int height
    ) {
        heap = new DefaultGpuBufferHeap(
                IRenderSystem.getDevice(),
                () -> "Palette Texture",
                width * height * ENTRY_BYTE_SIZE,
                0
        );
    }

    @Override
    public void upload() {
        heap.upload();
    }

    @Override
    public PaletteTextureView reserveEntry() {
        return new View(heap.allocateOrThrow(ENTRY_BYTE_SIZE));
    }

    @Override
    public void registerBuffers(IrisBufferHolder buffers) {
        buffers.addDefaultBuffer("ph_palette_texture", heap::buffer);
    }

    @Override
    public void close() {
        heap.close();
    }

    private static class View implements PaletteTextureView {
        private final MemoryView memory;

        public View(MemoryView memory) {
            this.memory = memory;
        }

        @Override
        public int entryData() {
            return (int) memory.begin() >> 4;
        }

        @Override
        public void writeFace(int face, Vector4i value) {
            int offset = face * 16;
            memory.putInt(offset, value.x);
            memory.putInt(offset + 4, value.y);
            memory.putInt(offset + 8, value.z);
            memory.putInt(offset + 12, value.w);
        }

        @Override
        public void upload() {
            memory.upload();
        }

        @Override
        public void close() {
            memory.close();
        }
    }
}
