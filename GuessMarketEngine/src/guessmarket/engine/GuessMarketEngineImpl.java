package guessmarket.engine;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuessMarketEngineImpl implements GuessMarketEngine {
    /** A user that sent no request for this long is considered offline. */
    public static final long ONLINE_TIMEOUT_MILLIS = 6_000;
    public static final double MAX_DEPOSIT = 1_000_000_000;

    private final Map<Integer, MarketEvent> events = new LinkedHashMap<>();
    private final Map<String, User> users = new LinkedHashMap<>();
    private final EventXmlParser parser = new EventXmlParser();
    private int nextEventId = 1;

    @Override
    public synchronized User login(String userName) throws GuessMarketException {
        String name = normalizeName(userName);
        User existing = users.get(name);
        if (existing != null) {
            if (isOnline(existing)) {
                throw new GuessMarketException("The user name '" + name
                        + "' already exists and is logged in. Please choose a different name.");
            }
            existing.touch(System.currentTimeMillis());
            return existing;
        }
        User user = new User(name);
        user.touch(System.currentTimeMillis());
        users.put(name, user);
        return user;
    }

    @Override
    public synchronized void logout(String userName) {
        User user = userName == null ? null : users.get(userName.trim());
        if (user != null) {
            user.markOffline();
        }
    }

    @Override
    public synchronized void touch(String userName) {
        User user = userName == null ? null : users.get(userName.trim());
        if (user != null) {
            user.touch(System.currentTimeMillis());
        }
    }

    @Override
    public synchronized boolean isOnline(User user) {
        return user.getLastSeenMillis() > 0
                && System.currentTimeMillis() - user.getLastSeenMillis() <= ONLINE_TIMEOUT_MILLIS;
    }

    @Override
    public List<MarketEvent> addEventsFromXml(InputStream xml, String uploaderName) throws GuessMarketException {
        // Parsing runs outside the lock so a slow upload does not block other users.
        List<EventDefinition> definitions = parser.parse(xml);
        synchronized (this) {
            User uploader = getUserByName(uploaderName);
            for (EventDefinition definition : definitions) {
                for (MarketEvent existing : events.values()) {
                    if (existing.getName().equals(definition.name())) {
                        throw new GuessMarketException("An event named '" + definition.name()
                                + "' already exists in the system. The file was not loaded.");
                    }
                }
            }
            List<MarketEvent> added = new ArrayList<>();
            for (EventDefinition definition : definitions) {
                MarketEvent event = new MarketEvent(nextEventId++, definition, uploader.getName());
                events.put(event.getId(), event);
                uploader.addMarketMakerEvent(event.getId());
                added.add(event);
            }
            return Collections.unmodifiableList(added);
        }
    }

    @Override
    public synchronized List<MarketEvent> getEvents() {
        return List.copyOf(events.values());
    }

    @Override
    public synchronized MarketEvent getEventById(int eventId) throws GuessMarketException {
        MarketEvent event = events.get(eventId);
        if (event == null) {
            throw new GuessMarketException("Event id " + eventId + " was not found.");
        }
        return event;
    }

    @Override
    public synchronized List<User> getUsers() {
        return List.copyOf(users.values());
    }

    @Override
    public synchronized Map<String, User> getUsersByName() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(users));
    }

    @Override
    public synchronized User getUserByName(String name) throws GuessMarketException {
        if (name == null || name.isBlank()) {
            throw new GuessMarketException("User name must not be empty.");
        }
        User user = users.get(name.trim());
        if (user == null) {
            throw new GuessMarketException("User " + name + " was not found.");
        }
        return user;
    }

    @Override
    public synchronized void deposit(String userName, double amount) throws GuessMarketException {
        if (!Double.isFinite(amount) || Money.round(amount) <= 0) {
            throw new GuessMarketException("Deposit amount must be a positive number.");
        }
        if (amount > MAX_DEPOSIT) {
            throw new GuessMarketException("Deposit amount is too large (max " + String.format("%.0f", MAX_DEPOSIT) + ").");
        }
        getUserByName(userName).deposit(Money.round(amount));
    }

    @Override
    public synchronized void openEvent(String marketMakerName, int eventId) throws GuessMarketException {
        getEventById(eventId).open(getUserByName(marketMakerName));
    }

    @Override
    public synchronized BuyResult buyLmsrShares(String userName, int eventId, int optionNumber, int quantity)
            throws GuessMarketException {
        MarketEvent event = getEventById(eventId);
        validateOptionNumber(event, optionNumber);
        User buyer = getUserByName(userName);
        User mm = getUserByName(event.getMarketMakerName());
        try {
            return event.buyLmsr(buyer, optionNumber - 1, quantity, mm);
        } catch (ArithmeticException ex) {
            throw new GuessMarketException("The share quantity is too large.", ex);
        }
    }

    @Override
    public synchronized OrderResult placeOrder(String userName, int eventId, int optionNumber, OrderSide side,
                                               int quantity, double price) throws GuessMarketException {
        if (side == null) {
            throw new GuessMarketException("Order side must be BUY or SELL.");
        }
        MarketEvent event = getEventById(eventId);
        validateOptionNumber(event, optionNumber);
        User actor = getUserByName(userName);
        User mm = getUserByName(event.getMarketMakerName());
        try {
            return event.placeOrder(actor, optionNumber - 1, side, quantity, price, users, mm);
        } catch (ArithmeticException ex) {
            throw new GuessMarketException("The share quantity is too large.", ex);
        }
    }

    @Override
    public synchronized CloseResult closeEvent(String marketMakerName, int eventId, int winningOptionNumber)
            throws GuessMarketException {
        MarketEvent event = getEventById(eventId);
        validateOptionNumber(event, winningOptionNumber);
        return event.close(getUserByName(marketMakerName), winningOptionNumber - 1, users);
    }

    private static void validateOptionNumber(MarketEvent event, int optionNumber) throws GuessMarketException {
        if (optionNumber < 1 || optionNumber > event.getOptionCount()) {
            throw new GuessMarketException("Option number must be between 1 and " + event.getOptionCount() + ".");
        }
    }

    private static String normalizeName(String userName) throws GuessMarketException {
        if (userName == null || userName.isBlank()) {
            throw new GuessMarketException("User name must not be empty.");
        }
        String name = userName.trim();
        if (name.length() > 40) {
            throw new GuessMarketException("User name must be at most 40 characters.");
        }
        return name;
    }
}
