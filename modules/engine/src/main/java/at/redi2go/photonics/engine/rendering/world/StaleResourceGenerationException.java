package at.redi2go.photonics.engine.rendering.world;

/** Signals that work using texture resources invalidated by a resource reload must be retried. */
public final class StaleResourceGenerationException extends RuntimeException {
    public StaleResourceGenerationException(String message) {
        super(message);
    }

    public StaleResourceGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static boolean causedBy(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof StaleResourceGenerationException) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }
}
