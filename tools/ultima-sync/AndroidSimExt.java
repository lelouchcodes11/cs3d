import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Plays the Android app for the extensions category: Android style file paths, plus one extension this PC does not have. */
public class AndroidSimExt {
    static HttpClient http = HttpClient.newHttpClient();
    static String base = "http://127.0.0.1:9099/", key = "desktop-test-key-001";

    static String get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "sync/" + key + "/" + path + ".json")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static void put(String path, String body) throws Exception {
        var r = http.send(HttpRequest.newBuilder(URI.create(base + "sync/" + key + "/" + path + ".json")).PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("PUT " + path + " -> " + r.statusCode());
    }

    static String md5(String s) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String unpack(String payload) throws Exception {
        Matcher m = Pattern.compile("\"data\":\"gz:([^\"]*)\"").matcher(payload);
        if (!m.find()) throw new RuntimeException("no data");
        byte[] gz = Base64.getDecoder().decode(m.group(1).replace("\\/", "/"));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    static String pack(String json, long ts, String device) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(json.getBytes(StandardCharsets.UTF_8)); }
        return "{\"data\":\"gz:" + Base64.getEncoder().encodeToString(bo.toByteArray()) + "\",\"ts\":" + ts + ",\"device\":\"" + device + "\"}";
    }

    public static void main(String[] a) throws Exception {
        String device = "Pixel 8 (sim)";
        long now = System.currentTimeMillis() + 5000;
        String ex = unpack(get("categories/extensions"));
        // Android style paths
        ex = Pattern.compile("(\\\\\"filePath\\\\\":\\\\\")(.*?)(\\\\\",\\\\\"version)", Pattern.DOTALL).matcher(ex)
            .replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "/data/user/0/com.lagradost.cloudstream3/files/Extensions/repo/plugin.cs3" + m.group(3)));
        // one more extension
        String extra = "{\\\"internalName\\\":\\\"StreamPlay\\\",\\\"url\\\":\\\"https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/StreamPlay.cs3\\\",\\\"isOnline\\\":true,\\\"filePath\\\":\\\"/data/user/0/com.lagradost.cloudstream3/files/Extensions/repo/StreamPlay.cs3\\\",\\\"version\\\":684},";
        String marker = "{\\\"internalName\\\":\\\"Ultima\\\"";
        int count = 0;
        int at = 0;
        while ((at = ex.indexOf(marker, at)) >= 0) { ex = ex.substring(0, at) + extra + ex.substring(at); at += extra.length() + marker.length(); count++; }
        System.out.println("inserted StreamPlay " + count + "x");
        put("categories/extensions", pack(ex, now, device));
        String manifest = get("manifest");
        String entry = "\"extensions\":{\"ts\":" + now + ",\"hash\":\"" + md5(ex) + "\",\"device\":\"" + device + "\"}";
        manifest = manifest.replaceAll("\"extensions\":(\\{[^}]*\\}|null)", Matcher.quoteReplacement(entry));
        put("manifest", manifest);
    }
}
