package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.server.utils.ApiServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

@WebServlet(name = "DepositServlet", urlPatterns = Api.DEPOSIT)
public class DepositServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        double amount = doubleParam(request, Api.PARAM_AMOUNT);
        engine().deposit(user, amount);
        return MessageDto.ok("Deposited " + String.format("%.2f", amount) + ". New balance: "
                + String.format("%.2f", engine().getUserByName(user).getCash()) + ".");
    }
}
