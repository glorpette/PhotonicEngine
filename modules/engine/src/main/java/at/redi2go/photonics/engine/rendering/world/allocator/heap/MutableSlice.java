package at.redi2go.photonics.engine.rendering.world.allocator.heap;

class MutableSlice implements MemorySlice {
    public long begin;
    public long end;

    MutableSlice(long begin, long end) {
        this.begin = begin;
        this.end = end;
    }

    MutableSlice(MemorySlice slice) {
        this.begin = slice.begin();
        this.end = slice.end();
    }

    @Override
    public long begin() {
        return begin;
    }

    @Override
    public long end() {
        return end;
    }
}
