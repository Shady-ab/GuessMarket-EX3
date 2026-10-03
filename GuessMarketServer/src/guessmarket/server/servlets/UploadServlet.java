package guessmarket.server.servlets;

import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;
import guessmarket.engine.MarketEvent;
import guessmarket.server.utils.ApiServlet;
import guessmarket.server.utils.HttpError;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Receives an events XML file as multipart/form-data. The threshold equals the max size, so Tomcat keeps the
 * part in memory and the file is never written to the server's disk.
 */
@WebServlet(name = "UploadServlet", urlPatterns = Api.UPLOAD)
@MultipartConfig(fileSizeThreshold = UploadServlet.MAX_FILE_BYTES, maxFileSize = UploadServlet.MAX_FILE_BYTES,
        maxRequestSize = UploadServlet.MAX_FILE_BYTES + 64 * 1024)
public class UploadServlet extends ApiServlet {
    static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final long serialVersionUID = 1L;

    @Override
    protected Object handlePost(HttpServletRequest request, String user) throws Exception {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST,
                    "The upload must be sent as multipart/form-data with a '" + Api.PART_FILE + "' part.");
        }
        Part part = request.getPart(Api.PART_FILE);
        if (part == null) {
            part = request.getParts().stream().filter(p -> p.getSubmittedFileName() != null).findFirst().orElse(null);
        }
        if (part == null || part.getSize() == 0) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST, "No file was uploaded (or the file is empty).");
        }
        String fileName = part.getSubmittedFileName() == null ? "uploaded file" : part.getSubmittedFileName();
        if (part.getSubmittedFileName() != null
                && !fileName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new HttpError(HttpServletResponse.SC_BAD_REQUEST, "The file '" + fileName
                    + "' is not an XML file. Only .xml files can be uploaded.");
        }
        List<MarketEvent> added;
        try (InputStream content = part.getInputStream()) {
            added = engine().addEventsFromXml(content, user);
        } finally {
            part.delete();
        }
        String names = added.stream().map(MarketEvent::getName).collect(Collectors.joining(", "));
        return MessageDto.ok("File '" + fileName + "' loaded successfully. " + added.size()
                + " event(s) added: " + names + ". You are the market maker of all of them.");
    }
}
