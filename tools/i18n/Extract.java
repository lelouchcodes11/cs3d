import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * java Extract.java <desktop source dir> <out file>
 * Lists the string literals of the desktop UI code that look like text a person reads: key (interpolations turned into {0}, {1} ...), file:line.
 */
public class Extract {
    record Lit(String key, String where, boolean template) {}

    static List<Lit> out = new ArrayList<>();
    static String file;
    static String src;
    static int pos, line;

    public static void main(String[] a) throws Exception {
        Path root = Path.of(a[0]);
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".kt")).filter(p -> !p.toString().replace('\\', '/').contains("/tools/")).sorted().collect(Collectors.toList());
        }
        for (Path p : files) {
            file = root.relativize(p).toString().replace('\\', '/');
            src = Files.readString(p);
            pos = 0; line = 1;
            scan(false);
        }
        Map<String, List<String>> by = new LinkedHashMap<>();
        Map<String, Boolean> tpl = new HashMap<>();
        for (Lit l : out) {
            if (!looksLikeText(l.key)) continue;
            by.computeIfAbsent(l.key, k -> new ArrayList<>()).add(l.where);
            tpl.put(l.key, l.template);
        }
        StringBuilder sb = new StringBuilder();
        for (var e : by.entrySet()) {
            List<String> w = e.getValue();
            sb.append(e.getKey().replace("\n", "\\n")).append('\t').append(w.get(0)).append(w.size() > 1 ? " +" + (w.size() - 1) : "").append('\n');
        }
        Files.writeString(Path.of(a[1]), sb.toString());
        System.out.println(by.size() + " keys, " + tpl.values().stream().filter(b -> b).count() + " with placeholders");
    }

    static boolean looksLikeText(String k) {
        String bare = k.replaceAll("\\{\\d+}", "").trim();
        if (bare.chars().filter(Character::isLetter).count() < 2) return false;
        if (k.contains("://") || k.contains("\\") || k.startsWith("/") || k.contains("::") || k.contains("=>") || k.contains("->")) return false;
        if (k.matches(".*\\.(png|jpg|svg|json|xml|txt|exe|dll|jar|kt|html|css|js|m3u8|mp4|mkv|srt|ass|vtt)\\b.*") && !k.contains(" ")) return false;
        boolean space = k.contains(" ");
        if (!space) {
            // one word: a label when it starts with a capital and has only letters (and maybe an apostrophe / dash)
            return k.matches("[\\p{Lu}][\\p{L}'’\\-]{2,}[….:!?]?") || k.matches("\\{\\d+}\\s*[\\p{L}]{2,}");
        }
        // several words: not code
        if (k.matches(".*[;]\\s*$") || k.contains(" = ") || k.contains("&&") || k.contains("||") || k.contains("==")) return false;
        if (k.startsWith("-") || k.startsWith("--")) return false;
        if (k.matches("^[a-z]+(_[a-z0-9]+)+.*")) return false;
        return true;
    }

    static char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }

    /** scans code; when inTemplate it stops at the matching '}' */
    static void scan(boolean inTemplate) {
        int depth = 0;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') { line++; pos++; continue; }
            if (inTemplate) {
                if (c == '{') depth++;
                else if (c == '}') { if (depth == 0) return; depth--; }
            }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') { while (pos < src.length() && src.charAt(pos) != '\n') pos++; continue; }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) { if (src.charAt(pos) == '\n') line++; pos++; }
                pos += 2; continue;
            }
            if (c == '\'') { // char literal
                pos++;
                if (peek() == '\\') pos += 2; else pos++;
                while (pos < src.length() && src.charAt(pos) != '\'') pos++;
                pos++; continue;
            }
            if (c == '"') { string(); continue; }
            pos++;
        }
    }

    static void string() {
        int startLine = line;
        boolean raw = src.startsWith("\"\"\"", pos);
        pos += raw ? 3 : 1;
        StringBuilder key = new StringBuilder();
        int n = 0;
        boolean template = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (raw) {
                if (src.startsWith("\"\"\"", pos)) { pos += 3; break; }
            } else if (c == '"') { pos++; break; }
            if (c == '\n') { line++; }
            if (!raw && c == '\\') {
                char d = pos + 1 < src.length() ? src.charAt(pos + 1) : ' ';
                switch (d) {
                    case 'n' -> key.append('\n');
                    case 't' -> key.append('\t');
                    case '"' -> key.append('"');
                    case '\\' -> key.append('\\');
                    case '$' -> key.append('$');
                    case '\'' -> key.append('\'');
                    case 'u' -> {
                        try { key.append((char) Integer.parseInt(src.substring(pos + 2, pos + 6), 16)); } catch (Exception ex) { key.append('?'); }
                        pos += 4;
                    }
                    default -> key.append(d);
                }
                pos += 2; continue;
            }
            if (c == '$' && pos + 1 < src.length() && src.charAt(pos + 1) == '{') {
                pos += 2; template = true;
                scan(true);   // stops at the closing brace
                pos++;
                key.append('{').append(n++).append('}');
                continue;
            }
            if (c == '$' && pos + 1 < src.length() && Character.isJavaIdentifierStart(src.charAt(pos + 1))) {
                pos++;
                while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) pos++;
                template = true;
                key.append('{').append(n++).append('}');
                continue;
            }
            key.append(c); pos++;
        }
        out.add(new Lit(key.toString(), file + ":" + startLine, template));
    }
}
