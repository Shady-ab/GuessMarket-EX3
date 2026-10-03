package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.MarketEvent;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "OpenEventServlet", urlPatterns = Api.EVENT_OPEN)
public class OpenEventServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        int eventId = intParam(request, Api.PARAM_EVENT_ID);
        engine().openEvent(user, eventId);
        MarketEvent event = engine().getEventById(eventId);
        return MessageDto.ok("Event '" + event.getName() + "' is now active. Opening cost paid: "
                + String.format("%.2f", event.getOpeningCost()) + ".");
    }
}
