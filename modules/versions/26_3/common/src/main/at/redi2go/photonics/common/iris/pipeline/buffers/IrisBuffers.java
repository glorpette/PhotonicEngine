package at.redi2go.photonics.common.iris.pipeline.buffers;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

public class IrisBuffers {
    public static IntSet getUsedBuffers(int programId) {
        var used = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

        int storageBlocks = GL43.glGetProgramInterfacei(programId, GL43.GL_SHADER_STORAGE_BLOCK, GL43.GL_ACTIVE_RESOURCES);
        for (int block = 0; block < storageBlocks; block++) {
            int[] binding = new int[1];
            GL43.glGetProgramResourceiv(
                    programId,
                    GL43.GL_SHADER_STORAGE_BLOCK,
                    block,
                    new int[]{GL43.GL_BUFFER_BINDING},
                    new int[1],
                    binding
            );
            used.add(binding[0]);
        }

        int uniformBlocks = GL20.glGetProgrami(programId, GL31.GL_ACTIVE_UNIFORM_BLOCKS);
        for (int block = 0; block < uniformBlocks; block++)
            used.add(GL31.glGetActiveUniformBlocki(programId, block, GL31.GL_UNIFORM_BLOCK_BINDING));

        return used;
    }

    public static void bindBuffers(@Nullable WorldRenderingPipeline pipeline, int programId) {
        if (pipeline instanceof IrisRenderingPipeline ext)
            ((InternalIrisBufferHolder) ext).bind(programId, getUsedBuffers(programId));
    }

    public static void bindBuffers(int programId) {
        bindBuffers(
                Iris.getPipelineManager().getPipelineNullable(),
                programId
        );
    }
}
