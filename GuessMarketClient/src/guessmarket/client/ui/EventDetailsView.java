package guessmarket.client.ui;

import guessmarket.dto.BookDto;
import guessmarket.dto.EventDetailsDto;
import guessmarket.dto.OptionDto;
import guessmarket.dto.OrderDto;
import guessmarket.dto.ParticipantDto;
import guessmarket.dto.TradeDto;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * "Event details and trade" panel of the events screen: the event's details, one box per option (its order book,
 * or its LMSR price) and the participations / trade history tables.
 */
final class EventDetailsView extends VBox {
    private static final int OPTION_COLUMNS = 2;

    private final Label nameLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final GridPane infoGrid = new GridPane();
    private final GridPane optionsGrid = new GridPane();
    private final TableView<ParticipantDto> participantsTable = new TableView<>();
    private final TableView<TradeDto> historyTable = new TableView<>();
    private final List<OptionBox> optionBoxes = new ArrayList<>();
    private final Label placeholder = new Label("Select an event to see its details and trade.");
    private final VBox content;
    private String layoutKey;

    EventDetailsView(Node actions) {
        setSpacing(10);
        setPadding(new Insets(0, 4, 4, 0));

        nameLabel.getStyleClass().add("event-name");
        nameLabel.setWrapText(true);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("hint-text");
        infoGrid.setHgap(18);
        infoGrid.setVgap(3);
        infoGrid.getStyleClass().add("info-grid");

        optionsGrid.setHgap(10);
        optionsGrid.setVgap(10);
        for (int i = 0; i < OPTION_COLUMNS; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / OPTION_COLUMNS);
            column.setHgrow(Priority.ALWAYS);
            optionsGrid.getColumnConstraints().add(column);
        }

        participantsTable.setPlaceholder(new Label("No participants yet."));
        participantsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        participantsTable.setPrefHeight(170);
        participantsTable.setMinHeight(120);

        historyTable.setPlaceholder(new Label("No trades yet."));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPrefHeight(170);
        historyTable.setMinHeight(120);
        historyTable.getColumns().add(column("Time", 70, TradeDto::time));
        historyTable.getColumns().add(column("Kind", 95, TradeDto::kind));
        historyTable.getColumns().add(column("Buyer", 95, TradeDto::user));
        historyTable.getColumns().add(column("Seller / MM", 95, TradeDto::counterparty));
        historyTable.getColumns().add(column("Option", 90, TradeDto::option));
        historyTable.getColumns().add(column("Qty", 50, t -> String.valueOf(t.quantity())));
        historyTable.getColumns().add(column("Price", 60, t -> UiFormat.money(t.price())));
        historyTable.getColumns().add(column("Commission", 80, t -> UiFormat.money(t.commission())));
        historyTable.getColumns().add(column("Total", 70, t -> UiFormat.money(t.total())));

        TabPane bottomTabs = new TabPane(
                new Tab("Participations information", participantsTable),
                new Tab("Trade history", historyTable));
        bottomTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        bottomTabs.setMinHeight(170);

