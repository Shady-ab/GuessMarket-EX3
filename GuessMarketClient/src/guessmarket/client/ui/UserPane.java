package guessmarket.client.ui;

import com.google.gson.reflect.TypeToken;
import guessmarket.dto.Api;
import guessmarket.dto.HoldingDto;
import guessmarket.dto.LedgerLineDto;
import guessmarket.dto.MyUserDto;
import guessmarket.dto.UserSummaryDto;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The private user screen: my balance and deposits, my events and my involvement in each, every line of my
 * account, and a summary of all the other users (name, balance, market maker or not).
 */
final class UserPane extends BorderPane {
    private static final Type USER_LIST = new TypeToken<List<UserSummaryDto>>() { }.getType();

    private final ClientSession session;
    private final Consumer<MyUserDto> onMeUpdated;
    private final Label nameLabel = new Label();
    private final Label balanceLabel = new Label();
    private final Label stateLabel = new Label();
    private final Label noticeLabel = new Label();
    private final TextField depositField = new TextField();
    private final ListView<HoldingDto> myEventsList = new ListView<>();
    private final TextArea involvementArea = new TextArea();
    private final TableView<LedgerLineDto> ledgerTable = new TableView<>();
    private final TableView<UserSummaryDto> usersTable = new TableView<>();

    private int ledgerSize;
    private String lastMeJson;
    private String lastUsersJson;
    private Integer selectedEventId;
    private boolean updatingList;

    UserPane(ClientSession session, Consumer<MyUserDto> onMeUpdated) {
        this.session = session;
        this.onMeUpdated = onMeUpdated;
        setPadding(new Insets(10));
        setTop(buildHeader());

        SplitPane split = new SplitPane(buildMyEvents(), buildLedger(), buildUsers());
        split.setDividerPositions(0.33, 0.70);
        setCenter(split);
    }

    private VBox buildHeader() {
        nameLabel.getStyleClass().add("section-title");
        balanceLabel.getStyleClass().add("balance-label");
        stateLabel.getStyleClass().add("hint-text");
        noticeLabel.getStyleClass().add("error-text");
        noticeLabel.setWrapText(true);
        depositField.setPromptText("Amount");
        depositField.setPrefColumnCount(10);
        depositField.setOnAction(e -> deposit());
        Button depositButton = new Button("Deposit");
        depositButton.setOnAction(e -> deposit());
        FlowPane row = new FlowPane(14, 8, nameLabel, balanceLabel, stateLabel,
                new Label("Load money:"), depositField, depositButton);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(6, row, noticeLabel);
        header.setPadding(new Insets(0, 0, 10, 0));
        return header;
    }

