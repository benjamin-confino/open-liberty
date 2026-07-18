package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Port of gen_ltags.sh — pivot a .fns file into per-source-file .range files.
 *
 * <p>Each .range file contains 4-column TSV rows sorted by start line:
 * <pre>rel_path TAB start TAB end TAB qualified_name</pre>
 *
 * <p>Entries arrive grouped by rel_path and sorted by start line within each
 * group (the order ftags.sh / ctags produces), so no sort step is needed.
 */
public final class LtagsComp {

    private LtagsComp() {}

    /**
     * @param fnsFile   component .fns file from ftags
     * @param ltagsDir  directory to write .range files into
     * @param stampFile stamp — touched after successful processing
     */
    public static void run(File fnsFile, File ltagsDir, File stampFile) throws IOException {
        if (!Util.isFnsStale(fnsFile, stampFile)) return;

        ltagsDir.mkdirs();

        // Streaming split: group rows by rel_path, write one .range file each.
        // .fns columns: simple TAB qualified TAB rel_path TAB start TAB end TAB sig
        String curPath  = null;
        BufferedWriter  curWriter = null;
        File            curFile   = null;

        try (BufferedReader r = new BufferedReader(new FileReader(fnsFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue;
                String relPath = f[2].replace('\\', '/');
                // Only index .java and .c files
                if (!relPath.endsWith(".java") && !relPath.endsWith(".c")) continue;

                if (!relPath.equals(curPath)) {
                    if (curWriter != null) { curWriter.close(); curWriter = null; }
                    String stem = Util.pathToStem(relPath);
                    curFile   = new File(ltagsDir, stem + ".range");
                    curPath   = relPath;
                    curWriter = new BufferedWriter(new FileWriter(curFile));
                }
                // Write: rel_path TAB start TAB end TAB qualified_name
                curWriter.write(relPath + "\t" + f[3] + "\t" + f[4] + "\t" + f[1]);
                curWriter.newLine();
            }
        } finally {
            if (curWriter != null) try { curWriter.close(); } catch (IOException ignored) {}
        }

        Util.touchStamp(stampFile);
    }
}
