import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * A tiny stand-in for Firebase Realtime Database REST (GET / PUT / DELETE on <path>.json), used to test Ultima's sync
 * without a real database. Values are stored as raw JSON text per path; a parent path is composed from its children.
 * Usage: java MockFirebase.java <port> <dumpDir>
 */
public class MockFirebase {
    static final ConcurrentSkipListMap<String, String> store = new ConcurrentSkipListMap<>();
    static Path dump;

    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]);
        dump = Paths.get(a[1]);
        Files.createDirectories(dump);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", MockFirebase::handle);
        server.start();
        System.out.println("mock firebase on " + port);
    }

    static final java.util.List<Object[]> streams = new java.util.concurrent.CopyOnWriteArrayList<>();

    static void event(OutputStream os, String path, String json) throws Exception {
        String data = "{\"path\":\"/\",\"data\":" + json + "}";
        os.write(("event: put\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    static void broadcast() {
        for (Object[] st : streams) {
            try { event((OutputStream) st[1], (String) st[0], compose((String) st[0])); } catch (Exception e) { streams.remove(st); }
        }
    }

    static void handle(HttpExchange ex) {
        boolean keepOpen = false;
        try {
            String q = ex.getRequestURI().getQuery();
            if ("GET".equals(ex.getRequestMethod()) && q != null && q.contains("alt=sse")) {
                String p0 = ex.getRequestURI().getPath();
                p0 = p0.startsWith("/") ? p0.substring(1) : p0;
                if (p0.endsWith(".json")) p0 = p0.substring(0, p0.length() - 5);
                ex.getResponseHeaders().add("Content-Type", "text/event-stream");
                ex.sendResponseHeaders(200, 0);
                OutputStream os = ex.getResponseBody();
                synchronized (store) { event(os, p0, compose(p0)); }
                streams.add(new Object[]{p0, os});
                System.out.println("SSE open /" + p0);
                keepOpen = true;
                return;
            }
            String method = ex.getRequestMethod();
            String raw = ex.getRequestURI().getPath();
            String path = raw.startsWith("/") ? raw.substring(1) : raw;
            if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String reply;
            int status = 200;
            synchronized (store) {
                switch (method) {
                    case "PUT":
                        removeUnder(path);
                        if (!body.trim().equals("null")) store.put(path, body.trim());
                        reply = body;
                        save(path, body);
                        break;
                    case "DELETE":
                        removeUnder(path);
                        store.remove(path);
                        reply = "null";
                        break;
                    case "GET":
                        reply = compose(path);
                        break;
                    default:
                        status = 405;
                        reply = "{\"error\":\"unsupported\"}";
                }
            }
            if (!method.equals("GET")) broadcast();
            System.out.println(method + " /" + path + " -> " + status + " (" + (method.equals("GET") ? reply.length() : body.length()) + " bytes)");
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Connection", "close");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (Throwable t) {
            t.printStackTrace();
            try { ex.sendResponseHeaders(500, -1); } catch (Exception ignored) { }
        } finally {
            if (!keepOpen) ex.close();
        }
    }

    static void removeUnder(String path) {
        String prefix = path.isEmpty() ? "" : path + "/";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    static void save(String path, String body) {
        try { Files.writeString(dump.resolve(path.replace('/', '_') + ".json"), body, StandardCharsets.UTF_8); } catch (Exception ignored) { }
    }

    /** The JSON at [path]: the stored text, or an object built from the children below it, or null */
    static String compose(String path) {
        String exact = store.get(path);
        if (exact != null) return exact;
        String prefix = path.isEmpty() ? "" : path + "/";
        TreeMap<String, String> children = new TreeMap<>();
        for (Map.Entry<String, String> e : store.tailMap(prefix).entrySet()) {
            if (!e.getKey().startsWith(prefix)) break;
            String rest = e.getKey().substring(prefix.length());
            int slash = rest.indexOf('/');
            String child = slash < 0 ? rest : rest.substring(0, slash);
            children.putIfAbsent(child, null);
        }
        if (children.isEmpty()) return "null";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String child : children.keySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(child.replace("\"", "\\\"")).append("\":").append(compose(prefix + child));
        }
        return sb.append('}').toString();
    }
}
