package guessmarket.server.utils;

import com.google.gson.Gson;
import guessmarket.engine.GuessMarketEngine;
import guessmarket.engine.GuessMarketEngineImpl;
import guessmarket.server.chat.ChatManager;
import jakarta.servlet.ServletContext;

/** Application-wide singletons, stored as ServletContext attributes and created lazily. */
public final class ServletUtils {
    public static final Gson GSON = new Gson();

    private static final String ENGINE_ATTRIBUTE = "guessMarketEngine";
    private static final String CHAT_ATTRIBUTE = "guessMarketChat";
    private static final Object ENGINE_LOCK = new Object();
    private static final Object CHAT_LOCK = new Object();

    private ServletUtils() {
    }

    public static GuessMarketEngine getEngine(ServletContext context) {
        synchronized (ENGINE_LOCK) {
            Object engine = context.getAttribute(ENGINE_ATTRIBUTE);
            if (engine == null) {
                engine = new GuessMarketEngineImpl();
                context.setAttribute(ENGINE_ATTRIBUTE, engine);
            }
            return (GuessMarketEngine) engine;
        }
    }

    public static ChatManager getChatManager(ServletContext context) {
        synchronized (CHAT_LOCK) {
            Object chat = context.getAttribute(CHAT_ATTRIBUTE);
            if (chat == null) {
                chat = new ChatManager();
                context.setAttribute(CHAT_ATTRIBUTE, chat);
            }
            return (ChatManager) chat;
        }
    }
}
