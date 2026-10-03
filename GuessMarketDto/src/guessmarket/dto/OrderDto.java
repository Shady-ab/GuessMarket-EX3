package guessmarket.dto;

public record OrderDto(String user, String side, int quantity, double price) {
}
