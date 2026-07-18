package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of ftags_merge.sh — merge per-component .fns files into a two-level
 * index:
 * <ul>
 *   <li>{@code parts/ftags_<comp>/<letter>.tsv} — per-component parts</li>
 *   <li>{@code <letter>.tsv} — flat cross-component index</li>
 *   <li>{@code hints.tsv} — simple-name → component list</li>
 * </ul>
 *
 * <p>Full rebuild (no changed-component args): processes all non-empty .fns files.
 */
public final class FtagsMerge {

    private FtagsMerge() {}

    public static void run(File fnsDir, File outDir) throws IOException {
        List<File> fnsFiles = Arrays.stream(
                Objects.requireNonNull(fnsDir.listFiles(f -> f.getName().endsWith(".fns"))))
            .filter(f -> f.length() > 0)
            .sorted()
            .collect(Collectors.toList());

        if (fnsFiles.isEmpty()) {
            System.err.println("[ftags] No non-empty .fns files found.");
            return;
        }
        System.out.println("[ftags] Building function/method lookup index ...");

        outDir.mkdirs();

        // Step 1: build per-component parts directories
        for (File fns : fnsFiles) {
            String stem       = stemOf(fns.getName(), ".fns");
            String compName   = compDirName(stem);
            File   partsDir   = new File(outDir, "parts" + File.separator + "ftags_" + compName);
            // Wipe and rebuild this component's part directory
            deleteDir(partsDir);
            partsDir.mkdirs();
            appendFnsToParts(fns, partsDir);
            // Sort each letter file so flat merge can use merge-sort
            sortTsvFiles(partsDir);
        }

        // Step 2: assemble flat cross-component letter files
        Set<String> letters = collectLetters(new File(outDir, "parts"), "ftags_");
        for (String letter : letters) {
            List<File> parts = new ArrayList<>();
            File partsRoot = new File(outDir, "parts");
            File[] compDirs = partsRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("ftags_"));
            if (compDirs != null) {
                for (File cd : compDirs) {
                    File lf = new File(cd, letter + ".tsv");
                    if (lf.exists()) parts.add(lf);
                }
            }
            if (!parts.isEmpty()) {
                File flat = new File(outDir, letter + ".tsv");
                mergeSortedFiles(parts, flat);
            }
        }

        // Step 3: rebuild hints.tsv
        buildHints(outDir);

        // Build known_names.tsv
        buildKnownNames(outDir);

