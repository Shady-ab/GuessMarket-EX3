package guessmarket.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketEvent {
    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionType commissionType;
    private final MarketType marketType;
    private final List<MarketOption> options;
    private final Double b;
    private final Integer initialInvestment;
    private final Integer d;
    private final boolean allowMint;
    private final String marketMakerName;
    private final List<TradeRecord> tradeHistory = new ArrayList<>();
    private final OptionOrderBook[] books;

    private EventStatus status = EventStatus.NOT_STARTED;
    private Integer winningOptionIndex;
    private double accountBalance;
    private double collectedCommission;
    private long nextOrderId = 1;

    MarketEvent(int id, EventDefinition definition, String marketMakerName) {
        if (marketMakerName == null || marketMakerName.isBlank()) {
            throw new IllegalArgumentException("Every event must have a market maker.");
        }
        this.id = id;
        this.name = definition.name();
        this.description = definition.description();
        this.commissionPercentage = definition.commission();
        this.commissionType = definition.commissionType();
        this.marketType = definition.marketType();
        this.b = definition.b();
        this.initialInvestment = definition.initial();
        this.d = definition.d();
        this.allowMint = definition.allowMint();
        this.marketMakerName = marketMakerName.trim();
        List<MarketOption> optionList = new ArrayList<>();
        for (String optionName : definition.options()) {
            optionList.add(new MarketOption(optionName));
        }
        this.options = List.copyOf(optionList);
        this.books = new OptionOrderBook[options.size()];
        for (int i = 0; i < books.length; i++) {
            books[i] = new OptionOrderBook();
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercentage() {
        return commissionPercentage;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public MarketType getMarketType() {
        return marketType;
    }

    public List<MarketOption> getOptions() {
        return options;
    }

    public int getOptionCount() {
        return options.size();
    }

    public Double getB() {
        return b;
    }

    public Integer getInitialInvestment() {
        return initialInvestment;
    }

    public Integer getD() {
        return d;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public String getMarketMakerName() {
        return marketMakerName;
    }

    public EventStatus getStatus() {
        return status;
    }

    public double getAccountBalance() {
        return Money.round(accountBalance);
    }

    public double getCollectedCommission() {
        return Money.round(collectedCommission);
    }

    public Integer getWinningOptionIndex() {
        return winningOptionIndex;
    }

    public String getWinningOptionName() {
        return winningOptionIndex == null ? null : options.get(winningOptionIndex).getName();
    }

    /** LMSR subsidy for n options: C(0,...,0) = b * ln(n). */
    public double getRequiredSubsidy() {
        if (marketType != MarketType.LMSR) {
            return 0;
        }
        return Money.round(b * Math.log(options.size()));
    }

    public double getOpeningCost() {
        if (marketType == MarketType.LMSR) {
            return getRequiredSubsidy();
        }
        return initialInvestment == null ? 0 : initialInvestment;
    }

    /** Payout per winning share: 1 for LMSR, d for an order book. */
    public double getUnitPayout() {
        return marketType == MarketType.ORDER_BOOK ? d : 1.0;
    }

    public double getCurrentPrice(int optionIndex) {
        validateOptionIndex(optionIndex);
        if (marketType != MarketType.LMSR) {
            Double last = books[optionIndex].getLastPrice();
            return last == null ? Money.round(d / (double) options.size()) : last;
        }
        return softmaxPrice(optionIndex, outstandingVector());
    }

    public List<TradeRecord> getTradeHistoryNewestFirst() {
        List<TradeRecord> result = new ArrayList<>(tradeHistory);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public OptionBookSnapshot getBookSnapshot(int optionIndex) {
        validateOptionIndex(optionIndex);
        OptionOrderBook book = books[optionIndex];
        return new OptionBookSnapshot(options.get(optionIndex).getName(), book.getLastPrice(),
                book.bestBid(), book.bestAsk(), book.snapshotBids(), book.snapshotAsks());
    }

    /** Users that hold shares, have resting orders or traded in this event, with their share count per option. */
    public Map<String, int[]> holdingsByUser(Map<String, User> users) {
        Map<String, int[]> result = new LinkedHashMap<>();
        for (User user : users.values()) {
            int[] shares = new int[options.size()];
            boolean holdsAny = false;
            for (int i = 0; i < shares.length; i++) {
                shares[i] = user.getHolding(id, i);
                holdsAny |= shares[i] > 0;
            }
            if (holdsAny || hasRestingOrder(user.getName()) || user.getParticipatingEventIds().contains(id)) {
                result.put(user.getName(), shares);
            }
        }
        return result;
    }

    void open(User marketMaker) throws GuessMarketException {
        if (status != EventStatus.NOT_STARTED) {
            throw new GuessMarketException("Event '" + name + "' cannot be opened because it is "
                    + status.displayName() + ".");
        }
        if (!marketMaker.getName().equals(marketMakerName)) {
            throw new GuessMarketException("Only market maker " + marketMakerName + " can open event '" + name + "'.");
        }
        marketMaker.ensureCanAct();
        double cost = getOpeningCost();
        if (marketMaker.getCash() < cost) {
            throw new GuessMarketException("Market maker " + marketMaker.getName()
                    + " does not have enough cash to open event '" + name + "'. Required: "
                    + String.format("%.2f", cost) + ", available: " + String.format("%.2f", marketMaker.getCash())
                    + ". Use Deposit to add money.");
        }
        if (cost > 0) {
            String reason = marketType == MarketType.LMSR ? "LMSR subsidy" : "initial investment";
            marketMaker.debit(cost, "Opened event '" + name + "' (" + reason + ")");
            accountBalance = Money.round(accountBalance + cost);
        }
        if (marketType == MarketType.ORDER_BOOK && initialInvestment > 0) {
            int sets = initialInvestment / d;
            if (sets > 0) {
                double paidPerOption = Money.round(sets * d / (double) options.size());
                for (int i = 0; i < options.size(); i++) {
                    options.get(i).addShares(sets);
                    marketMaker.addHolding(id, i, sets, paidPerOption);
                }
            }
        }
        marketMaker.markParticipant(id);
        status = EventStatus.ACTIVE;
    }

    BuyResult buyLmsr(User buyer, int optionIndex, int quantity, User marketMaker) throws GuessMarketException {
        ensureActive();
        if (marketType != MarketType.LMSR) {
            throw new GuessMarketException("Event '" + name + "' is not an LMSR event.");
        }
        buyer.ensureCanAct();
        validateOptionIndex(optionIndex);
        if (quantity <= 0) {
            throw new GuessMarketException("Share quantity must be a positive whole number.");
        }

        int[] before = outstandingVector();
        int[] after = before.clone();
        after[optionIndex] = Math.addExact(after[optionIndex], quantity);

        double shareCost = Money.round(costFunction(after) - costFunction(before));
        if (!Double.isFinite(shareCost) || shareCost < 0) {
            throw new GuessMarketException("The LMSR calculation produced an invalid trade cost.");
        }
        double commission = purchaseCommission(shareCost);
        double total = Money.round(shareCost + commission);
        String optionName = options.get(optionIndex).getName();
        buyer.debit(total, "Bought " + quantity + " x '" + optionName + "' in '" + name + "' (LMSR"
                + (commission > 0 ? ", incl. commission " + String.format("%.2f", commission) : "") + ")");
        accountBalance = Money.round(accountBalance + shareCost);
        payCommissionToMm(marketMaker, commission, buyer.getName());
        options.get(optionIndex).addShares(quantity);
        buyer.addHolding(id, optionIndex, quantity, shareCost);
        buyer.addCommission(id, commission);
        buyer.markParticipant(id);

        TradeRecord record = new TradeRecord(TradeRecord.Kind.LMSR_BUY, buyer.getName(), marketMakerName,
                optionName, OrderSide.BUY, quantity, shareCost / quantity, shareCost, commission);
        tradeHistory.add(record);
        buyer.addPersonalTrade(id, record);
        return new BuyResult(buyer.getName(), optionName, quantity, shareCost, commission, "LMSR purchase completed.");
    }

    OrderResult placeOrder(User actor, int optionIndex, OrderSide side, int quantity, double price,
                           Map<String, User> users, User marketMaker) throws GuessMarketException {
        ensureActive();
        if (marketType != MarketType.ORDER_BOOK) {
            throw new GuessMarketException("Event '" + name + "' is not an order-book event.");
        }
        actor.ensureCanAct();
        validateOptionIndex(optionIndex);
        if (quantity <= 0) {
            throw new GuessMarketException("Share quantity must be a positive whole number.");
        }
        double roundedPrice = Money.round(price);
        double maxPrice = Money.round(d - 0.01);
        if (roundedPrice < 0.01 || roundedPrice > maxPrice) {
            throw new GuessMarketException("Price must be between 0.01 and " + String.format("%.2f", maxPrice)
                    + " (d - 0.01).");
        }
        if (side == OrderSide.SELL && actor.getHolding(id, optionIndex) < quantity) {
            throw new GuessMarketException("User " + actor.getName() + " does not hold enough '"
                    + options.get(optionIndex).getName() + "' shares to sell.");
        }

        BookOrder incoming = new BookOrder(nextOrderId++, actor.getName(), optionIndex, side, roundedPrice, quantity);
        actor.markParticipant(id);
        StringBuilder log = new StringBuilder();
        FillTally tally = new FillTally();

        if (side == OrderSide.BUY) {
            matchAgainstAsks(incoming, users, marketMaker, log, tally);
            if (incoming.getRemainingQuantity() > 0 && allowMint) {
                mintCompleteSets(incoming, users, marketMaker, log, tally);
            }
        } else {
            matchAgainstBids(incoming, users, marketMaker, log, tally);
        }

        if (incoming.getRemainingQuantity() > 0) {
            books[optionIndex].add(incoming);
            log.append("Resting ").append(incoming.getRemainingQuantity())
                    .append(" at ").append(String.format("%.2f", incoming.getPrice())).append(". ");
        }

        return new OrderResult(tally.filled, incoming.getRemainingQuantity(), tally.shareCost, tally.commission,
                log.toString().trim());
    }

    /** Accumulates what the acting user actually filled, spent and paid in commission for a single order. */
    private static final class FillTally {
        private int filled;
        private double shareCost;
        private double commission;

        void add(int quantity, double cost, double commissionPaid) {
            filled += quantity;
            shareCost = Money.round(shareCost + cost);
            commission = Money.round(commission + commissionPaid);
        }
    }

    private void matchAgainstAsks(BookOrder buy, Map<String, User> users, User marketMaker, StringBuilder log,
                                  FillTally tally) {
        OptionOrderBook book = books[buy.getOptionIndex()];
        User buyer = users.get(buy.getUserName());
        while (buy.getRemainingQuantity() > 0) {
            BookOrder ask = book.peekBestAsk();
            if (ask == null || ask.getPrice() > buy.getPrice()) {
                break;
            }
            User seller = users.get(ask.getUserName());
            if (seller == null || buyer == null) {
                break;
            }
            int sellerShares = seller.getHolding(id, buy.getOptionIndex());
            if (sellerShares <= 0) {
                OptionOrderBook.consumeBest(book.askLevels(), ask.getRemainingQuantity());
                continue;
            }
            int qty = Math.min(buy.getRemainingQuantity(), Math.min(ask.getRemainingQuantity(), sellerShares));
            double commission = executeTrade(buyer, seller, buy.getOptionIndex(), qty, ask.getPrice(), marketMaker);
            buy.fill(qty);
            OptionOrderBook.consumeBest(book.askLevels(), qty);
            tally.add(qty, Money.round(qty * ask.getPrice()), commission);
            log.append("Matched ").append(qty).append(" with ").append(seller.getName())
                    .append(" at ").append(String.format("%.2f", ask.getPrice())).append(". ");
        }
    }

    private void matchAgainstBids(BookOrder sell, Map<String, User> users, User marketMaker, StringBuilder log,
                                  FillTally tally) {
        OptionOrderBook book = books[sell.getOptionIndex()];
        User seller = users.get(sell.getUserName());
        while (sell.getRemainingQuantity() > 0) {
            BookOrder bid = book.peekBestBid();
            if (bid == null || bid.getPrice() < sell.getPrice()) {
                break;
            }
            User buyer = users.get(bid.getUserName());
            if (seller == null || buyer == null) {
                break;
            }
            int available = seller.getHolding(id, sell.getOptionIndex());
            if (available <= 0) {
                break;
            }
            int qty = Math.min(sell.getRemainingQuantity(), Math.min(bid.getRemainingQuantity(), available));
            executeTrade(buyer, seller, sell.getOptionIndex(), qty, bid.getPrice(), marketMaker);
            sell.fill(qty);
            OptionOrderBook.consumeBest(book.bidLevels(), qty);
            // For a SELL the acting user receives the trade value; the commission is charged to the buyer.
            tally.add(qty, Money.round(qty * bid.getPrice()), 0);
            log.append("Matched ").append(qty).append(" with ").append(buyer.getName())
                    .append(" at ").append(String.format("%.2f", bid.getPrice())).append(". ");
        }
    }

    /** Moves shares and cash between two users at the resting order's price. Returns the buyer's commission. */
    private double executeTrade(User buyer, User seller, int optionIndex, int qty, double price, User marketMaker) {
        String optionName = options.get(optionIndex).getName();
        double tradeValue = Money.round(qty * price);
        double commission = purchaseCommission(tradeValue);
        String priceText = String.format("%.2f", price);
        buyer.debit(Money.round(tradeValue + commission), "Bought " + qty + " x '" + optionName + "' from "
                + seller.getName() + " @" + priceText + " in '" + name + "'"
                + (commission > 0 ? " (incl. commission " + String.format("%.2f", commission) + ")" : ""));
        seller.credit(tradeValue, "Sold " + qty + " x '" + optionName + "' to " + buyer.getName()
                + " @" + priceText + " in '" + name + "'");
        payCommissionToMm(marketMaker, commission, buyer.getName());
        seller.removeHolding(id, optionIndex, qty);
        buyer.addHolding(id, optionIndex, qty, tradeValue);
        buyer.addCommission(id, commission);
        books[optionIndex].setLastPrice(price);
        TradeRecord buyRec = new TradeRecord(TradeRecord.Kind.ORDER_MATCH, buyer.getName(), seller.getName(),
                optionName, OrderSide.BUY, qty, price, tradeValue, commission);
        TradeRecord sellRec = new TradeRecord(TradeRecord.Kind.ORDER_MATCH, seller.getName(), buyer.getName(),
                optionName, OrderSide.SELL, qty, price, tradeValue, 0);
        tradeHistory.add(buyRec);
        buyer.addPersonalTrade(id, buyRec);
        seller.addPersonalTrade(id, sellRec);
        buyer.markParticipant(id);
        seller.markParticipant(id);
        return commission;
    }

    /**
     * Mint of complete sets (one share of every option). It happens when the incoming BUY together with the best
     * resting BUY of every other option reach at least d. Resting orders pay their own price and the incoming order
     * pays the complement up to d. With two options this is exactly the binary YES/NO mint.
     */
    private void mintCompleteSets(BookOrder incomingBuy, Map<String, User> users, User marketMaker,
                                  StringBuilder log, FillTally tally) {
        int incomingIndex = incomingBuy.getOptionIndex();
        User incomingUser = users.get(incomingBuy.getUserName());
        if (incomingUser == null) {
            return;
        }
        while (incomingBuy.getRemainingQuantity() > 0) {
            List<BookOrder> restingBids = new ArrayList<>();
            double restingSum = 0;
            int qty = incomingBuy.getRemainingQuantity();
            for (int j = 0; j < options.size(); j++) {
                if (j == incomingIndex) {
                    continue;
                }
                BookOrder bid = books[j].peekBestBid();
                if (bid == null || users.get(bid.getUserName()) == null) {
                    return;
                }
                restingBids.add(bid);
                restingSum = Money.round(restingSum + bid.getPrice());
                qty = Math.min(qty, bid.getRemainingQuantity());
            }
            if (Money.round(incomingBuy.getPrice() + restingSum) < d) {
                return;
            }
            double incomingUnit = Money.round(Math.max(0, d - restingSum));
            double incomingPay = Money.round(qty * incomingUnit);
            double incomingCommission = purchaseCommission(incomingPay);
            String incomingOption = options.get(incomingIndex).getName();

            StringBuilder partners = new StringBuilder();
            for (BookOrder bid : restingBids) {
                User other = users.get(bid.getUserName());
                int otherIndex = bid.getOptionIndex();
                String otherOption = options.get(otherIndex).getName();
                double otherPay = Money.round(qty * bid.getPrice());
                double otherCommission = purchaseCommission(otherPay);
                other.debit(Money.round(otherPay + otherCommission), "Minted " + qty + " x '" + otherOption
                        + "' @" + String.format("%.2f", bid.getPrice()) + " in '" + name + "'"
                        + (otherCommission > 0 ? " (incl. commission " + String.format("%.2f", otherCommission) + ")" : ""));
                accountBalance = Money.round(accountBalance + otherPay);
                payCommissionToMm(marketMaker, otherCommission, other.getName());
                options.get(otherIndex).addShares(qty);
                other.addHolding(id, otherIndex, qty, otherPay);
                other.addCommission(id, otherCommission);
                other.markParticipant(id);
                books[otherIndex].setLastPrice(bid.getPrice());
                OptionOrderBook.consumeBest(books[otherIndex].bidLevels(), qty);
                TradeRecord otherRec = new TradeRecord(TradeRecord.Kind.MINT, other.getName(), incomingUser.getName(),
                        otherOption, OrderSide.BUY, qty, bid.getPrice(), otherPay, otherCommission);
                tradeHistory.add(otherRec);
                other.addPersonalTrade(id, otherRec);
                partners.append(partners.isEmpty() ? "" : ", ").append(other.getName());
            }

            incomingUser.debit(Money.round(incomingPay + incomingCommission), "Minted " + qty + " x '"
                    + incomingOption + "' @" + String.format("%.2f", incomingUnit) + " in '" + name + "'"
                    + (incomingCommission > 0 ? " (incl. commission " + String.format("%.2f", incomingCommission) + ")" : ""));
            accountBalance = Money.round(accountBalance + incomingPay);
            payCommissionToMm(marketMaker, incomingCommission, incomingUser.getName());
            options.get(incomingIndex).addShares(qty);
            incomingUser.addHolding(id, incomingIndex, qty, incomingPay);
            incomingUser.addCommission(id, incomingCommission);
            incomingUser.markParticipant(id);
            books[incomingIndex].setLastPrice(incomingUnit);
            incomingBuy.fill(qty);
            tally.add(qty, incomingPay, incomingCommission);
            TradeRecord incomingRec = new TradeRecord(TradeRecord.Kind.MINT, incomingUser.getName(),
                    partners.toString(), incomingOption, OrderSide.BUY, qty, incomingUnit, incomingPay,
                    incomingCommission);
            tradeHistory.add(incomingRec);
            incomingUser.addPersonalTrade(id, incomingRec);
            log.append("Minted ").append(qty).append(" set(s) with ").append(partners).append(". ");
        }
    }

    CloseResult close(User marketMaker, int winningOptionIndex, Map<String, User> users) throws GuessMarketException {
        ensureActive();
        if (!marketMaker.getName().equals(marketMakerName)) {
            throw new GuessMarketException("Only market maker " + marketMakerName + " can close event '" + name + "'.");
        }
        marketMaker.ensureCanAct();
        validateOptionIndex(winningOptionIndex);

        for (OptionOrderBook book : books) {
            book.cancelAll();
        }

        int winningShares = 0;
        for (User user : users.values()) {
            winningShares += user.getHolding(id, winningOptionIndex);
        }

        double unitPayout = getUnitPayout();
        double grossPayout = Money.round(winningShares * unitPayout);
        double closeCommissionRate = commissionType == CommissionType.ON_CLOSE ? commissionPercentage / 100.0 : 0.0;
        String winnerName = options.get(winningOptionIndex).getName();

        double totalCommission = 0;
        for (User user : users.values()) {
            int shares = user.getHolding(id, winningOptionIndex);
            if (shares <= 0) {
                continue;
            }
            double gross = Money.round(shares * unitPayout);
            double commission = Money.round(gross * closeCommissionRate);
            double net = Money.round(gross - commission);
            accountBalance = Money.round(accountBalance - gross);
            user.credit(net, "Payout for " + shares + " x '" + winnerName + "' in '" + name + "'"
                    + (commission > 0 ? " (after commission " + String.format("%.2f", commission) + ")" : ""));
            if (commission > 0) {
                marketMaker.credit(commission, "Close commission from " + user.getName() + " in '" + name + "'");
                collectedCommission = Money.round(collectedCommission + commission);
                user.addCommission(id, commission);
                totalCommission = Money.round(totalCommission + commission);
            }
        }

        double leftover = Money.round(accountBalance);
        if (leftover > 0) {
            marketMaker.credit(leftover, "Leftover of event '" + name + "' account returned");
        } else if (leftover < 0) {
            marketMaker.debit(-leftover, "Covered deficit of event '" + name + "' account");
        }
        accountBalance = 0;

        status = EventStatus.CLOSED;
        this.winningOptionIndex = winningOptionIndex;
        return new CloseResult(winnerName, winningShares, grossPayout, totalCommission,
                Money.round(grossPayout - totalCommission), leftover);
    }

    private boolean hasRestingOrder(String userName) {
        for (OptionOrderBook book : books) {
            for (BookOrder order : book.allOrders()) {
                if (order.getUserName().equals(userName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double purchaseCommission(double shareCost) {
        if (commissionType != CommissionType.ON_PURCHASE) {
            return 0;
        }
        return Money.round(shareCost * commissionPercentage / 100.0);
    }

    private void payCommissionToMm(User marketMaker, double commission, String payerName) {
        if (commission <= 0) {
            return;
        }
        marketMaker.credit(commission, "Commission from " + payerName + " in '" + name + "'");
        collectedCommission = Money.round(collectedCommission + commission);
    }

    private void ensureActive() throws GuessMarketException {
        if (status != EventStatus.ACTIVE) {
            throw new GuessMarketException("Event '" + name + "' is not active (status: " + status.displayName() + ").");
        }
    }

    private int[] outstandingVector() {
        int[] q = new int[options.size()];
        for (int i = 0; i < q.length; i++) {
            q[i] = options.get(i).getOutstandingShares();
        }
        return q;
    }

    /** C(q) = b * ln(sum_i e^(q_i / b)), computed with the log-sum-exp trick to avoid overflow. */
    private double costFunction(int[] q) {
        double max = Double.NEGATIVE_INFINITY;
        for (int value : q) {
            max = Math.max(max, value / b);
        }
        double sum = 0;
        for (int value : q) {
            sum += Math.exp(value / b - max);
        }
        return b * (max + Math.log(sum));
    }

    private double softmaxPrice(int optionIndex, int[] q) {
        double max = Double.NEGATIVE_INFINITY;
        for (int value : q) {
            max = Math.max(max, value / b);
        }
        double denominator = 0;
        for (int value : q) {
            denominator += Math.exp(value / b - max);
        }
        return Math.exp(q[optionIndex] / b - max) / denominator;
    }

    private void validateOptionIndex(int optionIndex) throws IllegalArgumentException {
        if (optionIndex < 0 || optionIndex >= options.size()) {
            throw new IllegalArgumentException("Option index must be between 0 and " + (options.size() - 1) + ".");
        }
    }
}
