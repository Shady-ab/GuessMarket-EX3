package guessmarket.client.ui;

import com.google.gson.reflect.TypeToken;
import guessmarket.dto.Api;
import guessmarket.dto.EventDetailsDto;
import guessmarket.dto.EventSummaryDto;
import guessmarket.dto.MyUserDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Events screen: every event in the system, of every type and status, with the Exercise-2 filters, the details
 * of the selected event and the actions the logged-in user may take on it.
 */
final class EventsPane extends BorderPane {
    private static final Type EVENT_LIST = new TypeToken<List<EventSummaryDto>>() { }.getType();

    private final ClientSession session;
    private final TableView<EventRow> eventsTable = new TableView<>();
    private final TextArea detailsArea = new TextArea();
    private final ToggleButton lmsrToggle = filterToggle("LMSR");
    private final ToggleButton orderBookToggle = filterToggle("Order Book");
    private final ToggleButton notStartedToggle = filterToggle("Not started");
    private final ToggleButton activeToggle = filterToggle("Active");
    private final ToggleButton closedToggle = filterToggle("Closed");
    private final ToggleButton onPurchaseToggle = filterToggle("on-purchase");
    private final ToggleButton onCloseToggle = filterToggle("on-close");
    private final ToggleButton mineToggle = new ToggleButton("Only my MM events");
    private final Button openButton = new Button("Open event");
    private final Button closeButton = new Button("Close event");
    private final Button tradeButton = new Button("Participate / Trade");
    private final Label countLabel = new Label();

    private List<EventSummaryDto> events = List.of();
    private String lastEventsJson;
    private String lastDetailsJson;
    private EventDetailsDto details;
    private Integer selectedEventId;
    private boolean blocked;
    private boolean updatingTable;

