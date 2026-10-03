package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.User;
import guessmarket.server.utils.ApiServlet;
import guessmarket.server.utils.HttpError;
import guessmarket.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(name = "LoginServlet", urlPatterns = Api.LOGIN)
public class LoginServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected Object handlePost(HttpServletRequest request, String currentUser) throws Exception {
        String requested = requiredParam(request, Api.PARAM_USERNAME);
        if (requested.equals(currentUser)) {
            return MessageDto.ok("Already logged in as " + currentUser + ".");
        }
        if (currentUser != null) {
            engine().logout(currentUser);
        }
        try {
            User user = engine().login(requested);
            SessionUtils.setUsername(request, user.getName());
            return MessageDto.ok("Welcome, " + user.getName() + "!");
        } catch (guessmarket.engine.GuessMarketException ex) {
            throw new HttpError(HttpServletResponse.SC_CONFLICT, ex.getMessage());
        }
    }
}
