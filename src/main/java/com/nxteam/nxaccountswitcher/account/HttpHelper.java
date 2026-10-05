package com.nxteam.nxaccountswitcher.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class HttpHelper {
    private static final int TIMEOUT_MILLIS = 15000;

    private HttpHelper() {
    }

    static final class Response {
        private final int status;
        private final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        int getStatus() {
            return status;
        }

        String getBody() {
            return body;
        }

        boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        JsonObject getJson() {
            try {
                JsonElement element = new JsonParser().parse(body);
                if (element != null && element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            } catch (RuntimeException ignored) {
            }
            return new JsonObject();
        }
    }

    static Response postForm(String url, Map<String, String> parameters) throws AuthenticationException {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return request("POST", url, "application/x-www-form-urlencoded", builder.toString(), null);
    }

    static Response postJson(String url, JsonObject body) throws AuthenticationException {
        return request("POST", url, "application/json", body.toString(), null);
    }

    static Response get(String url, String bearerToken) throws AuthenticationException {
        return request("GET", url, null, null, bearerToken);
    }

    private static Response request(String method, String url, String contentType, String body, String bearerToken) throws AuthenticationException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "NX-Account-Switcher/1.0");
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, readFully(stream));
        } catch (IOException exception) {
            throw new AuthenticationException("Network error: " + exception.getMessage(), exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
