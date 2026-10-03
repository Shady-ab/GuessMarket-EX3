package guessmarket.dto;

/** One row of the events table. */
public record EventSummaryDto(int id, String name, String status, String type, int optionCount, int commission,
                              String commissionType, String marketMaker, double account, String winner) {
}
