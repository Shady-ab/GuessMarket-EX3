package guessmarket.client.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

final class Alerts {
    private Alerts() {
    }

    static void error(String message) {
        show(Alert.AlertType.ERROR, "Error", message);
    }

    static void info(String message) {
        show(Alert.AlertType.INFORMATION, "Success", message);
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, "", ButtonType.OK);
        alert.setHeaderText(header);
        Label content = new Label(message);
        content.setWrapText(true);
        content.setMaxWidth(520);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
