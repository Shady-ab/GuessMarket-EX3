package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.server.DtoMapper;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "UsersServlet", urlPatterns = Api.USERS)
public class UsersServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handleGet(HttpServletRequest request, String user) {
        return DtoMapper.users(engine());
    }
}
