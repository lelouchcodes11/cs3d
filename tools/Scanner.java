import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Scans extension jars (dex converted to jvm bytecode) and records every reference to classes that
 * are not defined inside the extension itself. The output is used by ApiChecker to verify that the
 * desktop runtime provides every member extensions link against, with the exact same JVM signature.
 *
 * Usage: java -cp asm.jar;asm-tree.jar Scanner.java <outDir> <jar...>
 *
 * Output files (tab separated: count, key, sample of extensions):
 *   classes.txt  - referenced external classes
 *   members.txt  - referenced members, key format:
 *                  "S owner.name(desc)"  invokestatic
 *                  "V owner.name(desc)"  invokevirtual
 *                  "I owner.name(desc)"  invokeinterface
 *                  "P owner.name(desc)"  invokespecial (constructors / super calls)
 *                  "GS owner#name:desc" getstatic, "PS" putstatic, "GF" getfield, "PF" putfield
 *   supers.txt   - "extends X" / "implements X" for external super types
 *   overrides.txt- "externalSuper.name(desc)" methods declared by extension classes that directly
 *                  extend/implement an external type (potential overrides/callbacks)
 */
public class Scanner {
    static Map<String, Set<String>> classRefs = new TreeMap<>();
    static Map<String, Set<String>> memberRefs = new TreeMap<>();
    static Map<String, Set<String>> superRefs = new TreeMap<>();
    static Map<String, Set<String>> overrides = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        String outDir = args[0];
        for (int i = 1; i < args.length; i++) scan(new File(args[i]));
        write(outDir + "/classes.txt", classRefs);
        write(outDir + "/members.txt", memberRefs);
        write(outDir + "/supers.txt", superRefs);
        write(outDir + "/overrides.txt", overrides);
    }

    static void write(String path, Map<String, Set<String>> map) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (Map.Entry<String, Set<String>> e : map.entrySet()) {
                List<String> l = new ArrayList<>(e.getValue());
                Collections.sort(l);
                pw.println(e.getValue().size() + "\t" + e.getKey() + "\t" + String.join(",", l.subList(0, Math.min(6, l.size()))));
            }
        }
    }

    static void add(Map<String, Set<String>> m, String k, String plugin) {
        m.computeIfAbsent(k, x -> new TreeSet<>()).add(plugin);
    }

    static boolean interesting(String owner) {
        return owner != null && !owner.startsWith("java/") && !owner.startsWith("[");
    }

    static void scan(File jar) throws IOException {
        String plugin = jar.getName().replace(".jar", "");
        List<ClassNode> nodes = new ArrayList<>();
        Set<String> own = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.getName().endsWith(".class")) continue;
                try (InputStream is = zf.getInputStream(ze)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    nodes.add(cn);
                    own.add(cn.name);
                }
            }
        }
        for (ClassNode cn : nodes) {
            List<String> externalSupers = new ArrayList<>();
            if (cn.superName != null && !own.contains(cn.superName) && interesting(cn.superName)) {
                add(superRefs, "extends " + cn.superName, plugin);
                externalSupers.add(cn.superName);
            }
            for (String itf : cn.interfaces)
                if (!own.contains(itf) && interesting(itf)) {
                    add(superRefs, "implements " + itf, plugin);
                    externalSupers.add(itf);
                }
            if (!externalSupers.isEmpty()) {
                for (MethodNode mn : cn.methods) {
                    if ((mn.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) != 0) continue;
                    if (mn.name.startsWith("<")) continue;
                    for (String s : externalSupers) {
                        if (s.startsWith("kotlin/")) continue;
                        add(overrides, s + "." + mn.name + mn.desc, plugin);
                    }
                }
            }
            for (FieldNode fn : cn.fields) typeRefs(Type.getType(fn.desc), own, plugin);
            for (MethodNode mn : cn.methods) {
                for (Type t : Type.getArgumentTypes(mn.desc)) typeRefs(t, own, plugin);
                typeRefs(Type.getReturnType(mn.desc), own, plugin);
                for (AbstractInsnNode in : mn.instructions) {
                    if (in instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) in;
                        String o = m.owner.startsWith("[") ? null : m.owner;
                        if (o != null && !own.contains(o) && interesting(o)) {
                            add(classRefs, o, plugin);
                            String kind;
                            switch (m.getOpcode()) {
                                case Opcodes.INVOKESTATIC: kind = "S"; break;
                                case Opcodes.INVOKEINTERFACE: kind = "I"; break;
                                case Opcodes.INVOKESPECIAL: kind = "P"; break;
                                default: kind = "V";
                            }
                            add(memberRefs, kind + " " + o + "." + m.name + m.desc, plugin);
                        }
                        for (Type t : Type.getArgumentTypes(m.desc)) typeRefs(t, own, plugin);
                        typeRefs(Type.getReturnType(m.desc), own, plugin);
                    } else if (in instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) in;
                        if (!own.contains(f.owner) && interesting(f.owner)) {
                            add(classRefs, f.owner, plugin);
                            String kind;
                            switch (f.getOpcode()) {
                                case Opcodes.GETSTATIC: kind = "GS"; break;
                                case Opcodes.PUTSTATIC: kind = "PS"; break;
                                case Opcodes.GETFIELD: kind = "GF"; break;
                                default: kind = "PF";
                            }
                            add(memberRefs, kind + " " + f.owner + "#" + f.name + ":" + f.desc, plugin);
                        }
                        typeRefs(Type.getType(f.desc), own, plugin);
                    } else if (in instanceof TypeInsnNode) {
                        TypeInsnNode t = (TypeInsnNode) in;
                        String d = t.desc;
                        if (d.startsWith("[")) typeRefs(Type.getType(d), own, plugin);
                        else if (!own.contains(d) && interesting(d)) add(classRefs, d, plugin);
                    } else if (in instanceof LdcInsnNode) {
                        Object c = ((LdcInsnNode) in).cst;
                        if (c instanceof Type) typeRefs((Type) c, own, plugin);
                    }
                }
            }
        }
    }

    static void typeRefs(Type t, Set<String> own, String plugin) {
        if (t.getSort() == Type.ARRAY) t = t.getElementType();
        if (t.getSort() == Type.OBJECT) {
            String n = t.getInternalName();
            if (!own.contains(n) && interesting(n)) add(classRefs, n, plugin);
        }
    }
}
