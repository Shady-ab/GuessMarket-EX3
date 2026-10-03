package guessmarket.dto;

import java.util.List;

/** The logged-in user's involvement in one event. {@code profit} is set only after the event closed. */
public record HoldingDto(int eventId, String eventName, String status, String type, boolean marketMaker,
                         List<String> optionNames, List<Integer> shares, List<Double> paid, double commissionPaid,
                         List<TradeDto> trades, String winner, Double profit) {
}
