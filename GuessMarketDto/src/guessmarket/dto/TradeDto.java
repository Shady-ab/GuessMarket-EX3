package guessmarket.dto;

public record TradeDto(String time, String kind, String user, String counterparty, String option, String side,
                       int quantity, double price, double shareCost, double commission, double total) {
}
