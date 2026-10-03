package guessmarket.client.ui;

import guessmarket.client.api.ServerApi;
import guessmarket.client.api.ServerException;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * What every screen of a logged-in user shares: the server gateway, the user name and a single place that
 * decides what to do with errors (an expired session sends the user back to the login screen).
 */
final class ClientSession {
    private final ServerApi api;
    private final String userName;
    private final Consumer<String> onSessionLost;
    private final Consumer<String> onStatus;

    ClientSession(ServerApi api, String userName, Consumer<String> onSessionLost, Consumer<String> onStatus) {
        this.api = api;
        this.userName = userName;
        this.onSessionLost = onSessionLost;
        this.onStatus = onStatus;
    }

    ServerApi api() {
        return api;
    }

    String userName() {
        return userName;
    }

    /** Runs a user action (POST) and shows its result in a dialog, then calls {@code afterSuccess} on the FX thread. */
    void action(String path, Map<String, ?> form, Runnable afterSuccess) {
        CompletableFuture<String> future = api.post(path, form);
        future.whenComplete((message, error) -> Platform.runLater(() -> {
            if (error != null) {
                ServerException ex = ServerException.from(error, api.getBaseUrl());
                if (ex.isUnauthorized()) {
                    onSessionLost.accept(ex.getMessage());
                } else {
                    Alerts.error(ex.getMessage());
                }
                return;
            }
            Alerts.info(message);
            if (afterSuccess != null) {
                afterSuccess.run();
            }
        }));
    }

    /** Error handling for background polls: never pops dialogs, only updates the status line. */
    void pollFailed(Throwable error) {
        ServerException ex = ServerException.from(error, api.getBaseUrl());
        Platform.runLater(() -> {
            if (ex.isUnauthorized()) {
                onSessionLost.accept("Your session ended (was the server restarted?). Please log in again.");
            } else {
                onStatus.accept(ex.getMessage());
            }
        });
    }

    void status(String text) {
        onStatus.accept(text);
    }
}
