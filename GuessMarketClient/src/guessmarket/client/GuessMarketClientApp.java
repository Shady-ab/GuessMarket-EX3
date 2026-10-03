package guessmarket.client;

import guessmarket.client.api.ServerApi;
import guessmarket.client.ui.LoginView;
import guessmarket.client.ui.MainView;
import guessmarket.dto.Api;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the Exercise-3 JavaFX client. The server address defaults to http://localhost:8080/GuessMarket;
 * it can be overridden with the first program argument or with -Dgm.server=...
 */
public final class GuessMarketClientApp extends Application {
    private static final double PREFERRED_WIDTH = 1280;
    private static final double PREFERRED_HEIGHT = 820;

    private ServerApi api;
    private double width;
    private double height;
    private Stage stage;
    private MainView mainView;
    private String loggedInUser;

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        api = new ServerApi(resolveServer(getParameters().getRaw()));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        width = Math.min(PREFERRED_WIDTH, screen.getWidth() * 0.95);
        height = Math.min(PREFERRED_HEIGHT, screen.getHeight() * 0.92);
        stage.setMinWidth(Math.min(900, width));
        stage.setMinHeight(Math.min(600, height));
        stage.setOnCloseRequest(e -> shutdown());
        showLogin(null);
        stage.centerOnScreen();
        stage.show();
    }

    private void showLogin(String message) {
        if (mainView != null) {
            mainView.stop();
            mainView = null;
        }
        loggedInUser = null;
        stage.setTitle("Guess Market - Login");
        setRoot(new LoginView(api, this::showMain, message));
    }

    private void showMain(String userName) {
        loggedInUser = userName;
        stage.setTitle("Guess Market - " + userName);
        mainView = new MainView(api, userName, () -> showLogin(null), this::showLogin);
        setRoot(mainView);
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, width, height);
            var css = getClass().getResource("/guessmarket/client/ui/app.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    /** Logs out (so the name becomes free for a new login) and exits. */
    private void shutdown() {
        if (mainView != null) {
            mainView.stop();
        }
        if (loggedInUser != null) {
            try {
                api.post(Api.LOGOUT, null).get(1500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // The server may already be down; exiting anyway.
            }
        }
        Platform.exit();
        System.exit(0);
    }

    private static String resolveServer(List<String> args) {
        if (!args.isEmpty() && args.get(0).startsWith("http")) {
            return args.get(0);
        }
        return System.getProperty("gm.server", ServerApi.DEFAULT_SERVER);
    }
}
