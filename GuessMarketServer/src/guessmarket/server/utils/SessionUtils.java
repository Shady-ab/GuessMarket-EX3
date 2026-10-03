package guessmarket.server.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SessionUtils {
    private static final String USERNAME_ATTRIBUTE = "username";

    private SessionUtils() {
    }

    public static String getUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(USERNAME_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    public static void setUsername(HttpServletRequest request, String username) {
        request.getSession(true).setAttribute(USERNAME_ATTRIBUTE, username);
    }

    public static void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
