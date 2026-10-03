package guessmarket.dto;

/** Endpoint paths and parameter names shared by the server servlets and the client. */
public final class Api {
    public static final String CONTEXT_PATH = "/GuessMarket";

    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";
    public static final String UPLOAD = "/upload";
    public static final String EVENTS = "/events";
    public static final String EVENT = "/event";
    public static final String EVENT_OPEN = "/event/open";
    public static final String EVENT_CLOSE = "/event/close";
    public static final String EVENT_BUY = "/event/buy";
    public static final String EVENT_ORDER = "/event/order";
    public static final String USERS = "/users";
    public static final String ME = "/me";
    public static final String DEPOSIT = "/deposit";
    public static final String CHAT = "/chat";

    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_EVENT_ID = "id";
    public static final String PARAM_OPTION = "option";
    public static final String PARAM_WINNER = "winner";
    public static final String PARAM_QUANTITY = "quantity";
    public static final String PARAM_SIDE = "side";
    public static final String PARAM_PRICE = "price";
    public static final String PARAM_AMOUNT = "amount";
    public static final String PARAM_LEDGER_FROM = "ledgerFrom";
    public static final String PARAM_CHAT_VERSION = "version";
    public static final String PARAM_MESSAGE = "message";
    public static final String PART_FILE = "file";

    private Api() {
    }
}
