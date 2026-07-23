package com.livescreenlog.app.web;

import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class GzipRequestBodyReader {

    private GzipRequestBodyReader() {
    }

    public static String readUtf8(HttpServletRequest request, int maxUncompressedBytes) throws IOException {
        byte[] raw = request.getInputStream().readAllBytes();
        if (raw.length == 0) {
            return "";
        }

        String encoding = request.getHeader("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            return gunzipToString(raw, maxUncompressedBytes);
        }
        if (maxUncompressedBytes > 0 && raw.length > maxUncompressedBytes) {
            throw new IllegalArgumentException("Events payload exceeds max size of " + maxUncompressedBytes + " bytes");
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static String gunzipToString(byte[] compressed, int maxUncompressedBytes) throws IOException {
        try (InputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(compressed.length * 4, 65_536))) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = gis.read(buf)) != -1) {
                total += n;
                if (maxUncompressedBytes > 0 && total > maxUncompressedBytes) {
                    throw new IllegalArgumentException(
                            "Events payload exceeds max size of " + maxUncompressedBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid gzip payload");
        }
    }
}