        Label optionsTitle = new Label("Options");
        optionsTitle.getStyleClass().add("subsection-title");
        content = new VBox(10, nameLabel, descriptionLabel, infoGrid, actions, optionsTitle, optionsGrid, bottomTabs);
        placeholder.getStyleClass().add("hint-text");
        showPlaceholder();
    }

    void showPlaceholder() {
        layoutKey = null;
        getChildren().setAll(placeholder);
    }

    void show(EventDetailsDto event) {
        if (getChildren().size() != 1 || getChildren().get(0) != content) {
            getChildren().setAll(content);
        }
        nameLabel.setText(event.name() + "  (id " + event.id() + ")");
        descriptionLabel.setText(event.description());
        fillInfo(event);

        String key = event.id() + "|" + event.type() + "|" + event.options().size();
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            buildOptionBoxes(event);
            buildParticipantColumns(event);
        }
        for (int i = 0; i < optionBoxes.size(); i++) {
            optionBoxes.get(i).update(event.options().get(i), event.winner());
        }
        participantsTable.getItems().setAll(event.participants());
        historyTable.getItems().setAll(event.history());
    }

    private void fillInfo(EventDetailsDto event) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Status", event.status() + (event.winner() != null ? " - winner: " + event.winner() : "")});
        rows.add(new String[] {"Method", event.type()});
        rows.add(new String[] {"Commission", UiFormat.commission(event.commission(), event.commissionType())});
        rows.add(new String[] {"Market maker", event.marketMaker()});
        if ("LMSR".equals(event.type())) {
            rows.add(new String[] {"LMSR b", UiFormat.money(event.b())});
        } else {
            rows.add(new String[] {"Order book", "d = " + event.d() + ", initial = " + event.initial()
                    + ", allow-mint = " + event.allowMint()});
        }
        rows.add(new String[] {"Opening cost", UiFormat.money(event.openingCost())});
        rows.add(new String[] {"Event account", UiFormat.money(event.account())});
        rows.add(new String[] {"Collected commission", UiFormat.money(event.collectedCommission())});

        infoGrid.getChildren().clear();
        int half = (rows.size() + 1) / 2;
        for (int i = 0; i < rows.size(); i++) {
            Label key = new Label(rows.get(i)[0] + ":");
            key.getStyleClass().add("info-key");
            key.setMinWidth(Region.USE_PREF_SIZE);
            Label value = new Label(rows.get(i)[1]);
            value.setWrapText(true);
            int column = i < half ? 0 : 2;
            int row = i < half ? i : i - half;
            infoGrid.add(key, column, row);
            infoGrid.add(value, column + 1, row);
        }
    }

    private void buildOptionBoxes(EventDetailsDto event) {
        optionBoxes.clear();
        optionsGrid.getChildren().clear();
        boolean orderBook = !"LMSR".equals(event.type());
        for (int i = 0; i < event.options().size(); i++) {
            OptionBox box = new OptionBox(orderBook);
            optionBoxes.add(box);
            optionsGrid.add(box, i % OPTION_COLUMNS, i / OPTION_COLUMNS);
        }
    }

    private void buildParticipantColumns(EventDetailsDto event) {
        participantsTable.getColumns().clear();
        participantsTable.getColumns().add(column("User", 110, ParticipantDto::user));
        for (int i = 0; i < event.options().size(); i++) {
            int index = i;
            String option = event.options().get(i).name();
            participantsTable.getColumns().add(column(option + " shares", 90,
                    p -> index < p.shares().size() ? String.valueOf(p.shares().get(index)) : "0"));
            participantsTable.getColumns().add(column(option + " paid", 90,
                    p -> index < p.paid().size() ? UiFormat.money(p.paid().get(index)) : UiFormat.money(0)));
        }
    }

    private static <T> TableColumn<T, String> column(String title, int width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** One option: its order book (order-book events) or its current LMSR price. */
    private static final class OptionBox extends VBox {
        private final Label title = new Label();
        private final Label stats = new Label();
        private final ProgressBar priceBar = new ProgressBar(0);
        private final TableView<OrderDto> book = new TableView<>();

        OptionBox(boolean orderBook) {
            setSpacing(6);
            setPadding(new Insets(8));
            getStyleClass().add("option-box");
            title.getStyleClass().add("option-title");
            stats.setWrapText(true);
            if (orderBook) {
                book.setPlaceholder(new Label("No waiting orders."));
                book.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
                book.setPrefHeight(150);
                book.setMinHeight(110);
                book.getColumns().add(column("Side", 55, OrderDto::side));
                book.getColumns().add(column("Price", 60, o -> UiFormat.money(o.price())));
                book.getColumns().add(column("Qty", 50, o -> String.valueOf(o.quantity())));
                book.getColumns().add(column("User", 90, OrderDto::user));
                book.setRowFactory(table -> new TableRow<>() {
                    @Override
                    protected void updateItem(OrderDto order, boolean empty) {
                        super.updateItem(order, empty);
                        getStyleClass().removeAll("credit-row", "debit-row");
                        if (!empty && order != null) {
                            getStyleClass().add("BUY".equals(order.side()) ? "credit-row" : "debit-row");
                        }
                    }
                });
                getChildren().addAll(title, stats, book);
            } else {
                priceBar.setMaxWidth(Double.MAX_VALUE);
                getChildren().addAll(title, priceBar, stats);
            }
        }

        void update(OptionDto option, String winner) {
            boolean won = option.name().equals(winner);
            title.setText(option.number() + ") " + option.name() + (won ? "  - WINNER" : ""));
            BookDto orders = option.book();
            if (orders == null) {
                priceBar.setProgress(option.price());
                stats.setText("Price: " + UiFormat.money(option.price())
                        + "   |   Shares in market: " + option.outstandingShares());
                return;
            }
            stats.setText("Last " + UiFormat.moneyOrDash(orders.last())
                    + "  Bid " + UiFormat.moneyOrDash(orders.bestBid())
                    + "  Ask " + UiFormat.moneyOrDash(orders.bestAsk())
                    + "  Mid " + UiFormat.moneyOrDash(orders.mid())
                    + "  Spread " + UiFormat.moneyOrDash(orders.spread())
                    + "\nShares in market: " + option.outstandingShares());
            List<OrderDto> rows = new ArrayList<>(orders.asks().size() + orders.bids().size());
            List<OrderDto> asks = new ArrayList<>(orders.asks());
            Collections.reverse(asks);
            rows.addAll(asks);
            rows.addAll(orders.bids());
            book.getItems().setAll(rows);
        }
    }
}
