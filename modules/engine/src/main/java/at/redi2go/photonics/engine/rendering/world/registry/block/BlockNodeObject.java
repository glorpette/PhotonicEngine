package at.redi2go.photonics.engine.rendering.world.registry.block;

import at.redi2go.photonics.engine.rendering.world.allocator.VoxelEntryListMemory;
import at.redi2go.photonics.engine.rendering.world.allocator.VoxelEntryMemory;
import at.redi2go.photonics.engine.rendering.world.allocator.WorldAllocator;
import at.redi2go.photonics.engine.rendering.world.registry.object.InnerWorldObject;
import at.redi2go.photonics.engine.rendering.world.registry.object.WorldObject;
import at.redi2go.photonics.engine.rendering.world.tree.VoxelTreeEntry;
import at.redi2go.photonics.engine.rendering.world.tree.VoxelTreeNode;

import java.util.List;

public abstract class BlockNodeObject extends VoxelTreeNode implements BlockObject {
    private final Reference ref;
    private long childMask = 0;

    public BlockNodeObject(BlockRegistry blockRegistry, int depth) {
        super(depth);
        this.ref = new Reference(blockRegistry);
    }

    public BlockNodeObject(
            BlockRegistry blockRegistry,
            int depth,
            int size,
            VoxelTreeEntry[] entries
    ) {
        super(depth, size, entries);
        this.ref = new Reference(blockRegistry);
    }

    protected BlockNodeObject(
            BlockRegistry blockRegistry,
            int depth,
            VoxelTreeEntry[] entries
    ) {
        super(depth, entries);
        this.ref = new Reference(blockRegistry);
    }


    protected abstract boolean useChildMask();

    protected abstract int extraFieldCount();

    @Override
    public void allocate(WorldAllocator allocator) {
        ref.allocate(allocator);
    }

    @Override
    public void uploadTo(VoxelEntryMemory memory) {
        awaitAllocated();

        memory.setEntryData(ref.memoryOrThrow().entryData());
        memory.setChildMask(childMask);
    }

    protected void loadDependants(List<WorldObject> output) {
        for (int i = 0; i < ENTRIES_SIZE; i++) {
            var entry = getEntry(i);
            if (entry == null) continue;

            output.add((WorldObject) entry);
        }
    }


    @Override
    public boolean isAllocated() {
        return ref.isAllocated();
    }

    @Override
    public void awaitAllocated() {
        ref.awaitAllocated();
    }

    @Override
    public void acquireReference() {
        ref.acquireReference();
    }

    @Override
    public boolean tryAcquireReference() {
        return ref.tryAcquireReference();
    }

    @Override
    public void enqueueIfUnused() {
        ref.enqueueIfUnused();
    }

    @Override
    public void discardIfUnused() {
        ref.discardIfUnused();
    }

    @Override
    public void close() {
        ref.close();
    }

    private class Reference extends InnerWorldObject<VoxelEntryListMemory> {
        public Reference(BlockRegistry registry) {
            super(registry);
        }

        public void allocate(WorldAllocator allocator) {
            setMemory(
                    () -> allocator.allocateEntryList(useChildMask(), extraFieldCount()),
                    memory -> {
                        BlockNodeObject.this.childMask = BlockNodeObject.this.writeEntries(memory);
                        memory.upload();
                    }
            );
        }

        @Override
        protected void loadDependants(List<WorldObject> output) {
            BlockNodeObject.this.loadDependants(output);
        }

        @Override
        protected WorldObject getKey() {
            return BlockNodeObject.this;
        }
    }
}
