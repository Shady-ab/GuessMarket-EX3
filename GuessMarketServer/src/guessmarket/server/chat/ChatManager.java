package guessmarket.server.chat;

import guessmarket.dto.ChatLineDto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Holds the single global chat room. The version of the chat is the number of lines in it. */
public final class ChatManager {
    public static final int MAX_MESSAGE_LENGTH = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<ChatLineDto> lines = new ArrayList<>();

    public synchronized void add(String user, String text) {
        lines.add(new ChatLineDto(user, text, LocalTime.now().format(TIME)));
    }

    public synchronized int getVersion() {
        return lines.size();
    }

    public synchronized List<ChatLineDto> getLinesFrom(int fromVersion) {
        int from = Math.max(0, Math.min(fromVersion, lines.size()));
        return new ArrayList<>(lines.subList(from, lines.size()));
    }
}
