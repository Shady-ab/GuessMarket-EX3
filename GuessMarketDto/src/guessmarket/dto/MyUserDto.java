package guessmarket.dto;

import java.util.List;

/**
 * The private view of the logged-in user. {@code ledger} holds only the lines after the requested
 * {@code ledgerFrom}; {@code ledgerSize} is the total number of lines (delta fetching).
 */
public record MyUserDto(String name, double balance, boolean blocked, String notice, List<String> marketMakerOf,
                        List<HoldingDto> events, List<LedgerLineDto> ledger, int ledgerSize) {
}
