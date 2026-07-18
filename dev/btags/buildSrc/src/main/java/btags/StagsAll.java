package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of gen_stags_all.sh — merge per-component stags parts into flat
 * cross-component letter TSVs at the stags root.
 */
public final class StagsAll {

    private StagsAll() {}

    public static void run(File stagsDir) throws IOException {
        File partsRoot = new File(stagsDir, "parts");
        if (!partsRoot.isDirectory()) {
            System.err.println("[stags] No parts directory found — nothing to merge.");
            return;
        }

        // Collect all letters that appear across all component parts
        Set<String> letters = new TreeSet<>();
        File[] compDirs = partsRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("stags_"));
        if (compDirs == null || compDirs.length == 0) {
            System.err.println("[stags] No stags component parts found.");
            return;
        }
        for (File cd : compDirs) {
            File[] tsvs = cd.listFiles(f -> f.getName().endsWith(".tsv"));
            if (tsvs == null) continue;
            for (File t : tsvs) letters.add(t.getName().replace(".tsv", ""));
        }

        for (String letter : letters) {
            List<File> parts = new ArrayList<>();
            for (File cd : compDirs) {
                File f = new File(cd, letter + ".tsv");
                if (f.exists()) parts.add(f);
            }
            if (!parts.isEmpty()) {
                FtagsMerge.mergeSortedFiles(parts, new File(stagsDir, letter + ".tsv"));
            }
        }

        long total = FtagsMerge.countLines(stagsDir, "[a-z_].tsv");
        System.out.printf("[stags] Created Bob-specific type/struct/enum lookup index  (%,d entries)%n", total);

        // Touch sentinel file (a.tsv must already be the merge output, or create it)
        File sentinel = new File(stagsDir, "a.tsv");
        if (!sentinel.exists()) sentinel.createNewFile();
        sentinel.setLastModified(System.currentTimeMillis());
    }
}
