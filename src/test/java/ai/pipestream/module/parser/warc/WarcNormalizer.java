package ai.pipestream.module.parser.warc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class WarcNormalizer {
    private WarcNormalizer() {}

    public static byte[] normalizeGz(byte[] gzBytes) throws IOException {
        byte[] unzipped = ungzip(gzBytes);
        byte[] fixed = normalizeUncompressed(unzipped);
        return gzip(fixed);
    }

    private static byte[] normalizeUncompressed(byte[] src) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length + 1024);
        int p = 0;
        while (p < src.length) {
            // Expect header starts with WARC/1.0
            if (!startsWith(src, p, "WARC/")) {
                // try to skip CR/LF noise
                while (p < src.length && (src[p] == '\n' || src[p] == '\r')) p++;
                if (!(p < src.length && startsWith(src, p, "WARC/"))) {
                    break; // give up
                }
            }
            int hdrEnd = findHeaderEnd(src, p);
            if (hdrEnd < 0) break;
            int headerLenOriginal = hdrEnd - p;
            String header = new String(src, p, headerLenOriginal, StandardCharsets.ISO_8859_1);
            String fixedHeader = toCRLF(header);
            out.write(fixedHeader.getBytes(StandardCharsets.ISO_8859_1));
            out.write("\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // parse Content-Length
            int contentLength = parseContentLength(header);
            if (contentLength < 0) break;
            int sepLen = headerHasCRLF(header) ? 4 : 2;
            int payloadStart = hdrEnd + sepLen;
            if (payloadStart + contentLength > src.length) break;
            out.write(src, payloadStart, contentLength);
            p = payloadStart + contentLength;
            // skip any stray newlines between records
            while (p < src.length && (src[p] == '\n' || src[p] == '\r')) p++;
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] a, int off, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        if (off + b.length > a.length) return false;
        for (int i = 0; i < b.length; i++) {
            if (a[off + i] != b[i]) return false;
        }
        return true;
    }

    private static int findHeaderEnd(byte[] a, int off) {
        // look for \r\n\r\n or \n\n
        for (int i = off; i + 3 < a.length; i++) {
            if (a[i] == '\r' && a[i+1] == '\n' && a[i+2] == '\r' && a[i+3] == '\n') {
                return i; // position of first CR in CRLFCRLF
            }
        }
        for (int i = off; i + 1 < a.length; i++) {
            if (a[i] == '\n' && a[i+1] == '\n') {
                return i; // position of first LF in LFLF
            }
        }
        return -1;
    }

    private static boolean headerHasCRLF(String header) {
        return header.contains("\r\n");
    }

    private static String toCRLF(String header) {
        // normalize any sequence ending lines to CRLF
        String[] lines = header.replace("\r\n", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder(header.length() + lines.length);
        for (String line : lines) {
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private static int parseContentLength(String header) {
        String[] lines = header.replace("\r\n", "\n").split("\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                if ("content-length".equals(name)) {
                    try {
                        return Integer.parseInt(line.substring(idx+1).trim());
                    } catch (NumberFormatException ignore) { return -1; }
                }
            }
        }
        return -1;
    }

    private static byte[] ungzip(byte[] gz) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream out = new ByteArrayOutputStream(gz.length * 2)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = gis.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
             GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data);
            gos.finish();
            return bos.toByteArray();
        }
    }
}
