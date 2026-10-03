package guessmarket.dto;

import java.util.List;

/** Chat lines after the version the client asked for, plus the server's current version (delta fetching). */
public record ChatDto(int version, List<ChatLineDto> entries) {
}
