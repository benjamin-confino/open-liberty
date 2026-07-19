package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of ctags_part.sh — run Universal Ctags on one Java component.
 *
 * <p>Incremental: if the partial .tags file already exists, only re-scans
 * if any .java source file is newer than the stamp. When re-scanning, the
 * full component is re-scanned (ctags handles its own incremental sorting).
 */
public final class CtagsPart {

    private CtagsPart() {}

    private static final String[] JAVA_EXTS = {".java"};

    /**
     * @param ctagsBin  absolute path to the Universal Ctags binary
     * @param srcRoot   resolved Java source root (e.g. {@code .../comp/src} or
     *                  {@code .../comp/src/main/java}) — provided by the caller,
     *                  no package-name assumptions made here
     * @param partFile  output partial .tags file
     * @param stampFile stamp file — touched after a successful scan
     */
    public static void run(String ctagsBin, File srcRoot, File partFile, File stampFile)
            throws IOException, InterruptedException {

        if (!srcRoot.isDirectory()) {
            // Source root does not exist — write an empty placeholder
            partFile.getParentFile().mkdirs();
            if (!partFile.exists()) partFile.createNewFile();
            Util.touchStamp(stampFile);
            return;
        }

        // Incremental check: any .java newer than stamp?
        if (stampFile.exists() && !Util.isStale(srcRoot, JAVA_EXTS, stampFile)) return;

        // Collect all .java files under the source root
        List<String> sources = Util.findFiles(srcRoot, ".java").stream()
            .map(File::getAbsolutePath)
            .sorted()
            .collect(Collectors.toList());

        partFile.getParentFile().mkdirs();

        if (sources.isEmpty()) {
            if (!partFile.exists()) partFile.createNewFile();
            Util.touchStamp(stampFile);
            return;
        }

        // Write source list to a temp file for ctags -L
        File listFile = File.createTempFile("btags_ctags_", ".lst");
        try {
            Files.write(listFile.toPath(), sources);

            List<String> cmd = new ArrayList<>(Arrays.asList(
                ctagsBin,
                "--language-force=Java",
                "--kinds-Java=cgimpf",
                "--fields=+nKs",
                "--extras=+q",
                "--sort=yes",
                "-f", partFile.getAbsolutePath(),
                "-L", listFile.getAbsolutePath()
            ));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            int rc = p.waitFor();
            if (rc != 0) {
                throw new IOException("ctags exited " + rc + " for " + srcRoot +
                    ": " + new String(out).trim());
            }
        } finally {
            listFile.delete();
        }

        Util.touchStamp(stampFile);
    }
}
