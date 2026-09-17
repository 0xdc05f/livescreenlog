package com.livescreenlog.app.web;

import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class GzipRequestBodyReader {

    private GzipRequestBodyReader() {
    }

    public static String readUtf8(HttpServletRequest request, int maxUncompressedBytes) throws IOException {
        InputStream raw = new CappedInputStream(request.getInputStream(), maxUncompressedBytes);
        String encoding = request.getHeader("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            try (InputStream gis = new GZIPInputStream(raw)) {
                return readStreamToString(gis, maxUncompressedBytes);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid gzip payload");
            }
        }
        return readStreamToString(raw, maxUncompressedBytes);
    }

    private static String readStreamToString(InputStream in, int maxUncompressedBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (maxUncompressedBytes > 0 && total > maxUncompressedBytes) {
                throw new IllegalArgumentException(
                        "Events payload exceeds max size of " + maxUncompressedBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static final class CappedInputStream extends FilterInputStream {
        private final int maxBytes;
        private long total;

        private CappedInputStream(InputStream in, int maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            if (skipped > 0) {
                count(skipped);
            }
            return skipped;
        }

        private void count(long n) {
            total += n;
            if (maxBytes > 0 && total > maxBytes) {
                throw new IllegalArgumentException("Events payload exceeds max size of " + maxBytes + " bytes");
            }
        }
    }
}
