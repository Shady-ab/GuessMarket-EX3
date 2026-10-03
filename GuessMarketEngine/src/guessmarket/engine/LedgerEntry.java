package guessmarket.engine;

import java.time.LocalDateTime;

/** One line in a user's account: every change of the cash balance produces exactly one entry. */
public final class LedgerEntry {
    private final int sequence;
    private final LocalDateTime timestamp;
    private final String description;
    private final double amount;
    private final double balanceAfter;

    LedgerEntry(int sequence, String description, double amount, double balanceAfter) {
        this.sequence = sequence;
        this.timestamp = LocalDateTime.now();
        this.description = description;
        this.amount = Money.round(amount);
        this.balanceAfter = Money.round(balanceAfter);
    }

    public int getSequence() {
        return sequence;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}
