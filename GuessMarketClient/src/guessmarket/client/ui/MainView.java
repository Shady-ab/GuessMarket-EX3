package guessmarket.client.ui;

import guessmarket.client.api.Poller;
import guessmarket.client.api.ServerApi;
import guessmarket.client.api.ServerException;
import guessmarket.dto.Api;
import guessmarket.dto.MyUserDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.function.Consumer;

/** The screen of a logged-in user: a top bar (upload, balance, logout) and the Events / My account / Chat tabs. */
public final class MainView extends BorderPane {
    private final ServerApi api;
    private final String userName;
    private final Runnable onLoggedOut;
    private final Poller poller = new Poller();
    private final Label balanceLabel = new Label();
    private final Label statusLabel = new Label();
    private final ProgressIndicator uploadProgress = new ProgressIndicator();
    private final Button uploadButton = new Button("Load events XML...");
    private final EventsPane eventsPane;
    private final UserPane userPane;
    private final ChatPane chatPane;
    private boolean stopped;

    public MainView(ServerApi api, String userName, Runnable onLoggedOut, Consumer<String> onSessionLost) {
        this.api = api;
        this.userName = userName;
        this.onLoggedOut = onLoggedOut;
        ClientSession session = new ClientSession(api, userName, message -> {
            if (!stopped) {
                stop();
                onSessionLost.accept(message);
            }
        }, this::setStatus);
        eventsPane = new EventsPane(session);
        userPane = new UserPane(session, this::meUpdated);
        chatPane = new ChatPane(session);

        setTop(buildTopBar());
        TabPane tabs = new TabPane(
                new Tab("Events", eventsPane),
                new Tab("My account", userPane),
                new Tab("Chat", chatPane));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);

        poller.every(Poller.DEFAULT_INTERVAL_MILLIS, eventsPane::refreshEvents);
        poller.every(Poller.DEFAULT_INTERVAL_MILLIS, eventsPane::refreshDetails);
        poller.every(Poller.DEFAULT_INTERVAL_MILLIS, userPane::refreshMe);
        poller.every(Poller.DEFAULT_INTERVAL_MILLIS, userPane::refreshUsers);
        poller.every(Poller.DEFAULT_INTERVAL_MILLIS, chatPane::refreshChat);
    }

    private HBox buildTopBar() {
        Label title = new Label("Guess Market");
        title.getStyleClass().add("app-title");
        Label user = new Label("Logged in as: " + userName);
        user.getStyleClass().add("section-title");
        balanceLabel.getStyleClass().add("balance-label");
        uploadProgress.setVisible(false);
        uploadProgress.setPrefSize(22, 22);
        uploadButton.setOnAction(e -> upload());
        statusLabel.getStyleClass().add("hint-text");
        statusLabel.setMinWidth(0);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> logout());
        Region spacer = new Region();
        HBox bar = new HBox(14, title, user, balanceLabel, uploadButton, uploadProgress, statusLabel, spacer, logoutButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private void meUpdated(MyUserDto me) {
        balanceLabel.setText("Balance: " + UiFormat.money(me.balance()) + (me.blocked() ? "  [BLOCKED]" : ""));
        eventsPane.updateMe(me);
    }

    private void upload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market events XML (Exercise 3 format)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML files", "*.xml"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        Window window = getScene() == null ? null : getScene().getWindow();
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return;
        }
        uploadButton.setDisable(true);
        uploadProgress.setVisible(true);
        setStatus("Uploading " + file.getName() + " ...");
        api.upload(file.toPath()).whenComplete((message, error) -> Platform.runLater(() -> {
            uploadButton.setDisable(false);
            uploadProgress.setVisible(false);
            if (error != null) {
                ServerException ex = ServerException.from(error, api.getBaseUrl());
                setStatus("Upload failed: " + file.getName());
                Alerts.error("The file '" + file.getName() + "' was not loaded.\n\nReason: " + ex.getMessage());
                return;
            }
            setStatus("Loaded " + file.getName());
            Alerts.info(message);
            eventsPane.refreshEvents();
            userPane.refreshMe();
        }));
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void logout() {
        stop();
        api.post(Api.LOGOUT, null).whenComplete((ok, error) -> Platform.runLater(onLoggedOut));
    }

    /** Stops polling. Safe to call more than once. */
    public void stop() {
        stopped = true;
        poller.stop();
    }
}
