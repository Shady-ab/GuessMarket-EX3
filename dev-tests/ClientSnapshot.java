import guessmarket.client.api.ServerApi;
import guessmarket.client.ui.LoginView;
import guessmarket.client.ui.MainView;
import guessmarket.dto.Api;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * Renders the client screens to PNG files without showing any window (no keyboard or mouse use).
 * Needs a running server. Usage (from dist\GuessMarketClient):
 * java --module-path lib --add-modules javafx.controls -cp "GuessMarketClient.jar;GuessMarketDto.jar;gson-2.11.0.jar"
 *      ..\..\dev-tests\ClientSnapshot.java USER OUTPUT_DIR [EVENT_ROW] [WIDTH HEIGHT]
 */
public class ClientSnapshot extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage ignored) throws Exception {
        var raw = getParameters().getRaw();
        String user = raw.get(0);
        File out = new File(raw.get(1));
        int row = raw.size() > 2 ? Integer.parseInt(raw.get(2)) : 0;
        if (raw.size() > 4) {
            width = Integer.parseInt(raw.get(3));
            height = Integer.parseInt(raw.get(4));
        }
        out.mkdirs();
        ServerApi api = new ServerApi(ServerApi.DEFAULT_SERVER);

        shoot(new LoginView(api, name -> { }, "The user name 'Alice' already exists and is logged in."),
                new File(out, "login.png"), 0, () -> { });

        api.post(Api.LOGIN, Map.of(Api.PARAM_USERNAME, user)).get();
        MainView main = new MainView(api, user, () -> { }, msg -> System.out.println("session lost: " + msg));
        Scene scene = scene(main);
        TabPane tabs = (TabPane) main.getCenter();
        new Thread(() -> {
            try {
                Thread.sleep(2500);
                runFx(() -> {
                    scene.snapshot(null);
                    TableView<?> table = (TableView<?>) tabs.getTabs().get(0).getContent().lookup(".table-view");
                    table.getSelectionModel().select(Math.min(row, table.getItems().size() - 1));
                });
                Thread.sleep(2000);
                runFx(() -> {
                    var name = (javafx.scene.control.Label) tabs.getTabs().get(0).getContent().lookup(".event-name");
                    System.out.println("selected event: " + (name == null ? "none" : name.getText()));
                    save(scene, new File(out, "events.png"));
                });
                runFx(() -> tabs.getSelectionModel().select(1));
                Thread.sleep(1500);
                runFx(() -> save(scene, new File(out, "account.png")));
                runFx(() -> tabs.getSelectionModel().select(2));
                Thread.sleep(1000);
                runFx(() -> save(scene, new File(out, "chat.png")));
                main.stop();
                api.post(Api.LOGOUT, null).get();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            Platform.exit();
            System.exit(0);
        }).start();
    }

    private static int width = 1280;
    private static int height = 820;

    private static Scene scene(Parent root) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(ClientSnapshot.class.getResource("/guessmarket/client/ui/app.css").toExternalForm());
        return scene;
    }

    private static void shoot(Parent root, File file, long delay, Runnable before) {
        Scene scene = scene(root);
        before.run();
        save(scene, file);
    }

    private static void runFx(Runnable action) throws InterruptedException {
        Object lock = new Object();
        boolean[] done = {false};
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                synchronized (lock) {
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });
        synchronized (lock) {
            while (!done[0]) {
                lock.wait();
            }
        }
    }

    private static void save(Scene scene, File file) {
        WritableImage image = scene.snapshot(null);
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        try {
            ImageIO.write(buffered, "png", file);
            System.out.println("saved " + file);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