        // Summary
        long total = countLines(outDir, "[a-z_].tsv");
        File[] partDirs = new File(outDir, "parts").listFiles(File::isDirectory);
        int nComps = partDirs == null ? 0 : partDirs.length;
        System.out.printf("[ftags] Wrote Bob-tuned function/method lookup index from %d components  (%,d file:n..m ranges mapped)%n",
            nComps, total);
    }

    // -----------------------------------------------------------------------

    private static String stemOf(String name, String suffix) {
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /** java_sv → sv;  c_src_hl → src;  anything else → stem */
    static String compDirName(String stem) {
        if (stem.startsWith("java_")) return stem.substring(5);
        if (stem.startsWith("c_src_")) return "src";
        return stem;
    }

    /**
     * Append rows from a .fns file into per-letter TSV files under partsDir.
     * For each row, emit both the qualified name key and the simple name key
     * (last dot-segment) when they differ.
     */
    private static void appendFnsToParts(File fnsFile, File partsDir) throws IOException {
        // .fns columns: simple TAB qualified TAB rel_path TAB start TAB end TAB sig
        // We emit:   key TAB rel_path:start-end TAB sig
        Map<String, BufferedWriter> writers = new HashMap<>();
        try {
            try (BufferedReader r = new BufferedReader(new FileReader(fnsFile))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.split("\t", -1);
                    if (f.length < 5) continue;
                    String qual = f[1];
                    String loc  = f[2] + ":" + f[3] + "-" + f[4];
                    String sig  = f.length >= 6 ? f[5] : "-";
                    writeRow(partsDir, writers, qual, loc, sig);
                    // Also index simple name (last dot-segment)
                    int dot = qual.lastIndexOf('.');
                    if (dot >= 0) {
                        String simple = qual.substring(dot + 1);
                        if (!simple.equals(qual)) writeRow(partsDir, writers, simple, loc, sig);
                    }
                }
            }
        } finally {
            for (BufferedWriter w : writers.values()) w.close();
        }
    }

    private static void writeRow(File partsDir, Map<String, BufferedWriter> writers,
                                  String key, String loc, String sig) throws IOException {
        char bucket = Util.firstLetterBucket(key);
        String letter = String.valueOf(bucket);
        BufferedWriter w = writers.computeIfAbsent(letter, l -> {
            try {
                File f = new File(partsDir, l + ".tsv");
                return new BufferedWriter(new FileWriter(f, true));
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        w.write(key + "\t" + loc + "\t" + sig);
        w.newLine();
    }

    static void sortTsvFiles(File dir) throws IOException {
        File[] tsvs = dir.listFiles(f -> f.getName().endsWith(".tsv"));
        if (tsvs == null) return;
        for (File tsv : tsvs) {
            List<String> lines = Util.readLines(tsv);
            Collections.sort(lines);
            Util.writeLines(tsv, lines);
        }
    }

    static void mergeSortedFiles(List<File> parts, File output) throws IOException {
        // Priority-queue merge of already-sorted files
        PriorityQueue<LineReader> pq = new PriorityQueue<>(Comparator.comparing(lr -> lr.current));
        List<BufferedReader> readers = new ArrayList<>();
        for (File p : parts) {
            BufferedReader r = new BufferedReader(new FileReader(p));
            String line = r.readLine();
            if (line != null) { pq.offer(new LineReader(r, line)); readers.add(r); }
            else r.close();
        }
        output.getParentFile().mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(output))) {
            while (!pq.isEmpty()) {
                LineReader lr = pq.poll();
                w.write(lr.current); w.newLine();
                String next = lr.reader.readLine();
                if (next != null) pq.offer(new LineReader(lr.reader, next));
                else lr.reader.close();
            }
        } finally {
            for (BufferedReader r : readers) try { r.close(); } catch (IOException ignored) {}
        }
    }

    private static Set<String> collectLetters(File partsRoot, String prefix) {
        Set<String> letters = new TreeSet<>();
        File[] compDirs = partsRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith(prefix));
        if (compDirs == null) return letters;
        for (File cd : compDirs) {
            File[] tsvs = cd.listFiles(f -> f.getName().endsWith(".tsv"));
            if (tsvs == null) continue;
            for (File t : tsvs) letters.add(stemOf(t.getName(), ".tsv"));
        }
        return letters;
    }

    private static void buildHints(File outDir) throws IOException {
        // hints: simple_name → comma-separated list of component prefixes
        Map<String, Set<String>> hints = new TreeMap<>();
        File[] flats = outDir.listFiles(f -> f.getName().matches("[a-z_].tsv"));
        if (flats == null) return;
        for (File flat : flats) {
            try (BufferedReader r = new BufferedReader(new FileReader(flat))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.split("\t", -1);
                    if (f.length < 2) continue;
                    String key = f[0];
                    if (key.indexOf('.') < 0) continue; // only qualified names
                    String loc = f[1];
                    // simple = last dot-segment
                    String simple = key.substring(key.lastIndexOf('.') + 1);
                    if (simple.equals(key)) continue;
                    // component = first path segment of loc (before '/' or ':')
                    int slash = loc.indexOf('/');
                    int colon = loc.indexOf(':');
                    int end = (slash >= 0 && (colon < 0 || slash < colon)) ? slash : colon;
                    String comp = end >= 0 ? loc.substring(0, end) : loc;
                    hints.computeIfAbsent(simple, k -> new LinkedHashSet<>()).add(comp);
                }
            }
        }
        File hintsFile = new File(outDir, "hints.tsv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(hintsFile))) {
            for (Map.Entry<String, Set<String>> e : hints.entrySet()) {
                w.write(e.getKey() + "\t" + String.join(",", e.getValue()));
                w.newLine();
            }
        }
    }

    private static void buildKnownNames(File outDir) throws IOException {
        Set<String> names = new TreeSet<>();
        File[] flats = outDir.listFiles(f -> f.getName().matches("[a-z_].tsv"));
        if (flats == null) return;
        for (File flat : flats) {
            try (BufferedReader r = new BufferedReader(new FileReader(flat))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) names.add(line.substring(0, tab));
                }
            }
        }
        Util.writeLines(new File(outDir, "known_names.tsv"), new ArrayList<>(names));
    }

    static long countLines(File dir, String pattern) throws IOException {
        File[] files = dir.listFiles(f -> f.getName().matches(pattern));
        if (files == null) return 0;
        long count = 0;
        for (File f : files) count += Files.lines(f.toPath()).count();
        return count;
    }

    static void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) f.delete();
        dir.delete();
    }

    static class LineReader {
        final BufferedReader reader;
        final String current;
        LineReader(BufferedReader r, String c) { reader = r; current = c; }
    }
}
