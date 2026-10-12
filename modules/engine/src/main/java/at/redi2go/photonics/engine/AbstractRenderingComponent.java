package at.redi2go.photonics.engine;

import at.redi2go.photonics.game.Disposable;
import at.redi2go.photonics.engine.iris.pipeline.buffers.IrisBufferHolder;
import at.redi2go.photonics.engine.iris.pipeline.textures.IrisSamplerHolder;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IrisDynamicUniformHolder;
import at.redi2go.photonics.engine.iris.pipeline.uniforms.IrisUniformHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractRenderingComponent implements RenderingComponent {
    private final List<RenderingComponent> components;
    private final List<Disposable> resources = new ArrayList<>();
    private boolean closed;

    public AbstractRenderingComponent(@Nullable RenderingComponent... components) {
        this.components = new ArrayList<>(components.length);

        for (var component : components) {
            if (component != null) {
                this.components.add(component);
            }
        }
    }

    public <T extends RenderingComponent> T registerComponent(T component) {
        if (component != null)
            this.components.add(component);

        return component;
    }

    public <T extends Disposable> T registerResource(T resource) {
        if (resource != null)
            this.resources.add(resource);

        return resource;
    }

    @Override
    public void onFrameBegin() {
        components.forEach(RenderingComponent::onFrameBegin);
    }

    @Override
    public void onSectionAdded(int x, int y, int z) {
        for (var component : components)
            component.onSectionAdded(x, y, z);
    }

    @Override
    public void onSectionChanged(int x, int y, int z) {
        for (var component : components)
            component.onSectionChanged(x, y, z);
    }

    @Override
    public void registerUniforms(IrisUniformHolder uniforms) {
        for (var component : components)
            component.registerUniforms(uniforms);
    }

    @Override
    public void registerDynamicUniforms(IrisDynamicUniformHolder dynamicUniforms) {
        for (var component : components)
            component.registerDynamicUniforms(dynamicUniforms);
    }

    @Override
    public void registerBuffers(IrisBufferHolder buffers) {
        for (var component : components)
            component.registerBuffers(buffers);
    }

    @Override
    public void registerCustomTextures(IrisSamplerHolder samplers) {
        for (var component : components)
            component.registerCustomTextures(samplers);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;

        for (int i = components.size() - 1; i >= 0; i--) {
            try {
                components.get(i).close();
            } catch (RuntimeException | Error closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
        }

        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (RuntimeException | Error closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
        }

        components.clear();
        resources.clear();

        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
    }

}
