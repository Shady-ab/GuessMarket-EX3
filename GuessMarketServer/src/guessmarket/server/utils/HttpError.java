package guessmarket.server.utils;

/** Thrown by a servlet handler to answer with a specific HTTP status and a readable message. */
public final class HttpError extends Exception {
    private static final long serialVersionUID = 1L;

    private final int status;

    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
