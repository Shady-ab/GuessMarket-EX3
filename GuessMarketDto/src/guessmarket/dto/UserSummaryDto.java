package guessmarket.dto;

/** What every user can see about any other user. */
public record UserSummaryDto(String name, double balance, boolean marketMaker, boolean online, boolean blocked) {
}