    EventsPane(ClientSession session) {
        this.session = session;
        setPadding(new Insets(10));

        mineToggle.setOnAction(e -> applyFilters());
        FlowPane filters = new FlowPane(8, 8,
                new Label("Type:"), lmsrToggle, orderBookToggle,
                new Label("Status:"), notStartedToggle, activeToggle, closedToggle,
                new Label("Commission:"), onPurchaseToggle, onCloseToggle, mineToggle, countLabel);
        filters.getStyleClass().add("filters");

        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.setPlaceholder(new Label("No events yet. Use \"Load events XML\" to upload a file."));
        eventsTable.getColumns().add(col("ID", "id", 45));
        eventsTable.getColumns().add(col("Name", "name", 200));
        eventsTable.getColumns().add(col("Status", "status", 90));
        eventsTable.getColumns().add(col("Type", "type", 90));
        eventsTable.getColumns().add(col("Options", "options", 65));
        eventsTable.getColumns().add(col("Commission", "commission", 120));
        eventsTable.getColumns().add(col("Account", "account", 90));
        eventsTable.getColumns().add(col("Market maker", "marketMaker", 110));
        eventsTable.getColumns().add(col("Winner", "winner", 100));
        eventsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (updatingTable) {
                return;
            }
            selectedEventId = newV == null ? null : newV.getEventId();
            details = null;
            lastDetailsJson = null;
            detailsArea.setText(selectedEventId == null ? "Select an event." : "Loading...");
            updateButtons();
            refreshDetails();
        });

        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setText("Select an event.");
        detailsArea.getStyleClass().add("details-area");

        Label eventsTitle = new Label("Events");
        eventsTitle.getStyleClass().add("section-title");
        VBox top = new VBox(8, eventsTitle, filters, eventsTable);
        VBox.setVgrow(eventsTable, Priority.ALWAYS);
        Label detailsTitle = new Label("Selected event");
        detailsTitle.getStyleClass().add("section-title");
        VBox bottom = new VBox(8, detailsTitle, detailsArea);
        VBox.setVgrow(detailsArea, Priority.ALWAYS);
        SplitPane split = new SplitPane(top, bottom);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.45);
        setCenter(split);

        openButton.setOnAction(e -> openSelected());
        closeButton.setOnAction(e -> closeSelected());
        tradeButton.setOnAction(e -> tradeSelected());
        HBox actions = new HBox(10, openButton, closeButton, tradeButton);
        actions.setPadding(new Insets(10, 0, 0, 0));
        setBottom(actions);
        updateButtons();
    }

    /** Pull: the complete events list (all), redrawn only when the JSON changed. */
    CompletableFuture<?> refreshEvents() {
        return session.api().getJson(Api.EVENTS, null).whenComplete((json, error) -> {
            if (error != null) {
                session.pollFailed(error);
                return;
            }
            if (json.equals(lastEventsJson)) {
                return;
            }
            List<EventSummaryDto> parsed = session.api().parse(json, EVENT_LIST);
            Platform.runLater(() -> {
                lastEventsJson = json;
                events = parsed;
                applyFilters();
            });
        });
    }

    /** Pull: the selected event's full details. */
    CompletableFuture<?> refreshDetails() {
        Integer id = selectedEventId;
        if (id == null) {
            return null;
        }
        return session.api().getJson(Api.EVENT, Map.of(Api.PARAM_EVENT_ID, id)).whenComplete((json, error) -> {
            if (error != null) {
                session.pollFailed(error);
                return;
            }
            EventDetailsDto parsed = session.api().parse(json, EventDetailsDto.class);
            Platform.runLater(() -> {
                if (!id.equals(selectedEventId) || json.equals(lastDetailsJson)) {
                    return;
                }
                lastDetailsJson = json;
                details = parsed;
                setTextKeepingScroll(detailsArea, EventText.details(parsed));
                updateButtons();
            });
        });
    }

    void updateMe(MyUserDto me) {
        if (blocked != me.blocked()) {
            blocked = me.blocked();
            updateButtons();
        }
    }

    private void applyFilters() {
        List<EventRow> rows = new ArrayList<>();
        for (EventSummaryDto event : events) {
            if (passesFilters(event)) {
                rows.add(new EventRow(event));
            }
        }
        updatingTable = true;
        try {
            eventsTable.getItems().setAll(rows);
            if (selectedEventId != null) {
                rows.stream().filter(row -> row.getEventId() == selectedEventId).findFirst()
                        .ifPresent(row -> eventsTable.getSelectionModel().select(row));
            }
        } finally {
            updatingTable = false;
        }
        EventRow selected = eventsTable.getSelectionModel().getSelectedItem();
        if (selected == null && selectedEventId != null) {
            selectedEventId = null;
            details = null;
            lastDetailsJson = null;
            detailsArea.setText("Select an event.");
        }
        countLabel.setText("  Showing " + rows.size() + " of " + events.size());
        updateButtons();
    }

    private boolean passesFilters(EventSummaryDto event) {
        boolean typeOk = ("LMSR".equals(event.type()) && lmsrToggle.isSelected())
                || ("Order Book".equals(event.type()) && orderBookToggle.isSelected());
        boolean statusOk = ("Not started".equals(event.status()) && notStartedToggle.isSelected())
                || ("Active".equals(event.status()) && activeToggle.isSelected())
                || ("Closed".equals(event.status()) && closedToggle.isSelected());
        boolean commissionOk = ("on-purchase".equals(event.commissionType()) && onPurchaseToggle.isSelected())
                || ("on-close".equals(event.commissionType()) && onCloseToggle.isSelected());
        boolean mineOk = !mineToggle.isSelected() || session.userName().equals(event.marketMaker());
        return typeOk && statusOk && commissionOk && mineOk;
    }

    private void updateButtons() {
        EventRow row = eventsTable.getSelectionModel().getSelectedItem();
        EventSummaryDto event = row == null ? null : row.dto();
        boolean isMm = event != null && session.userName().equals(event.marketMaker());
        openButton.setDisable(!(isMm && "Not started".equals(event.status())));
        closeButton.setDisable(!(isMm && "Active".equals(event.status())));
        tradeButton.setDisable(!(event != null && "Active".equals(event.status()) && !blocked));
    }

    private void openSelected() {
        if (selectedEventId == null) {
            return;
        }
        session.action(Api.EVENT_OPEN, Map.of(Api.PARAM_EVENT_ID, selectedEventId), this::refreshNow);
    }

    private void closeSelected() {
        EventDetailsDto event = currentDetails();
        if (event == null) {
            return;
        }
        TradeDialogs.chooseWinner(event).ifPresent(winner -> session.action(Api.EVENT_CLOSE,
                Map.of(Api.PARAM_EVENT_ID, event.id(), Api.PARAM_WINNER, winner), this::refreshNow));
    }

    private void tradeSelected() {
        EventDetailsDto event = currentDetails();
        if (event == null) {
            return;
        }
        if ("LMSR".equals(event.type())) {
            TradeDialogs.lmsrBuy(event).ifPresent(buy -> session.action(Api.EVENT_BUY, Map.of(
                    Api.PARAM_EVENT_ID, event.id(),
                    Api.PARAM_OPTION, buy.optionNumber(),
                    Api.PARAM_QUANTITY, buy.quantity()), this::refreshNow));
        } else {
            TradeDialogs.order(event).ifPresent(order -> {
                double price;
                try {
                    price = Double.parseDouble(order.priceText());
                } catch (NumberFormatException ex) {
                    Alerts.error("Price must be a number.");
                    return;
                }
                session.action(Api.EVENT_ORDER, Map.of(
                        Api.PARAM_EVENT_ID, event.id(),
                        Api.PARAM_OPTION, order.optionNumber(),
                        Api.PARAM_SIDE, order.side(),
                        Api.PARAM_QUANTITY, order.quantity(),
                        Api.PARAM_PRICE, price), this::refreshNow);
            });
        }
    }

    private EventDetailsDto currentDetails() {
        if (details == null || selectedEventId == null || details.id() != selectedEventId) {
            Alerts.error("The event details are still loading. Please try again in a moment.");
            return null;
        }
        return details;
    }

    private void refreshNow() {
        refreshEvents();
        refreshDetails();
    }

    private ToggleButton filterToggle(String text) {
        ToggleButton button = new ToggleButton(text);
        button.setSelected(true);
        button.setOnAction(e -> applyFilters());
        return button;
    }

    private static TableColumn<EventRow, String> col(String title, String property, int width) {
        TableColumn<EventRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    static void setTextKeepingScroll(TextArea area, String text) {
        double scrollTop = area.getScrollTop();
        double scrollLeft = area.getScrollLeft();
        area.setText(text);
        Platform.runLater(() -> {
            area.setScrollTop(scrollTop);
            area.setScrollLeft(scrollLeft);
        });
    }
}
