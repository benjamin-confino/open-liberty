package btags;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Port of gen_xtags_comp.sh — scan source files belonging to one component
 * for call sites and write .calls files.
 *
 * <p>Algorithm (mirrors the shell/awk original):
 * <ol>
 *   <li>Load the component .fns file: build a per-file sorted range table.</li>
 *   <li>For each source file (.java, .c): stream line by line, strip comments
 *       and string literals, scan for {@code identifier(} patterns, binary-search
 *       the range table to find the enclosing method, and emit a .calls row.</li>
 * </ol>
 *
 * <p>.calls output format (3 columns, tab-separated):
 * <pre>callee_name TAB caller_qualified_name TAB rel_path:line</pre>
 */
public final class XtagsComp {

    private XtagsComp() {}

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "if", "for", "while", "switch", "catch", "return",
        "new", "class", "interface", "enum", "synchronized"
    ));

    private static final Pattern CALL_PAT =
        Pattern.compile("[A-Za-z_][A-Za-z0-9_]*[\\s]*\\(");

    /**
     * @param fnsFile    component .fns file from ftags
     * @param projectRoot project root (to resolve rel_path → absolute src file)
     * @param callsDir   directory to write .calls files
     * @param ltagsDir   directory containing .range files (unused here; range tables
     *                   are rebuilt inline from the .fns file for correctness)
     * @param stampFile  touched after successful processing
     */
    public static void run(File fnsFile, File projectRoot, File callsDir,
                           File ltagsDir, File stampFile) throws IOException {
        if (!Util.isFnsStale(fnsFile, stampFile)) return;
        callsDir.mkdirs();

        // Phase 1: load .fns into per-file range tables
        // Key = rel_path; value = sorted list of [start, end, qualifiedName]
        Map<String, List<long[]>> rangeTable = new LinkedHashMap<>(); // preserves insertion order
        Map<String, List<String>> rangeNames = new LinkedHashMap<>();

        try (BufferedReader r = new BufferedReader(new FileReader(fnsFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue;
                String relPath = f[2].replace('\\', '/');
                if (!relPath.endsWith(".java") && !relPath.endsWith(".c")) continue;
                int start = parseInt(f[3]);
                int end   = parseInt(f[4]);
                if (start == 0) continue;
                if (end < start) end = start;
                rangeTable.computeIfAbsent(relPath, k -> new ArrayList<>())
                           .add(new long[]{start, end});
                rangeNames.computeIfAbsent(relPath, k -> new ArrayList<>())
                           .add(f[1]); // qualified name
            }
        }

        // Derive fns stem for naming .calls files (mirrors shell: java_sv.fns → sv_*)
        String fnsStem = fnsFile.getName().replace(".fns", "");
        String callPrefix = fnsStem.startsWith("java_") ? fnsStem.substring(5) : fnsStem;

        // Phase 2: scan each source file
        String rootAbs = projectRoot.getAbsolutePath().replace('\\', '/');
        if (!rootAbs.endsWith("/")) rootAbs += "/";

        for (String relPath : rangeTable.keySet()) {
            File srcFile = new File(projectRoot, relPath);
            if (!srcFile.exists()) continue;

            List<long[]>  ranges = rangeTable.get(relPath);
            List<String>  names  = rangeNames.get(relPath);
            int nRanges = ranges.size();

            String stem      = Util.pathToStem(relPath);
            File   callsFile = new File(callsDir, stem + ".calls");

            List<String> calls = new ArrayList<>();
            boolean inBlock = false;
            int lineno = 0;

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(srcFile), StandardCharsets.ISO_8859_1))) {
                String raw;
                while ((raw = r.readLine()) != null) {
                    lineno++;
                    String line = raw;

                    // Strip block comment continuation
                    if (inBlock) {
                        int end = line.indexOf("*/");
                        if (end >= 0) { line = line.substring(end + 2); inBlock = false; }
                        else continue;
                    }

                    // Remove closed block comments on this line
                    if (line.contains("/*")) {
                        line = line.replaceAll("/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/", "");
                        // Detect unclosed block comment
                        int open = line.indexOf("/*");
                        if (open >= 0) { line = line.substring(0, open); inBlock = true; }
                    }

                    // Strip line comments
                    int lc = line.indexOf("//");
                    if (lc >= 0) line = line.substring(0, lc);

                    // Strip string literals
                    if (line.contains("\"")) line = line.replaceAll("\"[^\"]*\"", "\"\"");
                    if (line.contains("'"))  line = line.replaceAll("'[^']*'", "''");

                    if (!line.contains("(")) continue;

                    // Binary-search for enclosing method
                    int lo = 0, hi = nRanges - 1, methodIdx = -1;
                    while (lo <= hi) {
                        int mid = (lo + hi) >>> 1;
                        long start = ranges.get(mid)[0];
                        long end   = ranges.get(mid)[1];
                        if      (lineno < start) hi  = mid - 1;
                        else if (lineno > end)   lo  = mid + 1;
                        else { methodIdx = mid; break; }
                    }
                    if (methodIdx < 0) continue;
                    String method = names.get(methodIdx);

                    // Scan for call sites
                    Matcher m = CALL_PAT.matcher(line);
                    while (m.find()) {
                        String token = m.group().replaceAll("[\\s]*\\($", "").trim();
                        if (!KEYWORDS.contains(token) && !token.equals(method)) {
                            calls.add(token + "\t" + method + "\t" + relPath + ":" + lineno);
                        }
                    }
                }
            }

            if (!calls.isEmpty()) {
                Collections.sort(calls);
                Util.writeLines(callsFile, calls);
            }
        }

        Util.touchStamp(stampFile);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
