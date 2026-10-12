package at.redi2go.photonics.engine.rendering.world.bakery;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlockAndTintGetter;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import at.redi2go.photonics.engine.rendering.world.bakery.impl.BlockBakeryImpl;
import at.redi2go.photonics.engine.rendering.world.bakery.texture.AtlasDownloader;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3i;

public interface BlockBakery {
    @Nullable <T extends BlockMeshState> MeshResult meshBlock(
            BlockMesher<T> mesher,
            T meshState,
            Vector3i blockChunkOffset,
            IBlockPos pos,
            IBlockState blockState,
            IBlockAndTintGetter blockAndTintGetter
    );

    interface MeshResult extends Disposable {
        /** Resolves any deferred textures before the result is keyed or baked. */
        default void prepare() {}

        long vertexHash();

        void bake(VoxelConsumer voxelConsumer) throws InterruptedException;
    }

    static BlockBakery newBakery(AtlasDownloader atlasDownloader) {
        return new BlockBakeryImpl(atlasDownloader);
    }
}
