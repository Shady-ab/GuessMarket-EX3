package guessmarket.dto;

import java.util.List;

/** Everything shown for a selected event. Fields that do not apply to the event's method are null. */
public record EventDetailsDto(int id, String name, String description, String status, String type, int commission,
                              String commissionType, String marketMaker, double account, double collectedCommission,
                              double openingCost, Double b, Integer d, Integer initial, Boolean allowMint,
                              List<OptionDto> options, List<TradeDto> history, List<ParticipantDto> participants,
                              String winner) {
}
