package at.redi2go.photonics.engine.rendering.world.block.palette;

import at.redi2go.photonics.game.Disposable;
import org.joml.Vector4i;

public interface PaletteTextureView extends Disposable {
    int entryData();

    void writeFace(int face, Vector4i value);

    void upload();
}
