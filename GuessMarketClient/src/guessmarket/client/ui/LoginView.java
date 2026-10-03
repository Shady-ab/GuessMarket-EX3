package guessmarket.client.ui;

import guessmarket.client.api.ServerApi;
import guessmarket.client.api.ServerException;
import guessmarket.dto.Api;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.Consumer;

/** First screen: the user types a unique name. No passwords and no sign-up, as the exercise requires. */
public final class LoginView extends VBox {
    private final ServerApi api;
    private final Consumer<String> onLoggedIn;
    private final TextField nameField = new TextField();
    private final Button loginButton = new Button("Login");
    private final Label errorLabel = new Label();

    public LoginView(ServerApi api, Consumer<String> onLoggedIn, String initialMessage) {
        this.api = api;
        this.onLoggedIn = onLoggedIn;
        Label title = new Label("Guess Market");
        title.getStyleClass().add("login-title");
        Label prompt = new Label("Enter your user name:");
        nameField.setPromptText("User name");
        nameField.setMaxWidth(280);
        nameField.setOnAction(e -> login());
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> login());
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(420);
        if (initialMessage != null) {
            errorLabel.setText(initialMessage);
        }
        Label server = new Label("Server: " + api.getBaseUrl());
        server.getStyleClass().add("hint-text");

        setSpacing(12);
        setPadding(new Insets(30));
        setAlignment(Pos.CENTER);
        getChildren().addAll(title, prompt, nameField, loginButton, errorLabel, server);
        Platform.runLater(nameField::requestFocus);
    }

    private void login() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Please enter a user name.");
            return;
        }
        loginButton.setDisable(true);
        errorLabel.setText("Connecting...");
        api.post(Api.LOGIN, Map.of(Api.PARAM_USERNAME, name)).whenComplete((message, error) -> Platform.runLater(() -> {
            loginButton.setDisable(false);
            if (error != null) {
                errorLabel.setText(ServerException.from(error, api.getBaseUrl()).getMessage());
                nameField.selectAll();
                nameField.requestFocus();
                return;
            }
            onLoggedIn.accept(name);
        }));
    }
}
