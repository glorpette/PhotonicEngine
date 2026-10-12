package at.redi2go.photonics.engine.rendering.world.bakery;

import org.joml.Vector3f;

/**
 * Barycentric Position, only exists for readability reasons
 */
public class BaryPos extends Vector3f {
    public BaryPos() {
        super();
    }

    public BaryPos(float x, float y, float z) {
        super(x, y, z);
    }

    public static BaryPos from(Vector3f pos, Vertex[] tri) {
        Vector3f u = tri[1].sub(tri[0], new Vector3f());
        Vector3f v = tri[2].sub(tri[0], new Vector3f());
        Vector3f n = u.cross(v, new Vector3f());

        return from(pos, u, v, n);
    }

    public static BaryPos from(
            Vector3f pos,
            Vector3f ba,
            Vector3f ca,
            Vector3f n
    ) {
        return from(pos, ba, ca, n, new BaryPos());
    }

    public static BaryPos from(
            Vector3f pos,
            Vector3f ba,
            Vector3f ca,
            Vector3f n,
            BaryPos result
    ) {
        var nDotN = 1 / n.dot(n);

        float x = (ba.y * pos.z - ba.z * pos.y) * n.x
                + (ba.z * pos.x - ba.x * pos.z) * n.y
                + (ba.x * pos.y - ba.y * pos.x) * n.z;
        float y = (pos.y * ca.z - pos.z * ca.y) * n.x
                + (pos.z * ca.x - pos.x * ca.z) * n.y
                + (pos.x * ca.y - pos.y * ca.x) * n.z;

        x *= nDotN;
        y *= nDotN;
        result.set(x, y, 1 - x - y);
        return result;
    }
}
