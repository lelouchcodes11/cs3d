import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Plays "the Android app" against the mock Firebase: pushes a newer resume_watching (one existing title further on, one new
 * title) and a bookmarks category, with matching manifest entries. Usage: java AndroidSim.java [base] [key]
 */
public class AndroidSim {
    static HttpClient http = HttpClient.newHttpClient();
    static String base, key;

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
        if (!m.find()) throw new RuntimeException("no data in " + payload.substring(0, Math.min(80, payload.length())));
        byte[] gz = Base64.getDecoder().decode(m.group(1).replace("\\/", "/"));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    static String pack(String json, long ts, String device) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(json.getBytes(StandardCharsets.UTF_8)); }
        return "{\"data\":\"gz:" + Base64.getEncoder().encodeToString(bo.toByteArray()) + "\",\"ts\":" + ts + ",\"device\":\"" + device + "\"}";
    }

    static String q(String s) { return "\\\"" + s + "\\\""; } // a JSON string inside a JSON string

    public static void main(String[] a) throws Exception {
        base = a.length > 0 ? a[0] : "http://127.0.0.1:9099/";
        key = a.length > 1 ? a[1] : "desktop-test-key-001";
        String device = "Pixel 8 (sim)";
        long now = System.currentTimeMillis() + 5000;

        // ---- resume watching: Maharshi (-1700009250 / episode -1700009248) further on, and a new title
        String rw = unpack(get("categories/resume_watching"));
        rw = rw.replaceAll("(\"0/video_pos_dur/-1700009248\":\"\\{\\\\\"position\\\\\":)\\d+", "$1" + "9000000");
        rw = rw.replaceAll("(\"0/result_resume_watching_2/-1700009250\":\"\\{[^}]*\\\\\"updateTime\\\\\":)\\d+", "$1" + now);
        String added = "\"0/result_resume_watching_2/424242\":\"{" + q("parentId") + ":424242," + q("episodeId") + ":424243," + q("episode") + ":3," + q("season") + ":1," + q("updateTime") + ":" + now + "," + q("isFromDownload") + ":false}\","
            + "\"0/video_pos_dur/424243\":\"{" + q("position") + ":600000," + q("duration") + ":1500000}\","
            + "\"0/result_episode/424242\":\"3\",\"0/result_season/424242\":\"1\",\"0/result_dub/424242\":\"0\","
            + "\"download_header_cache/424242\":\"{" + q("apiName") + ":" + q("Re:ANIME") + "," + q("url") + ":" + q("https://reanime.to/anime/code-geass-lelouch-of-the-rebellion-r2-atxvgh") + "," + q("type") + ":" + q("Anime") + "," + q("name") + ":" + q("Phone Sim Show") + "," + q("poster") + ":" + q("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx2904-Fet9Q33suC7G.jpg") + "," + q("cacheTime") + ":" + now + "," + q("id") + ":424242}\",";
        int at = rw.indexOf("\"_String\":{");
        if (at < 0) throw new RuntimeException("no _String");
        at += "\"_String\":{".length();
        rw = rw.substring(0, at) + added + rw.substring(at);

        // ---- bookmarks: the new title as "Watching"
        String bm = "{\"datastore\":{\"_Bool\":{},\"_Float\":{},\"_Int\":{},\"_Long\":{},\"_String\":{"
            + "\"0/result_watch_state/424242\":\"0\","
            + "\"0/result_watch_state_data/424242\":\"{" + q("bookmarkedTime") + ":" + now + "," + q("id") + ":424242," + q("latestUpdatedTime") + ":" + now + "," + q("name") + ":" + q("Phone Sim Show") + "," + q("url") + ":" + q("https://reanime.to/anime/code-geass-lelouch-of-the-rebellion-r2-atxvgh") + "," + q("apiName") + ":" + q("Re:ANIME") + "," + q("type") + ":" + q("Anime") + "," + q("posterUrl") + ":" + q("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx2904-Fet9Q33suC7G.jpg") + "}\""
            + "},\"_StringSet\":{}},\"settings\":{\"_Bool\":{},\"_Float\":{},\"_Int\":{},\"_Long\":{},\"_String\":{},\"_StringSet\":{}}}";

        put("categories/resume_watching", pack(rw, now, device));
        put("categories/bookmarks", pack(bm, now, device));

        String manifest = get("manifest");
        String entryRw = "\"resume_watching\":{\"ts\":" + now + ",\"hash\":\"" + md5(rw) + "\",\"device\":\"" + device + "\"}";
        String entryBm = "\"bookmarks\":{\"ts\":" + now + ",\"hash\":\"" + md5(bm) + "\",\"device\":\"" + device + "\"}";
        manifest = manifest.replaceAll("\"resume_watching\":(\\{[^}]*\\}|null)", Matcher.quoteReplacement(entryRw));
        manifest = manifest.replaceAll("\"bookmarks\":(\\{[^}]*\\}|null)", Matcher.quoteReplacement(entryBm));
        put("manifest", manifest);
        System.out.println("manifest now: " + manifest);
    }
}
