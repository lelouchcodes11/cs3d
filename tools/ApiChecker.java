import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Verifies that a runtime classpath provides every member referenced by extensions
 * (see Scanner.java) with the exact JVM signature and invocation kind.
 *
 * Usage: java -cp asm.jar;asm-tree.jar ApiChecker.java <extension-api dir> <ownerRegex> <classpath entry>...
 *   classpath entries may be jars or class directories.
 *
 * Exit code is the number of problems (capped at 255).
 */
public class ApiChecker {
    static final Map<String, ClassNode> classes = new HashMap<>();
    static final List<File> cp = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File apiDir = new File(args[0]);
        Pattern ownerFilter = Pattern.compile(args[1]);
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("@")) { for (String l : Files.readAllLines(new File(args[i].substring(1)).toPath())) if (!l.isBlank()) cp.add(new File(l.trim())); }
            else cp.add(new File(args[i]));
        }
        index();

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String line : Files.readAllLines(new File(apiDir, "members.txt").toPath())) {
            String[] parts = line.split("\t");
            String key = parts[1];
            String users = parts.length > 2 ? parts[2] : "";
            int sp = key.indexOf(' ');
            String kind = key.substring(0, sp);
            String member = key.substring(sp + 1);
            String owner;
            if (member.contains("#")) owner = member.substring(0, member.indexOf('#'));
            else owner = member.substring(0, member.indexOf('.'));
            if (!ownerFilter.matcher(owner).matches()) continue;
            checked++;
            String err = check(kind, owner, member);
            if (err != null) problems.add(parts[0] + "\t" + key + "\t" + err + "\t[" + users + "]");
        }
        // Super types must exist, be non-final and (for classes) have the expected kind
        for (String line : Files.readAllLines(new File(apiDir, "supers.txt").toPath())) {
            String[] parts = line.split("\t");
            String[] kv = parts[1].split(" ");
            if (!ownerFilter.matcher(kv[1]).matches()) continue;
            checked++;
            ClassNode cn = load(kv[1]);
            if (cn == null) { problems.add(parts[0] + "\t" + parts[1] + "\tMISSING CLASS"); continue; }
            boolean isItf = (cn.access & Opcodes.ACC_INTERFACE) != 0;
            if (kv[0].equals("extends") && isItf) problems.add(parts[0] + "\t" + parts[1] + "\tIS INTERFACE");
            if (kv[0].equals("implements") && !isItf) problems.add(parts[0] + "\t" + parts[1] + "\tNOT INTERFACE");
            if ((cn.access & Opcodes.ACC_FINAL) != 0) problems.add(parts[0] + "\t" + parts[1] + "\tFINAL");
        }
        // Overridden callbacks: a method declared in an extension class that extends a runtime class.
        // Report declared methods which do not exist in the runtime super type hierarchy, as the
        // runtime would never call them (only informative for helper methods, critical for callbacks).
        File ov = new File(apiDir, "overrides.txt");
        List<String> notOverriding = new ArrayList<>();
        if (ov.exists()) {
            for (String line : Files.readAllLines(ov.toPath())) {
                String[] parts = line.split("\t");
                String m = parts[1];
                String owner = m.substring(0, m.indexOf('.'));
                if (!ownerFilter.matcher(owner).matches()) continue;
                String nd = m.substring(owner.length() + 1);
                String name = nd.substring(0, nd.indexOf('('));
                String desc = nd.substring(nd.indexOf('('));
                if (load(owner) == null) continue;
                if (findMethod(owner, name, desc, new HashSet<>()) == null) {
                    // Only report names that exist with some descriptor or look like callbacks
                    boolean sameName = hasMethodNamed(owner, name, new HashSet<>());
                    if (sameName || name.matches("^(on|should|handle|dispatch|get|set|is)[A-Z].*"))
                        notOverriding.add(parts[0] + "\t" + m + (sameName ? "\tDESCRIPTOR MISMATCH" : "\tNOT IN RUNTIME") + "\t[" + (parts.length > 2 ? parts[2] : "") + "]");
                }
            }
        }
        System.out.println("Checked " + checked + " references, " + problems.size() + " problems");
        for (String p : problems) System.out.println("  " + p);
        if (!notOverriding.isEmpty()) {
            System.out.println("Extension methods that do not override anything in the runtime (review callbacks):");
            for (String p : notOverriding) System.out.println("  " + p);
        }
        System.exit(Math.min(255, problems.size()));
    }

    static String check(String kind, String owner, String member) {
        ClassNode cn = load(owner);
        if (cn == null) return "MISSING CLASS";
        boolean isItf = (cn.access & Opcodes.ACC_INTERFACE) != 0;
        if (member.contains("#")) {
            String nd = member.substring(member.indexOf('#') + 1);
            String name = nd.substring(0, nd.indexOf(':'));
            String desc = nd.substring(nd.indexOf(':') + 1);
            FieldNode fn = findField(owner, name, desc, new HashSet<>());
            if (fn == null) return "MISSING FIELD";
            boolean isStatic = (fn.access & Opcodes.ACC_STATIC) != 0;
            boolean wantStatic = kind.endsWith("S");
            if (isStatic != wantStatic) return "STATIC MISMATCH (runtime static=" + isStatic + ")";
            if (kind.startsWith("P") && (fn.access & Opcodes.ACC_FINAL) != 0 && !kind.equals("PS")) return "FINAL FIELD WRITTEN";
            return null;
        } else {
            String nd = member.substring(owner.length() + 1);
            String name = nd.substring(0, nd.indexOf('('));
            String desc = nd.substring(nd.indexOf('('));
            if (kind.equals("I") && !isItf) return "INVOKEINTERFACE ON CLASS";
            if (kind.equals("V") && isItf) return "INVOKEVIRTUAL ON INTERFACE";
            MethodNode mn;
            if (name.equals("<init>")) {
                mn = null;
                for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) mn = m;
                if (mn == null) return "MISSING CONSTRUCTOR";
                if ((mn.access & Opcodes.ACC_PRIVATE) != 0) return "PRIVATE CONSTRUCTOR";
                return null;
            }
            mn = findMethod(owner, name, desc, new HashSet<>());
            if (mn == null) return "MISSING METHOD" + (hasMethodNamed(owner, name, new HashSet<>()) ? " (name exists, descriptor differs)" : "");
            boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
            if (kind.equals("S") != isStatic) return "STATIC MISMATCH (runtime static=" + isStatic + ")";
            if ((mn.access & Opcodes.ACC_PRIVATE) != 0) return "PRIVATE METHOD";
            return null;
        }
    }

    static boolean hasMethodNamed(String owner, String name, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        ClassNode cn = load(owner);
        if (cn == null) return false;
        for (MethodNode m : cn.methods) if (m.name.equals(name)) return true;
        if (hasMethodNamed(cn.superName, name, seen)) return true;
        for (String i : cn.interfaces) if (hasMethodNamed(i, name, seen)) return true;
        return false;
    }

    static MethodNode findMethod(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        ClassNode cn = load(owner);
        if (cn == null) return null;
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        MethodNode r = findMethod(cn.superName, name, desc, seen);
        if (r != null) return r;
        for (String i : cn.interfaces) {
            r = findMethod(i, name, desc, seen);
            if (r != null) return r;
        }
        return null;
    }

    static FieldNode findField(String owner, String name, String desc, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        ClassNode cn = load(owner);
        if (cn == null) return null;
        for (FieldNode f : cn.fields) if (f.name.equals(name) && f.desc.equals(desc)) return f;
        for (String i : cn.interfaces) {
            FieldNode r = findField(i, name, desc, seen);
            if (r != null) return r;
        }
        return findField(cn.superName, name, desc, seen);
    }

    static final Map<String, byte[]> bytes = new HashMap<>();

    static void index() throws IOException {
        for (File f : cp) {
            if (f.isDirectory()) {
                Path root = f.toPath();
                try (var s = Files.walk(root)) {
                    s.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        String n = root.relativize(p).toString().replace('\\', '/');
                        n = n.substring(0, n.length() - 6);
                        try { bytes.putIfAbsent(n, Files.readAllBytes(p)); } catch (IOException e) { throw new UncheckedIOException(e); }
                    });
                }
            } else if (f.getName().endsWith(".jar") && f.exists()) {
                try (ZipFile zf = new ZipFile(f)) {
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry ze = en.nextElement();
                        String n = ze.getName();
                        if (!n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                        n = n.substring(0, n.length() - 6);
                        if (bytes.containsKey(n)) continue;
                        try (InputStream is = zf.getInputStream(ze)) { bytes.put(n, is.readAllBytes()); }
                    }
                }
            }
        }
    }

    static ClassNode load(String name) {
        if (name == null) return null;
        if (classes.containsKey(name)) return classes.get(name);
        byte[] b = bytes.get(name);
        ClassNode cn = null;
        if (b != null) {
            cn = new ClassNode();
            new ClassReader(b).accept(cn, ClassReader.SKIP_CODE);
        } else if (name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/")) {
            try (InputStream is = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                if (is != null) {
                    cn = new ClassNode();
                    new ClassReader(is).accept(cn, ClassReader.SKIP_CODE);
                }
            } catch (IOException ignored) {
            }
        }
        classes.put(name, cn);
        return cn;
    }
}
