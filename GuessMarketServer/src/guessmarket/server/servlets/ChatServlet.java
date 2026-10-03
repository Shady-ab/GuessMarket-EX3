package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.ChatDto;
import guessmarket.dto.MessageDto;
import guessmarket.server.chat.ChatManager;
import guessmarket.server.utils.ApiServlet;
import guessmarket.server.utils.HttpError;
import guessmarket.server.utils.ServletUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Bonus: one global chat room. GET returns only the lines after {@code version} (delta fetching). */
@WebServlet(name = "ChatServlet", urlPatterns = Api.CHAT)
public class ChatServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handleGet(HttpServletRequest request, String user) throws Exception {
        int from = intParam(request, Api.PARAM_CHAT_VERSION, 0);
        ChatManager chat = chat();
        synchronized (chat) {
            return new ChatDto(chat.getVersion(), chat.getLinesFrom(from));
        }
    }

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        String text = requiredParam(request, Api.PARAM_MESSAGE);
        if (text.length() > ChatManager.MAX_MESSAGE_LENGTH) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST,
                    "Chat messages are limited to " + ChatManager.MAX_MESSAGE_LENGTH + " characters.");
        }
        chat().add(user, text);
        return MessageDto.ok("Sent.");
    }

    private ChatManager chat() {
        return ServletUtils.getChatManager(getServletContext());
    }
}
