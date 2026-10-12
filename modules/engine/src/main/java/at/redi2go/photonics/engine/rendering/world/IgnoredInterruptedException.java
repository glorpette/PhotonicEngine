package at.redi2go.photonics.engine.rendering.world;

public class IgnoredInterruptedException extends RuntimeException {
    public IgnoredInterruptedException() {
    }

    public IgnoredInterruptedException(Throwable cause) {
        super(cause);
    }

    public static boolean shouldIgnore(Throwable t) {
        if (t == null) return true;

        while (t != null) {
            if (t instanceof IgnoredInterruptedException) return true;
            t = t.getCause();
        }

        return false;
    }
}
