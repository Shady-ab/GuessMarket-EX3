package guessmarket.dto;

/** One option of an event. {@code book} is set only for order-book events. */
public record OptionDto(int number, String name, int outstandingShares, double price, BookDto book) {
}
