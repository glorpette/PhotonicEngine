package at.redi2go.photonics.engine.rendering.world.block.palette;

import at.redi2go.photonics.engine.RenderingComponent;

public interface PaletteTexture extends RenderingComponent {
    PaletteTextureView reserveEntry();

    void upload();
}
