package guessmarket.client.ui;

import guessmarket.dto.Api;
import guessmarket.dto.ChatDto;
import guessmarket.dto.ChatLineDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Bonus: a single chat room for all logged-in users. Lines are pulled as a delta from the last known version. */
final class ChatPane extends BorderPane {
    private final ClientSession session;
    private final TextArea chatArea = new TextArea();
    private final TextField messageField = new TextField();
    private final Button sendButton = new Button("Send");
    private int version;

    ChatPane(ClientSession session) {
        this.session = session;
        setPadding(new Insets(10));
        Label title = new Label("Chat (everybody sees everything)");
        title.getStyleClass().add("section-title");
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 8, 0));

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.getStyleClass().add("details-area");
        setCenter(chatArea);

        messageField.setPromptText("Type a message and press Enter");
        messageField.setOnAction(e -> send());
        sendButton.setOnAction(e -> send());
        HBox inputRow = new HBox(8, messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);
        inputRow.setPadding(new Insets(8, 0, 0, 0));
        setBottom(inputRow);
    }

    CompletableFuture<?> refreshChat() {
        int from = version;
        return session.api().getJson(Api.CHAT, Map.of(Api.PARAM_CHAT_VERSION, from)).whenComplete((json, error) -> {
            if (error != null) {
                session.pollFailed(error);
                return;
            }
            ChatDto chat = session.api().parse(json, ChatDto.class);
            Platform.runLater(() -> {
                if (from != version) {
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (ChatLineDto line : chat.entries()) {
                    sb.append('[').append(line.time()).append("] ").append(line.user()).append(": ")
                            .append(line.text()).append('\n');
                }
                if (!sb.isEmpty()) {
                    chatArea.appendText(sb.toString());
                }
                version = chat.version();
            });
        });
    }

    private void send() {
        String text = messageField.getText() == null ? "" : messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        sendButton.setDisable(true);
        session.api().post(Api.CHAT, Map.of(Api.PARAM_MESSAGE, text)).whenComplete((ok, error) -> Platform.runLater(() -> {
            sendButton.setDisable(false);
            if (error != null) {
                session.pollFailed(error);
                Alerts.error(guessmarket.client.api.ServerException.from(error, session.api().getBaseUrl()).getMessage());
                return;
            }
            messageField.clear();
            messageField.requestFocus();
            refreshChat();
        }));
    }
}
