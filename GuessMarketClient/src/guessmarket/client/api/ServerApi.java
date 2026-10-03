package guessmarket.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import guessmarket.dto.Api;
import guessmarket.dto.MessageDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The client's only gateway to the server. Every call is asynchronous (java.net.http.HttpClient) and the session
 * cookie (JSESSIONID) is kept by the cookie manager, so the server always knows who is calling.
 */
public final class ServerApi {
    public static final String DEFAULT_SERVER = "http://localhost:8080" + Api.CONTEXT_PATH;
    private static final Gson GSON = new Gson();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final HttpClient http;

    public ServerApi(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public static Gson gson() {
        return GSON;
    }

    /** GET that returns the raw JSON body, so pollers can skip UI updates when nothing changed. */
    public CompletableFuture<String> getJson(String path, Map<String, ?> query) {
        String url = baseUrl + path + (query == null || query.isEmpty() ? "" : "?" + encode(query));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
        return send(request);
    }

    public <T> T parse(String json, Type type) {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException ex) {
            throw new ServerException(0, "The server sent an unreadable reply: " + ex.getMessage());
        }
    }

    /** POST of form fields. Completes with the server's success message. */
    public CompletableFuture<String> post(String path, Map<String, ?> form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form == null ? "" : encode(form), StandardCharsets.UTF_8))
                .build();
        return send(request).thenApply(this::message);
    }

    /** Uploads a file as multipart/form-data (part name "file"), like an HTML form with an input of type file. */
    public CompletableFuture<String> upload(Path file) {
        String boundary = "----GuessMarket" + UUID.randomUUID().toString().replace("-", "");
        byte[] body;
        try {
            body = multipartBody(boundary, file);
        } catch (IOException ex) {
            return CompletableFuture.failedFuture(new ServerException(0, "Cannot read the file: " + ex.getMessage()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + Api.UPLOAD))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request).thenApply(this::message);
    }

    private CompletableFuture<String> send(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        throw ServerException.from(error, baseUrl);
                    }
                    if (response.statusCode() >= 400) {
                        throw new ServerException(response.statusCode(), errorText(response));
                    }
                    return response.body();
                });
    }

    private String errorText(HttpResponse<String> response) {
        try {
            MessageDto dto = GSON.fromJson(response.body(), MessageDto.class);
            if (dto != null && dto.error() != null) {
                return dto.error();
            }
        } catch (JsonSyntaxException ignored) {
            // Not our JSON (for example a Tomcat error page); fall through to the generic text.
        }
        if (response.statusCode() == 404) {
            return "The server does not know " + response.uri().getPath()
                    + ". Is GuessMarket.war deployed under the name GuessMarket?";
        }
        return "Server error " + response.statusCode() + ".";
    }

    private String message(String json) {
        MessageDto dto = parse(json, MessageDto.class);
        return dto == null || dto.message() == null ? "" : dto.message();
    }

    private static byte[] multipartBody(String boundary, Path file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String fileName = file.getFileName().toString().replace("\"", "");
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + Api.PART_FILE + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/xml\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(Files.readAllBytes(file));
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String encode(Map<String, ?> values) {
        StringJoiner joiner = new StringJoiner("&");
        values.forEach((key, value) -> joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
        return joiner.toString();
    }
}
