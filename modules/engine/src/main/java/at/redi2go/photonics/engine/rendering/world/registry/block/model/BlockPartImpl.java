package at.redi2go.photonics.engine.rendering.world.registry.block.model;

import at.redi2go.photonics.engine.rendering.world.block.BlockEntry;
import at.redi2go.photonics.engine.rendering.world.block.BlockModel;
import at.redi2go.photonics.engine.rendering.world.registry.block.BlockLayer;
import at.redi2go.photonics.engine.rendering.world.registry.object.WeakValue;
import at.redi2go.photonics.engine.rendering.world.tree.entries.LightBlockEntry;
import at.redi2go.photonics.engine.rendering.world.tree.entries.SimpleBlockEntry;
import at.redi2go.photonics.engine.rendering.world.registry.light.WorldLight;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3i;

public class BlockPartImpl implements BlockModel.Part {
    private final Vector3i offset;
    private final BlockLayer block;

    BlockModelImpl owner;

    public BlockPartImpl(Vector3i offset, BlockLayer block) {
        this.offset = offset;
        this.block = block;
    }

    @Override
    public Vector3i offset() {
        return offset;
    }

    public BlockLayer blockLayer() {
        return block;
    }

    @Override
    public BlockEntry createEntry(
            int region,
            int skylight,
            @Nullable @WeakValue WorldLight light
    ) {
        return light == null ? new SimpleBlockEntry(region, skylight, this)
                : new LightBlockEntry(region, skylight, light, this);
    }

    @Override
    public void close() {
        owner.close();
    }
}
