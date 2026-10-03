package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.server.utils.ApiServlet;
import guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "LogoutServlet", urlPatterns = Api.LOGOUT)
public class LogoutServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected Object handlePost(HttpServletRequest request, String user) {
        if (user != null) {
            engine().logout(user);
        }
        SessionUtils.clear(request);
        return MessageDto.ok("Logged out.");
    }
}
