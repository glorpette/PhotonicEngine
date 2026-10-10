package at.redi2go.photonics.engine.rendering;

import at.redi2go.photonics.game.minecraft.IMinecraft;
import at.redi2go.photonics.game.minecraft.core.IBlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

public class WorldOrigin extends Vector3d {
    public WorldOrigin() {
        super();
    }

    public WorldOrigin(double x, double y, double z) {
        super(x, y, z);
    }

    public WorldOrigin(Vector3i origin) {
        this(origin.x, origin.y, origin.z);
    }

    public WorldOrigin(Vector3f origin) {
        this(origin.x, origin.y, origin.z);
    }

    public Vector3d applyOffset(IBlockPos pos) {
        return new Vector3d(
                (double) pos.ph$x() - x,
                (double) pos.ph$y() - y,
                (double) pos.ph$z() - z
        );
    }

    public Vector3d applyOffset(Vector3ic pos) {
        return new Vector3d(
                (double) pos.x() - x,
                (double) pos.y() - y,
                (double) pos.z() - z
        );
    }


    public Vector3d applyOffset(Vector3fc pos) {
        return new Vector3d(
                (double) pos.x() - x,
                (double) pos.y() - y,
                (double) pos.z() - z
        );
    }

    public Vector3d applyOffset(Vector3dc pos) {
        return pos.sub(this, new Vector3d());
    }

    private static int snapToSectionPos(int component, int renderDistance) {
        int value = ((component >> 4) - renderDistance) << 4;
        return (value >> 6) << 6;
    }

    public static Vector3i getAsVector3i() {
        Vector3d cameraPos = IMinecraft.getCameraPos();
        int renderDistance = IMinecraft.getRenderDistance() + 2;

        return new Vector3i(
                snapToSectionPos((int) cameraPos.x, renderDistance),
                snapToSectionPos((int) cameraPos.y, renderDistance),
                snapToSectionPos((int) cameraPos.z, renderDistance)
        );
    }

    public static WorldOrigin get() {
        return new WorldOrigin(getAsVector3i());
    }
}
