package guessmarket.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class User {
    private final String name;
    private final Set<Integer> marketMakerEventIds = new LinkedHashSet<>();
    private double cash;
    private boolean blocked;
    private String lastNotice;
    private long lastSeenMillis;
    private final Set<Integer> participatingEventIds = new LinkedHashSet<>();
    private final Map<Integer, int[]> holdings = new HashMap<>();
    private final Map<Integer, double[]> amountPaid = new HashMap<>();
    private final Map<Integer, Double> commissionPaid = new HashMap<>();
    private final Map<Integer, List<TradeRecord>> personalTrades = new HashMap<>();
    private final List<LedgerEntry> ledger = new ArrayList<>();

    User(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("User name must not be empty.");
        }
        this.name = name.trim();
    }

    public String getName() {
        return name;
    }

    public double getCash() {
        return cash;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getLastNotice() {
        return lastNotice;
    }

    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public Set<Integer> getMarketMakerEventIds() {
        return Collections.unmodifiableSet(marketMakerEventIds);
    }

    public boolean isMarketMaker() {
        return !marketMakerEventIds.isEmpty();
    }

    public boolean isMarketMakerOf(int eventId) {
        return marketMakerEventIds.contains(eventId);
    }

    public Set<Integer> getParticipatingEventIds() {
        return Collections.unmodifiableSet(participatingEventIds);
    }

    public int getHolding(int eventId, int optionIndex) {
        int[] shares = holdings.get(eventId);
        if (shares == null || optionIndex >= shares.length) {
            return 0;
        }
        return shares[optionIndex];
    }

    public double getAmountPaid(int eventId, int optionIndex) {
        double[] paid = amountPaid.get(eventId);
        if (paid == null || optionIndex >= paid.length) {
            return 0;
        }
        return Money.round(paid[optionIndex]);
    }

    public double getCommissionPaid(int eventId) {
        return Money.round(commissionPaid.getOrDefault(eventId, 0.0));
    }

    public List<TradeRecord> getPersonalTradesNewestFirst(int eventId) {
        List<TradeRecord> list = new ArrayList<>(personalTrades.getOrDefault(eventId, List.of()));
        Collections.reverse(list);
        return Collections.unmodifiableList(list);
    }

    public List<LedgerEntry> getLedger() {
        return Collections.unmodifiableList(new ArrayList<>(ledger));
    }

    public int getLedgerSize() {
        return ledger.size();
    }

    void touch(long nowMillis) {
        lastSeenMillis = nowMillis;
    }

    void markOffline() {
        lastSeenMillis = 0;
    }

    void addMarketMakerEvent(int eventId) {
        marketMakerEventIds.add(eventId);
    }

    void ensureCanAct() throws GuessMarketException {
        if (blocked) {
            throw new GuessMarketException("User " + name
                    + " is blocked because the account balance is negative. Deposit money to unblock.");
        }
    }

    void markParticipant(int eventId) {
        participatingEventIds.add(eventId);
    }

    void addHolding(int eventId, int optionIndex, int quantity, double paidForShares) {
        int[] shares = holdings.compute(eventId, (id, current) -> grow(current, optionIndex));
        shares[optionIndex] += quantity;
        double[] paid = amountPaid.compute(eventId, (id, current) -> grow(current, optionIndex));
        paid[optionIndex] = Money.round(paid[optionIndex] + paidForShares);
    }

    void removeHolding(int eventId, int optionIndex, int quantity) {
        int current = getHolding(eventId, optionIndex);
        if (quantity > current) {
            throw new IllegalArgumentException("User does not hold enough shares.");
        }
        holdings.get(eventId)[optionIndex] = current - quantity;
        double remainingPaid = getAmountPaid(eventId, optionIndex);
        if (current > 0) {
            double portion = remainingPaid * quantity / (double) current;
            amountPaid.compute(eventId, (id, paid) -> grow(paid, optionIndex))[optionIndex] =
                    Money.round(Math.max(0, remainingPaid - portion));
        }
    }

    void addCommission(int eventId, double amount) {
        if (amount > 0) {
            commissionPaid.put(eventId, Money.round(getCommissionPaid(eventId) + amount));
        }
    }

    void addPersonalTrade(int eventId, TradeRecord record) {
        personalTrades.computeIfAbsent(eventId, id -> new ArrayList<>()).add(record);
    }

    void credit(double amount, String description) {
        if (amount == 0) {
            return;
        }
        cash = Money.round(cash + amount);
        record(description, amount);
    }

    /** A debit that exceeds the balance is allowed on purpose: the account goes negative and is then blocked. */
    void debit(double amount, String description) {
        if (amount == 0) {
            return;
        }
        cash = Money.round(cash - amount);
        record(description, -amount);
        if (cash < 0 && !blocked) {
            blocked = true;
            lastNotice = "Account " + name + " went negative (cash=" + String.format("%.2f", cash)
                    + ") and is now blocked from further actions until a deposit covers the debt.";
        }
    }

    void deposit(double amount) {
        credit(amount, "Deposit");
        if (blocked && cash >= 0) {
            blocked = false;
            lastNotice = "Deposit covered the negative balance. Account " + name + " is unblocked.";
        }
    }

    private void record(String description, double amount) {
        ledger.add(new LedgerEntry(ledger.size() + 1, description, amount, cash));
    }

    private static int[] grow(int[] current, int index) {
        if (current == null) {
            return new int[index + 1];
        }
        return current.length > index ? current : Arrays.copyOf(current, index + 1);
    }

    private static double[] grow(double[] current, int index) {
        if (current == null) {
            return new double[index + 1];
        }
        return current.length > index ? current : Arrays.copyOf(current, index + 1);
    }
}
