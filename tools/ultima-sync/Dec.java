import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

public class Dec {
    public static void main(String[] a) throws Exception {
        String s = Files.readString(Paths.get(a[0]));
        int i = s.indexOf("\"data\":\"gz:");
        if (i < 0) { System.out.println(s); return; }
        int st = i + 11;
        int en = s.indexOf('"', st);
        String b64 = s.substring(st, en).replace("\\/", "/");
        byte[] gz = Base64.getDecoder().decode(b64);
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            System.out.println(new String(in.readAllBytes(), "UTF-8"));
        }
        System.out.println("-- rest: " + s.substring(0, i) + " ... " + s.substring(en));
    }
}
