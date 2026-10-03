package guessmarket.client.ui;

import guessmarket.dto.HoldingDto;
import guessmarket.dto.TradeDto;

/** Text rendering of the user's involvement in one event (the Exercise-2 involvement view, for N options). */
final class EventText {
    private EventText() {
    }

    static String involvement(HoldingDto holding) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(holding.eventName()).append(" (id ").append(holding.eventId()).append(")\n");
        sb.append("Status: ").append(holding.status()).append(" | Type: ").append(holding.type()).append('\n');
        sb.append("My role: ").append(holding.marketMaker() ? "Market maker" : "Participant").append('\n');
        sb.append("\nMy shares:\n");
        for (int i = 0; i < holding.optionNames().size(); i++) {
            sb.append("  ").append(i + 1).append(") ").append(holding.optionNames().get(i))
                    .append(" | shares: ").append(holding.shares().get(i))
                    .append(" | paid: ").append(UiFormat.money(holding.paid().get(i))).append('\n');
        }
        sb.append("Commission paid: ").append(UiFormat.money(holding.commissionPaid())).append('\n');
        if (holding.winner() != null) {
            sb.append("Winner: ").append(holding.winner()).append('\n');
        }
        if (holding.profit() != null) {
            sb.append("Trading profit/loss after close: ").append(UiFormat.signedMoney(holding.profit())).append('\n');
        }
        sb.append("\nMy trades (newest first):\n");
        if (holding.trades().isEmpty()) {
            sb.append("  No personal trades yet.\n");
        }
        for (TradeDto trade : holding.trades()) {
            sb.append("  ").append(trade.time()).append(" | ").append(trade.side())
                    .append(" | ").append(trade.option())
                    .append(" | shares: ").append(trade.quantity())
                    .append(" | price: ").append(UiFormat.money(trade.price()))
                    .append(" | commission: ").append(UiFormat.money(trade.commission()))
                    .append(" | with: ").append(trade.counterparty()).append('\n');
        }
        return sb.toString();
    }
}
