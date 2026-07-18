package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Port of ftags.sh (directory mode) — run Universal Ctags on one Java component
 * source root, extract method ranges, write a .fns file.
 *
 * <p>Output format (tab-separated, 6 columns):
 * <pre>simple_name TAB qualified_name TAB rel_path TAB start TAB end TAB sig</pre>
 *
 * <p>Incremental: if the .fns output already exists, re-scans only if any .java
 * file under srcDir is newer than the existing output.
 */
public final class FtagsComp {

    private FtagsComp() {}

    public static void run(String ctagsBin, File srcDir, File projectRoot, File fnsFile)
            throws IOException, InterruptedException {

        if (!srcDir.isDirectory()) {
            // No src/ dir — write empty placeholder
            if (!fnsFile.exists()) { fnsFile.getParentFile().mkdirs(); fnsFile.createNewFile(); }
            return;
        }

        // Incremental check
        if (fnsFile.exists() && !Util.isStale(srcDir, new String[]{".java"}, fnsFile)) return;

        fnsFile.getParentFile().mkdirs();
        File tmp = new File(fnsFile.getAbsolutePath() + ".tmp");

        // Run ctags on the component source root, capture stdout
        List<String> cmd = Arrays.asList(
            ctagsBin,
            "--language-force=Java",
            "--kinds-Java=cm",
            "--fields=+neZsS",
            "--output-format=e-ctags",
            "--sort=no",
            "-R",
            "-f", "-",
            srcDir.getAbsolutePath()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();

        String absRoot = projectRoot.getAbsolutePath() + File.separator;
        List<String> fnsLines = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("!_")) continue;
                String[] f = line.split("\t", -1);
                if (f.length < 4) continue;

                String name  = f[0];
                String file  = f[1];
                String kind  = f[3];

                // Only keep methods ('m')
                if (!"m".equals(kind) && !"method".equals(kind)) continue;

                int lineno = 0, endno = 0;
                String sig = "-", scope = "";
                for (int i = 4; i < f.length; i++) {
                    int eq = f[i].indexOf(':');
                    if (eq < 0) continue;
                    String k = f[i].substring(0, eq);
                    String v = f[i].substring(eq + 1);
                    switch (k) {
                        case "line":      lineno = parseInt(v); break;
                        case "end":       endno  = parseInt(v); break;
                        case "signature": sig    = v; break;
                        case "scope": {
                            int colon = v.indexOf(':');
                            scope = colon >= 0 ? v.substring(colon + 1) : v;
                            break;
                        }
                    }
                }
                if (lineno == 0) continue;
                if (endno  == 0) endno = lineno;
                if (sig.isEmpty()) sig = "-";

                String rel = file;
                if (rel.startsWith(absRoot)) rel = rel.substring(absRoot.length());
                // Normalise to forward slashes for cross-platform .fns files
                rel = rel.replace('\\', '/');

                String qualified = scope.isEmpty() ? name : scope + "." + name;
                fnsLines.add(name + "\t" + qualified + "\t" + rel + "\t" + lineno + "\t" + endno + "\t" + sig);
            }
        }

        // Drain stderr
        p.getErrorStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor();

        if (fnsLines.isEmpty()) {
            fnsFile.createNewFile();
            return;
        }

        Files.write(tmp.toPath(), fnsLines);
        Util.atomicMove(tmp, fnsFile);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
