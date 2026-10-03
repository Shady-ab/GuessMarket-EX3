package guessmarket.client.ui;

import guessmarket.dto.EventDetailsDto;
import guessmarket.dto.OptionDto;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** The Exercise-2 trade / close dialogs, now driven by event DTOs and supporting any number of options. */
final class TradeDialogs {
    record LmsrBuy(int optionNumber, int quantity) {
    }

    record Order(int optionNumber, String side, int quantity, String priceText) {
    }

    private TradeDialogs() {
    }

    static Optional<LmsrBuy> lmsrBuy(EventDetailsDto event) {
        Dialog<LmsrBuy> dialog = new Dialog<>();
        dialog.setTitle("LMSR purchase - " + event.name());
        dialog.setHeaderText("Buy shares of one option (cost is calculated by LMSR)");
        ComboBox<String> optionBox = optionBox(event);
        Spinner<Integer> qty = new Spinner<>(1, 1_000_000, 1);
        qty.setEditable(true);
        dialog.getDialogPane().setContent(grid("Option", optionBox, "Quantity", qty));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new LmsrBuy(optionBox.getSelectionModel().getSelectedIndex() + 1, committedValue(qty))
                : null);
        return dialog.showAndWait();
    }

    static Optional<Order> order(EventDetailsDto event) {
        Dialog<Order> dialog = new Dialog<>();
        dialog.setTitle("Order book - " + event.name());
        dialog.setHeaderText("Place a BUY or SELL order. Price range: 0.01 - " + UiFormat.money(event.d() - 0.01));
        ComboBox<String> optionBox = optionBox(event);
        ComboBox<String> sideBox = new ComboBox<>();
        sideBox.getItems().addAll("BUY", "SELL");
        sideBox.getSelectionModel().selectFirst();
        Spinner<Integer> qty = new Spinner<>(1, 1_000_000, 1);
        qty.setEditable(true);
        TextField price = new TextField(UiFormat.money(event.options().get(0).price()));
        optionBox.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.intValue() >= 0) {
                price.setText(UiFormat.money(event.options().get(newV.intValue()).price()));
            }
        });
        dialog.getDialogPane().setContent(grid("Option", optionBox, "Side", sideBox, "Quantity", qty,
                "Price per share", price));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new Order(optionBox.getSelectionModel().getSelectedIndex() + 1, sideBox.getValue(),
                committedValue(qty), price.getText().trim())
                : null);
        return dialog.showAndWait();
    }

    /** Returns the 1-based number of the winning option. */
    static Optional<Integer> chooseWinner(EventDetailsDto event) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Close event - " + event.name());
        dialog.setHeaderText("Choose the winning option");
        ComboBox<String> optionBox = optionBox(event);
        dialog.getDialogPane().setContent(grid("Winner", optionBox));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? optionBox.getSelectionModel().getSelectedIndex() + 1
                : null);
        return dialog.showAndWait();
    }

    private static ComboBox<String> optionBox(EventDetailsDto event) {
        ComboBox<String> box = new ComboBox<>();
        for (OptionDto option : event.options()) {
            box.getItems().add(option.number() + ") " + option.name() + "  (now " + UiFormat.money(option.price()) + ")");
        }
        box.getSelectionModel().selectFirst();
        return box;
    }

    /** Spinner keeps typed text uncommitted until focus moves; read it explicitly so typed values are not lost. */
    private static int committedValue(Spinner<Integer> spinner) {
        try {
            int value = Integer.parseInt(spinner.getEditor().getText().trim());
            return Math.max(1, value);
        } catch (NumberFormatException ex) {
            return spinner.getValue();
        }
    }

    private static GridPane grid(Object... labelAndControl) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        for (int i = 0; i < labelAndControl.length; i += 2) {
            grid.add(new Label(String.valueOf(labelAndControl[i])), 0, i / 2);
            grid.add((Node) labelAndControl[i + 1], 1, i / 2);
        }
        return grid;
    }
}
