package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.CloseResult;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "CloseEventServlet", urlPatterns = Api.EVENT_CLOSE)
public class CloseEventServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        CloseResult result = engine().closeEvent(user, intParam(request, Api.PARAM_EVENT_ID),
                intParam(request, Api.PARAM_WINNER));
        return MessageDto.ok("Event closed.\nWinner: " + result.getWinningOptionName()
                + "\nWinning shares: " + result.getWinningShares()
                + "\nGross payout: " + String.format("%.2f", result.getGrossPayout())
                + "\nCommission: " + String.format("%.2f", result.getCommission())
                + "\nNet payout: " + String.format("%.2f", result.getNetPayout())
                + "\nReturned to market maker: " + String.format("%.2f", result.getReturnedToMarketMaker()));
    }
}
