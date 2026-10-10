package at.redi2go.photonics.impl.minecraft.world.level;

import at.redi2go.photonics.game.minecraft.IProperty;
import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import at.redi2go.photonics.game.minecraft.world.level.IBlock;
import at.redi2go.photonics.game.minecraft.world.level.IBlockGetter;
import at.redi2go.photonics.game.minecraft.world.level.IBlockState;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockState.class)
public abstract class BlockStateImpl extends BlockBehaviour.BlockStateBase implements IBlockState {
    @Override
    public IBlock ph$block() {
        return (IBlock) getBlock();
    }

    @Override
    public boolean ph$is(IBlock block) {
        return is((Block) block);
    }

    @Override
    public boolean ph$isAir() {
        return isAir();
    }

    @Override
    public boolean ph$isViewBlocking(IBlockGetter blockGetter, IBlockPos blockPos) {
        return isViewBlocking((BlockGetter) blockGetter, (BlockPos) blockPos, new net.minecraft.world.phys.AABB((BlockPos) blockPos));
    }

    @Override
    public boolean ph$hasProperty(IProperty<?> property) {
        return hasProperty((Property<?>) property);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T ph$getValue(IProperty<T> property) {
        return getValue((Property<T>) property);
    }

    private BlockStateImpl(Block block, Property<?>[] properties, Comparable<?>[] values) {
        super(block, properties, values);
    }
}
