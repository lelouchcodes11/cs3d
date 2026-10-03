import org.objectweb.asm.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Lists the string constants of extension jars that name a crypto algorithm or transformation, because those are looked up by
 * name at run time ("AES/CBC/PKCS7Padding") and a JVM may not have what Android has. The output feeds `LinkLab -Dlinklab.crypto`.
 *
 * Usage: java -cp asm.jar StringScan.java <outFile> <jar...>
 * Output lines: kind TAB value TAB extensions
 */
public class StringScan {
    static final Pattern TRANSFORM = Pattern.compile("^[A-Za-z0-9_]+/[A-Za-z0-9_]+/[A-Za-z0-9_]+$");
    static final Pattern NAME = Pattern.compile("^(AES|DES|DESede|RSA|Blowfish|ChaCha20|RC4|ARC4|RC2|EC|DSA|DH|HmacMD5|HmacSHA[0-9]+|HmacSHA3-[0-9]+|PBKDF2With[A-Za-z0-9]+|PBEWith[A-Za-z0-9]+|MD5|MD2|SHA|SHA-1|SHA-224|SHA-256|SHA-384|SHA-512|SHA3-[0-9]+|AES_[0-9]+|SHA[0-9]+withRSA|SHA[0-9]+withECDSA|TLS|TLSv1\\.[0-9]|SSL|SSLv3|X\\.509|PKCS12|BKS|JKS|SecureRandom|SHA1PRNG|NativePRNG)$");
    static final Map<String, Set<String>> found = new TreeMap<>();

    public static void main(String[] a) throws Exception {
        for (int i = 1; i < a.length; i++) {
            File jar = new File(a[i]);
            String id = jar.getName().substring(0, Math.min(10, jar.getName().length()));
            try (ZipFile z = new ZipFile(jar)) {
                for (ZipEntry e : Collections.list(z.entries())) {
                    if (!e.getName().endsWith(".class")) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override public void visitLdcInsn(Object v) {
                                        if (v instanceof String s && s.length() < 60) {
                                            if (TRANSFORM.matcher(s).matches()) add("transform", s, id);
                                            else if (NAME.matcher(s).matches()) add("name", s, id);
                                        }
                                    }
                                };
                            }
                        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    } catch (Exception ignored) { }
                }
            }
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(a[0]))) {
            for (var e : found.entrySet()) pw.println(e.getKey() + "\t" + String.join(",", e.getValue()));
        }
        System.out.println(found.size() + " algorithm names");
    }

    static void add(String kind, String s, String id) {
        found.computeIfAbsent(kind + "\t" + s, k -> new TreeSet<>()).add(id);
    }
}
