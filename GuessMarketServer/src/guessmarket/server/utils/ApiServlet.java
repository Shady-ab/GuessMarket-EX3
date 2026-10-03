package guessmarket.server.utils;

import guessmarket.dto.MessageDto;
import guessmarket.engine.GuessMarketEngine;
import guessmarket.engine.GuessMarketException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Base class of all JSON endpoints. It checks the session, marks the caller as online, runs the handler and
 * turns the result into JSON. Engine validation errors become HTTP 400 with {"error": "..."}.
 */
public abstract class ApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected boolean requiresLogin() {
        return true;
    }

    /** @param user the logged-in user name, or null when {@link #requiresLogin()} is false and nobody is logged in */
    protected Object handleGet(HttpServletRequest request, String user) throws Exception {
        throw new HttpError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET is not supported here.");
    }

    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        throw new HttpError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST is not supported here.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, true);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, false);
    }

    protected GuessMarketEngine engine() {
        return ServletUtils.getEngine(getServletContext());
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean get) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String user = SessionUtils.getUsername(request);
        if (requiresLogin() && user == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, MessageDto.failure("You must log in first."));
            return;
        }
        if (user != null) {
            engine().touch(user);
        }
        try {
            Object result = get ? handleGet(request, user) : handlePost(request, user);
            write(response, HttpServletResponse.SC_OK, result);
        } catch (HttpError ex) {
            write(response, ex.getStatus(), MessageDto.failure(ex.getMessage()));
        } catch (GuessMarketException | IllegalArgumentException ex) {
            write(response, HttpServletResponse.SC_BAD_REQUEST, MessageDto.failure(ex.getMessage()));
        } catch (Exception ex) {
            log("Unexpected server error", ex);
            write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    MessageDto.failure("Unexpected server error: " + ex.getMessage()));
        }
    }

    private static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.getWriter().print(ServletUtils.GSON.toJson(body));
        response.getWriter().flush();
    }

    protected static String requiredParam(HttpServletRequest request, String name) throws HttpError {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter '" + name + "'.");
        }
        return value.trim();
    }

    protected static int intParam(HttpServletRequest request, String name) throws HttpError {
        String value = requiredParam(request, name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST,
                    "Parameter '" + name + "' must be a whole number (got '" + value + "').");
        }
    }

    protected static int intParam(HttpServletRequest request, String name, int defaultValue) throws HttpError {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? defaultValue : intParam(request, name);
    }

    protected static double doubleParam(HttpServletRequest request, String name) throws HttpError {
        String value = requiredParam(request, name);
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST,
                    "Parameter '" + name + "' must be a number (got '" + value + "').");
        }
    }
}
