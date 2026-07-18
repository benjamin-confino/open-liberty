package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of xtags_merge.sh — filter .calls files by known callee names and
 * merge into flat cross-component letter TSVs.
 */
public final class XtagsMerge {

    private XtagsMerge() {}

    public static void run(File callsDir, File outDir, File ftagsDir) throws IOException {
        outDir.mkdirs();

        // Build known-names set from ftags known_names.tsv (or flat TSVs as fallback)
        Set<String> knownNames = loadKnownNames(ftagsDir);

        // Collect all non-empty .calls files
        List<File> callFiles = Arrays.stream(
                Objects.requireNonNull(callsDir.listFiles(f -> f.getName().endsWith(".calls"))))
            .filter(f -> f.length() > 0)
            .sorted()
            .collect(Collectors.toList());

        if (callFiles.isEmpty()) {
            System.err.println("[xtags] No non-empty .calls files found.");
            return;
        }

        // Fan out to letter files, then dedup-sort
        Map<Character, BufferedWriter> writers = new HashMap<>();
        try {
            for (File callFile : callFiles) {
                try (BufferedReader r = new BufferedReader(new FileReader(callFile))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        int tab = line.indexOf('\t');
                        if (tab < 0) continue;
                        String callee = line.substring(0, tab);
                        if (!knownNames.contains(callee)) continue;
                        char bucket = Util.firstLetterBucket(callee);
                        BufferedWriter w = writers.computeIfAbsent(bucket, b -> {
                            try { return new BufferedWriter(new FileWriter(new File(outDir, b + ".tsv"), true)); }
                            catch (IOException e) { throw new UncheckedIOException(e); }
                        });
                        w.write(line);
                        w.newLine();
                    }
                }
            }
        } finally {
            for (BufferedWriter w : writers.values()) w.close();
        }

        // Deduplicate and sort each letter file
        File[] tsvFiles = outDir.listFiles(f -> f.getName().endsWith(".tsv"));
        if (tsvFiles != null) {
            for (File tsv : tsvFiles) {
                List<String> lines = Util.readLines(tsv);
                List<String> sorted = lines.stream().distinct().sorted().collect(Collectors.toList());
                Util.writeLines(tsv, sorted);
            }
        }

        long total = FtagsMerge.countLines(outDir, "[a-z_].tsv");
        System.out.printf("[xtags] Mapped Bob-friendly call-site lookup index  (%,d call graph links)%n", total);
    }

    private static Set<String> loadKnownNames(File ftagsDir) throws IOException {
        File prebuilt = new File(ftagsDir, "known_names.tsv");
        Set<String> names = new HashSet<>();
        if (prebuilt.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(prebuilt))) {
                String line;
                while ((line = r.readLine()) != null) if (!line.isEmpty()) names.add(line.trim());
            }
            return names;
        }
        // Fallback: scan flat TSVs
        File[] flats = ftagsDir.listFiles(f -> f.getName().matches("[a-z_].tsv"));
        if (flats == null) return names;
        for (File flat : flats) {
            try (BufferedReader r = new BufferedReader(new FileReader(flat))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) names.add(line.substring(0, tab));
                }
            }
        }
        return names;
    }
}
