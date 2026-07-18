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
     * @param compDir   component root (e.g. {@code .../com.ibm.ws.app.manager})
     * @param partFile  output partial .tags file
     * @param stampFile stamp file — touched after a successful scan
     */
    public static void run(String ctagsBin, File compDir, File partFile, File stampFile)
            throws IOException, InterruptedException {

        // Collect Java source roots in priority order: src/com, src/main/java/com, src/main/java
        List<File> srcRoots = new ArrayList<>();
        File srcCom = new File(compDir, "src" + File.separator + "com");
        if (srcCom.isDirectory()) srcRoots.add(new File(compDir, "src"));
        File mvnCom = new File(compDir, "src" + File.separator + "main" + File.separator + "java" + File.separator + "com");
        if (mvnCom.isDirectory()) srcRoots.add(new File(compDir, "src" + File.separator + "main" + File.separator + "java"));

        if (srcRoots.isEmpty()) {
            // Component has no indexable source — write an empty placeholder
            partFile.getParentFile().mkdirs();
            if (!partFile.exists()) partFile.createNewFile();
            Util.touchStamp(stampFile);
            return;
        }

        // Check staleness: any .java newer than stamp?
        boolean stale = !stampFile.exists();
        if (!stale) {
            for (File root : srcRoots) {
                if (Util.isStale(root, JAVA_EXTS, stampFile)) { stale = true; break; }
            }
        }
        if (!stale) return;  // up to date

        // Collect all .java files
        List<String> sources = new ArrayList<>();
        for (File root : srcRoots) {
            Util.findFiles(root, ".java").stream()
                .map(File::getAbsolutePath)
                .forEach(sources::add);
        }
        sources.sort(Comparator.naturalOrder());

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
            // drain output to avoid blocking
            byte[] out = p.getInputStream().readAllBytes();
            int rc = p.waitFor();
            if (rc != 0) {
                throw new IOException("ctags exited " + rc + " for " + compDir.getName() +
                    ": " + new String(out).trim());
            }
        } finally {
            listFile.delete();
        }

        Util.touchStamp(stampFile);
    }
}
