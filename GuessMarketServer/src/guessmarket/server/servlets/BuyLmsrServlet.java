package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.BuyResult;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "BuyLmsrServlet", urlPatterns = Api.EVENT_BUY)
public class BuyLmsrServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        BuyResult result = engine().buyLmsrShares(user, intParam(request, Api.PARAM_EVENT_ID),
                intParam(request, Api.PARAM_OPTION), intParam(request, Api.PARAM_QUANTITY));
        return MessageDto.ok("Purchase completed.\nOption: " + result.getOptionName()
                + "\nShares: " + result.getQuantity()
                + "\nShare cost: " + String.format("%.2f", result.getShareCost())
                + "\nCommission: " + String.format("%.2f", result.getCommission())
                + "\nTotal paid: " + String.format("%.2f", result.getTotalPaid()));
    }
}
