package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Source component discovery — mirrors GNUmakefile JAVA_COMP_DIRS logic.
 * Handles both open-liberty layout ({@code <comp>/src/com}) and Maven
 * layout ({@code <comp>/src/main/java/com}).
 */
public final class SourceDiscovery {

    private SourceDiscovery() {}

    /** Exclusion patterns for generated/binary directories. */
    private static final List<String> EXCLUDES = Arrays.asList(
        File.separator + "bin" + File.separator,
        File.separator + "bin_test" + File.separator,
        File.separator + "btags" + File.separator,
        File.separator + "bin",
        File.separator + "bin_test"
    );

    /**
     * Returns a sorted, deduplicated list of component root directories.
     * A component root is the parent of a Java source root:
     * <ul>
     *   <li>{@code <comp>/src/com}            → open-liberty flat layout</li>
     *   <li>{@code <comp>/src/main/java/com}  → Maven layout</li>
     * </ul>
     */
    public static List<String> findJavaCompDirs(File projectRoot) throws IOException {
        Set<String> dirs = new TreeSet<>();

        File[] topLevel = projectRoot.listFiles(File::isDirectory);
        if (topLevel == null) return Collections.emptyList();

        for (File top : topLevel) {
            String abs = top.getCanonicalPath();
            if (isExcluded(abs)) continue;

            // Layout 1: <comp>/src/com
            if (new File(top, "src" + File.separator + "com").isDirectory()) {
                dirs.add(abs);
            }
            // Layout 2: <comp>/src/main/java/com
            if (new File(top, "src" + File.separator + "main" + File.separator +
                              "java" + File.separator + "com").isDirectory()) {
                dirs.add(abs);
            }
        }
        return new ArrayList<>(dirs);
    }

    private static boolean isExcluded(String abs) {
        for (String ex : EXCLUDES) {
            if (abs.contains(ex) || abs.endsWith(ex)) return true;
        }
        return false;
    }
}
