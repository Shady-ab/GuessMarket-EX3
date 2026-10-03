package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.server.DtoMapper;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "MeServlet", urlPatterns = Api.ME)
public class MeServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handleGet(HttpServletRequest request, String user) throws Exception {
        return DtoMapper.me(engine(), user, intParam(request, Api.PARAM_LEDGER_FROM, 0));
    }
}
