package guessmarket.client.api;

/** An error reply from the server (status >= 400) or a failure to reach it (status 0). */
public final class ServerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    public ServerException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }

    public boolean isConnectionProblem() {
        return status == 0;
    }

    /** Unwraps CompletionException layers and converts anything else into a readable ServerException. */
    public static ServerException from(Throwable error, String serverUrl) {
        Throwable cause = error;
        while (cause.getCause() != null && !(cause instanceof ServerException)) {
            cause = cause.getCause();
        }
        if (cause instanceof ServerException serverException) {
            return serverException;
        }
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpConnectTimeoutException
                || cause instanceof java.nio.channels.ClosedChannelException) {
            return new ServerException(0, "Cannot reach the server at " + serverUrl
                    + ". Make sure Tomcat is running on localhost:8080 and GuessMarket.war is deployed.");
        }
        return new ServerException(0, cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
    }
}
