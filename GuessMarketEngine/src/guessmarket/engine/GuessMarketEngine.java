package guessmarket.engine;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * The Guess Market engine of Exercise 3. One instance is shared by all clients on the server, so every
 * implementation must be thread safe. Callers that read several values and need a consistent snapshot
 * should synchronize on the engine instance.
 */
public interface GuessMarketEngine {
    /** Registers a new user, or reconnects to an existing account whose owner is offline. */
    User login(String userName) throws GuessMarketException;

    void logout(String userName);

    /** Marks the user as seen now (used to tell online users from offline ones). */
    void touch(String userName);

    boolean isOnline(User user);

    /** Validates the whole file first; only when it is fully valid are its events added, with the uploader as MM. */
    List<MarketEvent> addEventsFromXml(InputStream xml, String uploaderName) throws GuessMarketException;

    List<MarketEvent> getEvents();

    MarketEvent getEventById(int eventId) throws GuessMarketException;

    List<User> getUsers();

    Map<String, User> getUsersByName();

    User getUserByName(String name) throws GuessMarketException;

    void deposit(String userName, double amount) throws GuessMarketException;

    void openEvent(String marketMakerName, int eventId) throws GuessMarketException;

    BuyResult buyLmsrShares(String userName, int eventId, int optionNumber, int quantity) throws GuessMarketException;

    OrderResult placeOrder(String userName, int eventId, int optionNumber, OrderSide side,
                           int quantity, double price) throws GuessMarketException;

    CloseResult closeEvent(String marketMakerName, int eventId, int winningOptionNumber) throws GuessMarketException;
}
