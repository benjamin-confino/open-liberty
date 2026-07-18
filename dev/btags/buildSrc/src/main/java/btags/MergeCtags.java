package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of merge_ctags.sh — merge sorted per-component .tags partials into one
 * canonical sorted tags file using a priority-queue merge (O(N)).
 */
public final class MergeCtags {

    private MergeCtags() {}

    private static final String HEADER =
        "!_TAG_FILE_FORMAT\t2\t/extended format; --format=1 will not append ;\" to lines/\n" +
        "!_TAG_FILE_SORTED\t1\t/0=unsorted, 1=sorted, 2=foldcase/\n" +
        "!_TAG_OUTPUT_EXCMD\tmixed\t/number, pattern, mixed, or combineV2/\n" +
        "!_TAG_OUTPUT_FILESEP\tslash\t/uses slash as filename separator/\n" +
        "!_TAG_OUTPUT_MODE\tu-ctags\t/u-ctags or e-ctags/\n" +
        "!_TAG_PATTERN_LENGTH_LIMIT\t96\t/0 for no limit/\n" +
        "!_TAG_PROC_CWD\t./\t//\n" +
        "!_TAG_PROGRAM_AUTHOR\tUniversal Ctags Team\t//\n" +
        "!_TAG_PROGRAM_NAME\tUniversal Ctags\t/Derived from Exuberant Ctags/\n" +
        "!_TAG_PROGRAM_URL\thttps://ctags.io/\t/official site/\n" +
        "!_TAG_PROGRAM_VERSION\t0.0.0\t/merged/\n";

    public static void run(File partsDir, File outputFile) throws IOException {
        List<File> parts = Arrays.stream(
                Objects.requireNonNull(partsDir.listFiles(f -> f.getName().endsWith(".tags")),
                    "Cannot list " + partsDir))
            .sorted()
            .collect(Collectors.toList());

        outputFile.getParentFile().mkdirs();
        File tmp = new File(outputFile.getAbsolutePath() + ".tmp");

        try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
            w.write(HEADER);

            if (!parts.isEmpty()) {
                // Priority-queue merge — each reader is already sorted
                PriorityQueue<ReaderLine> pq = new PriorityQueue<>(
                    Comparator.comparing(rl -> rl.current));
                List<BufferedReader> readers = new ArrayList<>();
                for (File p : parts) {
                    BufferedReader r = new BufferedReader(new FileReader(p));
                    String line = nextTagLine(r);
                    if (line != null) {
                        pq.offer(new ReaderLine(r, line));
                        readers.add(r);
                    } else {
                        r.close();
                    }
                }
                try {
                    while (!pq.isEmpty()) {
                        ReaderLine rl = pq.poll();
                        w.write(rl.current);
                        w.newLine();
                        String next = nextTagLine(rl.reader);
                        if (next != null) {
                            pq.offer(new ReaderLine(rl.reader, next));
                        } else {
                            rl.reader.close();
                        }
                    }
                } finally {
                    for (BufferedReader r : readers) {
                        try { r.close(); } catch (IOException ignored) {}
                    }
                }
            }
        }

        Util.atomicMove(tmp, outputFile);

        long lines = Files.lines(outputFile.toPath()).count();
        System.out.printf("[ctags] Merged %d parallel tag files → %s  (index count: %,d)%n",
            parts.size(), outputFile.getAbsolutePath(), lines);
    }

    /** Read next non-pseudo-tag line from a reader. */
    private static String nextTagLine(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (!line.startsWith("!_")) return line;
        }
        return null;
    }

    private static final class ReaderLine {
        final BufferedReader reader;
        final String current;
        ReaderLine(BufferedReader reader, String current) {
            this.reader  = reader;
            this.current = current;
        }
    }
}
