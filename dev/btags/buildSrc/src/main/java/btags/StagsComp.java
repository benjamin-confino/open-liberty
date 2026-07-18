package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of gen_stags.sh — parse one ctags partial file, extract structural
 * kinds (class, enum, interface, struct, typedef, macro, union, field),
 * and write per-letter TSV files under {@code <stagsDir>/parts/stags_<comp>/}.
 */
public final class StagsComp {

    private StagsComp() {}

    private static final Set<String> SKIP_KINDS = new HashSet<>(Arrays.asList(
        "method", "function", "m", "f", "package"
    ));

    /**
     * @param projectRoot project root (for making paths relative)
     * @param partFile    one ctags/parts/java_*.tags file
     * @param stagsDir    stags output root
     * @param stampFile   stamp — touched after successful processing
     */
    public static void run(File projectRoot, File partFile, File stagsDir, File stampFile)
            throws IOException {

        // Derive component name from filename: java_foo.tags → foo, c_src_bar.tags → src
        String base = partFile.getName();
        if (base.endsWith(".tags")) base = base.substring(0, base.length() - 5);
        String comp;
        if (base.startsWith("java_")) comp = base.substring(5);
        else if (base.equals("c_src")) comp = "src";
        else if (base.startsWith("c_src_")) comp = base.substring(6);
        else comp = base;

        File compDir = new File(stagsDir, "parts" + File.separator + "stags_" + comp);
        compDir.mkdirs();

        String rootPrefix = projectRoot.getAbsolutePath() + File.separator;
        Map<String, BufferedWriter> writers = new HashMap<>();

        try (BufferedReader r = new BufferedReader(new FileReader(partFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("!_")) continue;
                String[] f = line.split("\t", -1);
                if (f.length < 4) continue;

                String name    = f[0];
                String absPath = f[1];
                String kletter = f[3];

                // Skip methods early (ftags territory)
                if ("m".equals(kletter) || "method".equals(kletter)) continue;

                int    lineno   = 0;
                String kindword = "";
                String scope    = "";

                for (int i = 4; i < f.length; i++) {
                    int eq = f[i].indexOf(':');
                    if (eq < 0) {
                        // Bare word — shifted kind column
                        if (SKIP_KINDS.contains(f[i]) && kindword.isEmpty()) kindword = f[i];
                        continue;
                    }
                    String k = f[i].substring(0, eq);
                    String v = f[i].substring(eq + 1);
                    switch (k) {
                        case "line":                        lineno   = parseInt(v); break;
                        case "kind":                        kindword = v;           break;
                        case "class": case "namespace":
                        case "struct": case "enum":         scope    = v;           break;
                    }
                }

                if (lineno == 0) continue;
                if (kindword.isEmpty()) kindword = kletter;

                // Skip method/function kinds
                if (SKIP_KINDS.contains(kindword)) continue;
                if ("f".equals(kindword)) continue; // bare C function

                // Relative path
                String rel = absPath;
                if (rel.startsWith(rootPrefix)) rel = rel.substring(rootPrefix.length());
                rel = rel.replace('\\', '/');

                // Qualified name
                String qual;
                if (name.indexOf('.') >= 0) {
                    qual = name;
                } else {
                    qual = scope.isEmpty() ? name : scope + "." + name;
                }

                String row = qual + "\t" + rel + ":" + lineno + "\t" + kindword;
                writeRow(compDir, writers, qual, row);

                // Also index simple name (last dot-segment) when it differs
                int dot = qual.lastIndexOf('.');
                if (dot >= 0) {
                    String simple = qual.substring(dot + 1);
                    if (!simple.equals(qual)) {
                        writeRow(compDir, writers, simple,
                            simple + "\t" + rel + ":" + lineno + "\t" + kindword);
                    }
                }
            }
        } finally {
            for (BufferedWriter w : writers.values()) w.close();
        }

        // Sort each letter file
        FtagsMerge.sortTsvFiles(compDir);
        Util.touchStamp(stampFile);
    }

    private static void writeRow(File compDir, Map<String, BufferedWriter> writers,
                                  String key, String row) throws IOException {
        char bucket = Util.firstLetterBucket(key);
        String letter = String.valueOf(bucket);
        BufferedWriter w = writers.computeIfAbsent(letter, l -> {
            try { return new BufferedWriter(new FileWriter(new File(compDir, l + ".tsv"), true)); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        });
        w.write(row);
        w.newLine();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
