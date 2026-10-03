package guessmarket.dto;

import java.util.List;

/** A user's position in one event: shares and amount paid per option, in option order. */
public record ParticipantDto(String user, List<Integer> shares, List<Double> paid) {
}
