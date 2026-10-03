package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.OrderResult;
import guessmarket.engine.OrderSide;
import guessmarket.server.utils.ApiServlet;
import guessmarket.server.utils.HttpError;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Locale;

@WebServlet(name = "PlaceOrderServlet", urlPatterns = Api.EVENT_ORDER)
public class PlaceOrderServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        OrderSide side;
        String sideText = requiredParam(request, Api.PARAM_SIDE);
        try {
            side = OrderSide.valueOf(sideText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST, "Side must be BUY or SELL (got '" + sideText + "').");
        }
        OrderResult result = engine().placeOrder(user, intParam(request, Api.PARAM_EVENT_ID),
                intParam(request, Api.PARAM_OPTION), side, intParam(request, Api.PARAM_QUANTITY),
                doubleParam(request, Api.PARAM_PRICE));
        return MessageDto.ok("Order processed.\nFilled: " + result.getFilledQuantity()
                + "\nResting in book: " + result.getRemainingQuantity()
                + "\nValue: " + String.format("%.2f", result.getShareCost())
                + "\nCommission: " + String.format("%.2f", result.getCommission())
                + (result.getSummary().isEmpty() ? "" : "\n" + result.getSummary()));
    }
}
