package guessmarket.server;

import guessmarket.dto.BookDto;
import guessmarket.dto.EventDetailsDto;
import guessmarket.dto.EventSummaryDto;
import guessmarket.dto.HoldingDto;
import guessmarket.dto.LedgerLineDto;
import guessmarket.dto.MyUserDto;
import guessmarket.dto.OptionDto;
import guessmarket.dto.OrderDto;
import guessmarket.dto.ParticipantDto;
import guessmarket.dto.TradeDto;
import guessmarket.dto.UserSummaryDto;
import guessmarket.engine.BookOrder;
import guessmarket.engine.EventStatus;
import guessmarket.engine.GuessMarketEngine;
import guessmarket.engine.GuessMarketException;
import guessmarket.engine.LedgerEntry;
import guessmarket.engine.MarketEvent;
import guessmarket.engine.MarketType;
import guessmarket.engine.Money;
import guessmarket.engine.OptionBookSnapshot;
import guessmarket.engine.TradeRecord;
import guessmarket.engine.User;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts engine objects into the JSON DTOs. Every method locks the engine so that the snapshot it builds
 * is consistent even while other requests are trading.
 */
public final class DtoMapper {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private DtoMapper() {
    }

    public static List<EventSummaryDto> events(GuessMarketEngine engine) {
        synchronized (engine) {
            List<EventSummaryDto> result = new ArrayList<>();
            for (MarketEvent event : engine.getEvents()) {
                result.add(new EventSummaryDto(event.getId(), event.getName(), event.getStatus().displayName(),
                        typeName(event), event.getOptionCount(), event.getCommissionPercentage(),
                        event.getCommissionType().getXmlValue(), event.getMarketMakerName(),
                        event.getAccountBalance(), event.getWinningOptionName()));
            }
            return result;
        }
    }

    public static EventDetailsDto eventDetails(GuessMarketEngine engine, int eventId) throws GuessMarketException {
        synchronized (engine) {
            MarketEvent event = engine.getEventById(eventId);
            boolean lmsr = event.getMarketType() == MarketType.LMSR;
            List<OptionDto> options = new ArrayList<>();
            for (int i = 0; i < event.getOptionCount(); i++) {
                BookDto book = lmsr ? null : book(event.getBookSnapshot(i));
                options.add(new OptionDto(i + 1, event.getOptions().get(i).getName(),
                        event.getOptions().get(i).getOutstandingShares(), Money.round(event.getCurrentPrice(i)), book));
            }
            List<ParticipantDto> participants = new ArrayList<>();
            Map<String, User> users = engine.getUsersByName();
            event.holdingsByUser(users).forEach((name, shares) -> {
                User user = users.get(name);
                List<Integer> shareList = new ArrayList<>();
                List<Double> paid = new ArrayList<>();
                for (int i = 0; i < shares.length; i++) {
                    shareList.add(shares[i]);
                    paid.add(user.getAmountPaid(event.getId(), i));
                }
                participants.add(new ParticipantDto(name, shareList, paid));
            });
            return new EventDetailsDto(event.getId(), event.getName(), event.getDescription(),
                    event.getStatus().displayName(), typeName(event), event.getCommissionPercentage(),
                    event.getCommissionType().getXmlValue(), event.getMarketMakerName(), event.getAccountBalance(),
                    event.getCollectedCommission(), event.getOpeningCost(), event.getB(), event.getD(),
                    event.getInitialInvestment(), lmsr ? null : event.isAllowMint(), options,
                    trades(event.getTradeHistoryNewestFirst()), participants, event.getWinningOptionName());
        }
    }

    public static List<UserSummaryDto> users(GuessMarketEngine engine) {
        synchronized (engine) {
            List<UserSummaryDto> result = new ArrayList<>();
            for (User user : engine.getUsers()) {
                result.add(new UserSummaryDto(user.getName(), user.getCash(), user.isMarketMaker(),
                        engine.isOnline(user), user.isBlocked()));
            }
            return result;
        }
    }

