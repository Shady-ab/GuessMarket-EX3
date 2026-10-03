package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.server.DtoMapper;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "EventDetailsServlet", urlPatterns = Api.EVENT)
public class EventDetailsServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handleGet(HttpServletRequest request, String user) throws Exception {
        return DtoMapper.eventDetails(engine(), intParam(request, Api.PARAM_EVENT_ID));
    }
}
