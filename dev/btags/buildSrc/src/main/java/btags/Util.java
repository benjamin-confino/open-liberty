package btags;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Shared utilities: ctags binary discovery, file ops, stamp checks.
 */
public final class Util {

    private Util() {}

    // -----------------------------------------------------------------------
    // Find Universal Ctags binary (cross-platform)
    // -----------------------------------------------------------------------
    private static volatile String cachedCtags;

    public static String findCtags() {
        if (cachedCtags != null) return cachedCtags;
        synchronized (Util.class) {
            if (cachedCtags != null) return cachedCtags;
            List<String> candidates = new ArrayList<>();
            // Standard PATH lookup first
            String pathCtags = which("ctags");
            if (pathCtags != null) candidates.add(pathCtags);
            // macOS Homebrew locations
            candidates.add("/opt/homebrew/bin/ctags");
            candidates.add("/usr/local/bin/ctags");
            // Windows: ctags.exe via common installers / scoop / choco
            candidates.add("C:/ProgramData/scoop/shims/ctags.exe");
            candidates.add("C:/tools/ctags/ctags.exe");
            for (String c : candidates) {
                if (isUniversalCtags(c)) {
                    cachedCtags = c;
                    return c;
                }
            }
            throw new RuntimeException(
                "Universal Ctags not found. Install it:\n" +
                "  macOS:  brew install universal-ctags\n" +
                "  Debian: sudo apt install universal-ctags\n" +
                "  RHEL:   sudo dnf install epel-release && sudo dnf --enablerepo=epel install ctags\n" +
                "  Windows: scoop install ctags  (or choco install universal-ctags)"
            );
        }
    }

    private static String which(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        String ext = System.getProperty("os.name", "").toLowerCase().contains("win") ? ".exe" : "";
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, name + ext);
            if (f.canExecute()) return f.getAbsolutePath();
            // also try without extension on Windows
            if (!ext.isEmpty()) {
                f = new File(dir, name);
                if (f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static boolean isUniversalCtags(String path) {
        File f = new File(path);
        if (!f.canExecute()) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out.toLowerCase().contains("universal ctags");
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Stamp helpers
    // -----------------------------------------------------------------------

    /** Returns true if sourceDir has a file newer than stampFile (or stamp absent). */
    public static boolean isStale(File sourceDir, String[] extensions, File stampFile) {
        if (!stampFile.exists()) return true;
        long stampMs = stampFile.lastModified();
        try {
            return Files.walk(sourceDir.toPath())
                .filter(p -> {
                    String n = p.getFileName().toString();
                    for (String ext : extensions) if (n.endsWith(ext)) return true;
                    return false;
                })
                .anyMatch(p -> p.toFile().lastModified() > stampMs);
        } catch (IOException e) {
            return true; // assume stale on error
        }
    }

    /** Returns true if fnsFile is newer than stampFile (or stamp absent). */
    public static boolean isFnsStale(File fnsFile, File stampFile) {
        return !stampFile.exists() || fnsFile.lastModified() > stampFile.lastModified();
    }

    public static void touchStamp(File stampFile) throws IOException {
        stampFile.getParentFile().mkdirs();
        stampFile.createNewFile();
        stampFile.setLastModified(System.currentTimeMillis());
    }

    // -----------------------------------------------------------------------
    // File helpers
    // -----------------------------------------------------------------------

    public static List<String> readLines(File f) throws IOException {
        if (!f.exists() || f.length() == 0) return Collections.emptyList();
        return Files.readAllLines(f.toPath());
    }

    public static void writeLines(File f, List<String> lines) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void appendLine(File f, String line) throws IOException {
        try (var w = new BufferedWriter(new FileWriter(f, true))) {
            w.write(line);
            w.newLine();
        }
    }

    /** Atomically replace dest with tmp. */
    public static void atomicMove(File tmp, File dest) throws IOException {
        Files.move(tmp.toPath(), dest.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Walk a directory tree and collect files matching extensions. */
    public static List<File> findFiles(File dir, String... extensions) throws IOException {
        if (!dir.isDirectory()) return Collections.emptyList();
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            return s.filter(p -> {
                    String n = p.getFileName().toString();
                    for (String ext : extensions) if (n.endsWith(ext)) return true;
                    return false;
                })
                .map(Path::toFile)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /** Make a path relative to root; returns original string if not under root. */
    public static String relativize(File root, File file) {
        String r = root.getAbsolutePath();
        String f = file.getAbsolutePath();
        if (f.startsWith(r + File.separator)) return f.substring(r.length() + 1);
        if (f.startsWith(r + "/"))  return f.substring(r.length() + 1);
        return f;
    }

    /** Replace all '/' with '_' — used for .range/.calls file names. */
    public static String pathToStem(String relPath) {
        return relPath.replace('/', '_').replace('\\', '_');
    }

    /** First character bucketed to a lowercase letter or '_'. */
    public static char firstLetterBucket(String key) {
        if (key.isEmpty()) return '_';
        char c = key.charAt(0);
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return Character.toLowerCase(c);
        return '_';
    }
}