    private VBox buildMyEvents() {
        Label title = new Label("My events");
        title.getStyleClass().add("section-title");
        myEventsList.setPlaceholder(new Label("You are not involved in any event yet."));
        myEventsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(HoldingDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.eventName() + (item.marketMaker() ? "  [MM]" : "") + "  - " + item.status());
            }
        });
        myEventsList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (updatingList) {
                return;
            }
            selectedEventId = newV == null ? null : newV.eventId();
            involvementArea.setText(newV == null ? "Select one of your events." : EventText.involvement(newV));
        });
        involvementArea.setEditable(false);
        involvementArea.setWrapText(true);
        involvementArea.setText("Select one of your events.");
        involvementArea.getStyleClass().add("details-area");
        Label detailsTitle = new Label("My involvement in the selected event");
        VBox box = new VBox(8, title, myEventsList, detailsTitle, involvementArea);
        VBox.setVgrow(myEventsList, Priority.ALWAYS);
        VBox.setVgrow(involvementArea, Priority.ALWAYS);
        box.setPadding(new Insets(0, 8, 0, 0));
        return box;
    }

    private VBox buildLedger() {
        Label title = new Label("Account transactions");
        title.getStyleClass().add("section-title");
        ledgerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ledgerTable.setPlaceholder(new Label("No transactions yet. Start with a deposit."));
        ledgerTable.getColumns().add(textCol("#", 40, line -> String.valueOf(line.number())));
        ledgerTable.getColumns().add(textCol("Time", 70, LedgerLineDto::time));
        ledgerTable.getColumns().add(textCol("Amount", 85, line -> UiFormat.signedMoney(line.amount())));
        ledgerTable.getColumns().add(textCol("Balance", 85, line -> UiFormat.money(line.balanceAfter())));
        ledgerTable.getColumns().add(textCol("Description", 260, LedgerLineDto::description));
        ledgerTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(LedgerLineDto item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("credit-row", "debit-row");
                if (!empty && item != null) {
                    getStyleClass().add(item.amount() >= 0 ? "credit-row" : "debit-row");
                }
            }
        });
        VBox box = new VBox(8, title, ledgerTable);
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        box.setPadding(new Insets(0, 8, 0, 8));
        return box;
    }

    private VBox buildUsers() {
        Label title = new Label("Other users");
        title.getStyleClass().add("section-title");
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("No other users yet."));
        usersTable.getColumns().add(textCol("Name", 110, UserSummaryDto::name));
        usersTable.getColumns().add(textCol("Balance", 85, user -> UiFormat.money(user.balance())
                + (user.blocked() ? " (blocked)" : "")));
        usersTable.getColumns().add(textCol("Market maker", 90, user -> user.marketMaker() ? "Yes" : "No"));
        usersTable.getColumns().add(textCol("Online", 60, user -> user.online() ? "Yes" : "No"));
        VBox box = new VBox(8, title, usersTable);
        VBox.setVgrow(usersTable, Priority.ALWAYS);
        box.setPadding(new Insets(0, 0, 0, 8));
        return box;
    }

    /** Pull: my private data. The ledger is fetched as a delta (only lines I do not have yet). */
    CompletableFuture<?> refreshMe() {
        int from = ledgerSize;
        return session.api().getJson(Api.ME, Map.of(Api.PARAM_LEDGER_FROM, from)).whenComplete((json, error) -> {
            if (error != null) {
                session.pollFailed(error);
                return;
            }
            MyUserDto me = session.api().parse(json, MyUserDto.class);
            Platform.runLater(() -> {
                if (from != ledgerSize) {
                    return;
                }
                if (!me.ledger().isEmpty()) {
                    ledgerTable.getItems().addAll(me.ledger());
                    ledgerTable.scrollTo(ledgerTable.getItems().size() - 1);
                }
                ledgerSize = me.ledgerSize();
                String withoutLedger = guessmarket.client.api.ServerApi.gson().toJson(new MyUserDto(me.name(),
                        me.balance(), me.blocked(), me.notice(), me.marketMakerOf(), me.events(), List.of(),
                        me.ledgerSize()));
                if (withoutLedger.equals(lastMeJson)) {
                    return;
                }
                lastMeJson = withoutLedger;
                applyMe(me);
            });
        });
    }

    /** Pull: all users (all, not delta, since balances of everybody change all the time). */
    CompletableFuture<?> refreshUsers() {
        return session.api().getJson(Api.USERS, null).whenComplete((json, error) -> {
            if (error != null) {
                session.pollFailed(error);
                return;
            }
            if (json.equals(lastUsersJson)) {
                return;
            }
            List<UserSummaryDto> all = session.api().parse(json, USER_LIST);
            List<UserSummaryDto> others = new ArrayList<>();
            for (UserSummaryDto user : all) {
                if (!user.name().equals(session.userName())) {
                    others.add(user);
                }
            }
            Platform.runLater(() -> {
                lastUsersJson = json;
                usersTable.getItems().setAll(others);
            });
        });
    }

    private void applyMe(MyUserDto me) {
        nameLabel.setText(me.name());
        balanceLabel.setText("Balance: " + UiFormat.money(me.balance()));
        String mm = me.marketMakerOf().isEmpty() ? "not a market maker"
                : "market maker of " + me.marketMakerOf().size() + " event(s)";
        stateLabel.setText((me.blocked() ? "BLOCKED | " : "") + mm);
        noticeLabel.setText(me.notice() == null ? "" : me.notice());
        noticeLabel.setVisible(me.notice() != null);
        noticeLabel.setManaged(me.notice() != null);

        updatingList = true;
        try {
            myEventsList.getItems().setAll(me.events());
            HoldingDto selected = null;
            for (HoldingDto holding : me.events()) {
                if (selectedEventId != null && holding.eventId() == selectedEventId) {
                    selected = holding;
                }
            }
            if (selected != null) {
                myEventsList.getSelectionModel().select(selected);
                EventsPane.setTextKeepingScroll(involvementArea, EventText.involvement(selected));
            } else {
                selectedEventId = null;
                involvementArea.setText("Select one of your events.");
            }
        } finally {
            updatingList = false;
        }
        onMeUpdated.accept(me);
    }

    private void deposit() {
        String text = depositField.getText() == null ? "" : depositField.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            Alerts.error("Please enter a positive amount to deposit.");
            return;
        }
        if (!(amount > 0)) {
            Alerts.error("Please enter a positive amount to deposit.");
            return;
        }
        session.action(Api.DEPOSIT, Map.of(Api.PARAM_AMOUNT, amount), () -> {
            depositField.clear();
            refreshMe();
        });
    }

    private static <T> TableColumn<T, String> textCol(String title, int width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
