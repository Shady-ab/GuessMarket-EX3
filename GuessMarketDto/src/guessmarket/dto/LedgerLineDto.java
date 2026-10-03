package guessmarket.dto;

public record LedgerLineDto(int number, String time, String description, double amount, double balanceAfter) {
}