    public static MyUserDto me(GuessMarketEngine engine, String userName, int ledgerFrom) throws GuessMarketException {
        synchronized (engine) {
            User user = engine.getUserByName(userName);
            Set<Integer> eventIds = new LinkedHashSet<>(user.getMarketMakerEventIds());
            eventIds.addAll(user.getParticipatingEventIds());
            List<String> mmOf = new ArrayList<>();
            List<HoldingDto> holdings = new ArrayList<>();
            for (int eventId : eventIds) {
                MarketEvent event = engine.getEventById(eventId);
                if (user.isMarketMakerOf(eventId)) {
                    mmOf.add(event.getName());
                }
                holdings.add(holding(user, event));
            }
            List<LedgerEntry> ledger = user.getLedger();
            List<LedgerLineDto> lines = new ArrayList<>();
            for (int i = Math.max(0, ledgerFrom); i < ledger.size(); i++) {
                LedgerEntry entry = ledger.get(i);
                lines.add(new LedgerLineDto(entry.getSequence(), entry.getTimestamp().format(TIME),
                        entry.getDescription(), entry.getAmount(), entry.getBalanceAfter()));
            }
            return new MyUserDto(user.getName(), user.getCash(), user.isBlocked(), user.getLastNotice(), mmOf,
                    holdings, lines, ledger.size());
        }
    }

    private static HoldingDto holding(User user, MarketEvent event) {
        List<String> names = new ArrayList<>();
        List<Integer> shares = new ArrayList<>();
        List<Double> paid = new ArrayList<>();
        double totalPaid = 0;
        for (int i = 0; i < event.getOptionCount(); i++) {
            names.add(event.getOptions().get(i).getName());
            shares.add(user.getHolding(event.getId(), i));
            paid.add(user.getAmountPaid(event.getId(), i));
            totalPaid += user.getAmountPaid(event.getId(), i);
        }
        Double profit = null;
        if (event.getStatus() == EventStatus.CLOSED) {
            double payout = user.getHolding(event.getId(), event.getWinningOptionIndex()) * event.getUnitPayout();
            profit = Money.round(payout - totalPaid - user.getCommissionPaid(event.getId()));
        }
        return new HoldingDto(event.getId(), event.getName(), event.getStatus().displayName(), typeName(event),
                user.isMarketMakerOf(event.getId()), names, shares, paid, user.getCommissionPaid(event.getId()),
                trades(user.getPersonalTradesNewestFirst(event.getId())), event.getWinningOptionName(), profit);
    }

    private static BookDto book(OptionBookSnapshot snapshot) {
        return new BookDto(snapshot.getLast(), snapshot.getBestBid(), snapshot.getBestAsk(), snapshot.getMid(),
                snapshot.getSpread(), orders(snapshot.getBids()), orders(snapshot.getAsks()));
    }

    private static List<OrderDto> orders(List<BookOrder> orders) {
        List<OrderDto> result = new ArrayList<>();
        for (BookOrder order : orders) {
            result.add(new OrderDto(order.getUserName(), order.getSide().name(), order.getRemainingQuantity(),
                    order.getPrice()));
        }
        return result;
    }

    private static List<TradeDto> trades(List<TradeRecord> records) {
        List<TradeDto> result = new ArrayList<>();
        for (TradeRecord trade : records) {
            result.add(new TradeDto(trade.getTimestamp().format(TIME), trade.getKind().name(), trade.getUserName(),
                    trade.getCounterparty(), trade.getOptionName(), trade.getSide().name(), trade.getQuantity(),
                    trade.getPricePerShare(), trade.getShareCost(), trade.getCommission(), trade.getTotalPaid()));
        }
        return result;
    }

    private static String typeName(MarketEvent event) {
        return event.getMarketType() == MarketType.LMSR ? "LMSR" : "Order Book";
    }
}
