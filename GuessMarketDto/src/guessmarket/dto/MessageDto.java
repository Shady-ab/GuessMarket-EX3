package guessmarket.dto;

/** Generic reply: {@code message} on success, {@code error} on failure. */
public record MessageDto(String message, String error) {
    public static MessageDto ok(String message) {
        return new MessageDto(message, null);
    }

    public static MessageDto failure(String error) {
        return new MessageDto(null, error);
    }
}
