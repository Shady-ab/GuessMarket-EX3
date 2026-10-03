package guessmarket.client.ui;

import guessmarket.dto.BookDto;
import guessmarket.dto.EventDetailsDto;
import guessmarket.dto.HoldingDto;
import guessmarket.dto.OptionDto;
import guessmarket.dto.OrderDto;
import guessmarket.dto.ParticipantDto;
import guessmarket.dto.TradeDto;

import java.util.List;

/** Text renderings of an event and of the user's involvement in it (the Exercise-2 detail views, for N options). */
final class EventText {
    private EventText() {
    }

    static String details(EventDetailsDto event) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(event.id()).append('\n');
        sb.append("Name: ").append(event.name()).append('\n');
        sb.append("Description: ").append(event.description()).append('\n');
        sb.append("Status: ").append(event.status()).append('\n');
        sb.append("Type: ").append(event.type()).append('\n');
        sb.append("Options: ").append(event.options().size()).append('\n');
        sb.append("Commission: ").append(UiFormat.commission(event.commission(), event.commissionType())).append('\n');
        sb.append("Market maker: ").append(event.marketMaker()).append('\n');
        sb.append("Opening cost: ").append(UiFormat.money(event.openingCost())).append('\n');
        sb.append("Event account: ").append(UiFormat.money(event.account())).append('\n');
        sb.append("Collected commission: ").append(UiFormat.money(event.collectedCommission())).append('\n');
        if (event.winner() != null) {
            sb.append("WINNER: ").append(event.winner()).append('\n');
        }

        if ("LMSR".equals(event.type())) {
            sb.append("LMSR b: ").append(UiFormat.money(event.b())).append('\n');
            sb.append("\nCurrent market:\n");
            for (OptionDto option : event.options()) {
                sb.append("  ").append(option.number()).append(") ").append(option.name())
                        .append(" | value: ").append(UiFormat.money(option.price()))
                        .append(" | shares: ").append(option.outstandingShares()).append('\n');
            }
            sb.append("\nTrade history (newest first):\n");
            if (event.history().isEmpty()) {
                sb.append("  No trades yet.\n");
            }
            for (TradeDto trade : event.history()) {
                sb.append("  ").append(trade.time()).append(" | ").append(trade.user())
                        .append(" | ").append(trade.option())
                        .append(" | shares: ").append(trade.quantity())
                        .append(" | paid: ").append(UiFormat.money(trade.total())).append('\n');
            }
        } else {
            sb.append("d: ").append(event.d()).append(" | initial: ").append(event.initial())
                    .append(" | allow-mint: ").append(event.allowMint()).append('\n');
            for (OptionDto option : event.options()) {
                BookDto book = option.book();
                sb.append('\n').append(option.number()).append(") ").append(option.name())
                        .append(" book (shares in market: ").append(option.outstandingShares()).append("):\n");
                sb.append("  LAST=").append(UiFormat.moneyOrDash(book.last()))
                        .append(" BID=").append(UiFormat.moneyOrDash(book.bestBid()))
                        .append(" ASK=").append(UiFormat.moneyOrDash(book.bestAsk()))
                        .append(" MID=").append(UiFormat.moneyOrDash(book.mid()))
                        .append(" SPREAD=").append(UiFormat.moneyOrDash(book.spread())).append('\n');
                sb.append("  Bids:\n");
                appendOrders(sb, book.bids());
                sb.append("  Asks:\n");
                appendOrders(sb, book.asks());
            }
            sb.append("\nTrade history (newest first):\n");
            if (event.history().isEmpty()) {
                sb.append("  No trades yet.\n");
            }
            for (TradeDto trade : event.history()) {
                sb.append("  ").append(trade.time()).append(" | ").append(trade.kind())
                        .append(" | ").append(trade.user()).append(" <- ").append(trade.counterparty())
                        .append(" | ").append(trade.option())
                        .append(" | qty ").append(trade.quantity())
                        .append(" @ ").append(UiFormat.money(trade.price())).append('\n');
            }
        }

        sb.append("\nParticipants:\n");
        if (event.participants().isEmpty()) {
            sb.append("  none\n");
        }
        for (ParticipantDto participant : event.participants()) {
            sb.append("  ").append(participant.user());
            for (int i = 0; i < event.options().size() && i < participant.shares().size(); i++) {
                sb.append(" | ").append(event.options().get(i).name()).append('=').append(participant.shares().get(i))
                        .append(" (").append(UiFormat.money(participant.paid().get(i))).append(')');
            }
            sb.append('\n');
        }
        return sb.toString();
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

    private static void appendOrders(StringBuilder sb, List<OrderDto> orders) {
        if (orders.isEmpty()) {
            sb.append("    none\n");
            return;
        }
        for (OrderDto order : orders) {
            sb.append("    ").append(order.user())
                    .append(" | ").append(order.side())
                    .append(" | qty ").append(order.quantity())
                    .append(" | price ").append(UiFormat.money(order.price())).append('\n');
        }
    }
}
