package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Source component discovery.
 *
 * <p>A directory under {@code projectRoot} is a component if it contains a
 * Java source root — a sub-directory that itself contains at least one
 * {@code .java} file anywhere beneath it. 
 
 * <p>No package-name assumptions ({@code com}, {@code io}, {@code org} …) are
 * made; any source root that actually contains {@code .java} files qualifies.
 *
 * <p>The result is a {@link Map} from canonical component-root path to its
 * resolved {@link File} source-root directory. When both layouts are present in
 * the same component, both source roots are included under a single component
 * key (the flat {@code src/} is the canonical root in that case).
 */
public final class SourceDiscovery {

    private SourceDiscovery() {}

    /**
     * Candidate source-root sub-paths probed under each top-level component
     * directory, in priority order. The first one that contains at least one
     * {@code .java} file wins.
     *
     * <p>Uses {@code File.separator} so the paths are correct on Windows too.
     */
    public static final List<String> SRC_CANDIDATES = Arrays.asList(
        "src",
        "src" + File.separator + "main" + File.separator + "java"
    );

    /** Exclusion patterns for generated/binary directories. */
    private static final List<String> EXCLUDES = Arrays.asList(
        File.separator + "bin" + File.separator,
        File.separator + "bin_test" + File.separator,
        File.separator + "btags" + File.separator,
        File.separator + "bin",
        File.separator + "bin_test"
    );

    /**
     * Returns a sorted map of {@code compPath → srcRoot} for every qualifying
     * component under {@code projectRoot}.
     *
     * <p>The map is keyed by canonical component-root path (sorted). The value
     * is the resolved Java source-root directory to pass to ctags / ftags —
     * the first entry in {@link #SRC_CANDIDATES} that actually contains
     * {@code .java} files.
     */
    public static Map<String, File> findJavaComponents(File projectRoot) throws IOException {
        Map<String, File> result = new TreeMap<>();

        File[] topLevel = projectRoot.listFiles(File::isDirectory);
        if (topLevel == null) return result;

        for (File top : topLevel) {
            String abs = top.getCanonicalPath();
            if (isExcluded(abs)) continue;

            for (String candidate : SRC_CANDIDATES) {
                File srcRoot = new File(top, candidate);
                if (hasJavaSources(srcRoot)) {
                    result.put(abs, srcRoot);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Convenience method returning only the sorted list of component-root paths.
     * Equivalent to {@code findJavaComponents(root).keySet()}.
     */
    public static List<String> findJavaCompDirs(File projectRoot) throws IOException {
        return new ArrayList<>(findJavaComponents(projectRoot).keySet());
    }

    // -----------------------------------------------------------------------

    /** Returns true if {@code dir} exists and contains at least one .java file. */
    private static boolean hasJavaSources(File dir) {
        if (!dir.isDirectory()) return false;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            return s.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isExcluded(String abs) {
        for (String ex : EXCLUDES) {
            if (abs.contains(ex) || abs.endsWith(ex)) return true;
        }
        return false;
    }
}
