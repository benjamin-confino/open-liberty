/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package btags;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Builds the mtags message-code index.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Walk {@code projectRoot} for {@code *.nlsprops} files (English only —
 *       files that do not have a locale suffix such as {@code _de}, {@code _fr},
 *       etc.). Both the component's own {@code resources/} tree and any copies
 *       under {@code build.pii.package/} are included so that every message
 *       defined in the repository is indexed.</li>
 *   <li>Parse each nlsprops file, extracting:
 *       <ul>
 *         <li>{@code msg_key} — the property key (e.g. {@code SECURITY_SERVICE_READY})</li>
 *         <li>{@code msg_code} — the CWWKX-style code embedded at the start of the
 *             message text (e.g. {@code CWWKS0008I})</li>
 *         <li>{@code msg_text} — English message text (the part after the code + ": ")</li>
 *         <li>{@code explanation} — from {@code <key>.explanation} line, or {@code -}</li>
 *         <li>{@code useraction} — from {@code <key>.useraction} line, or {@code -}</li>
 *       </ul>
 *   </li>
 *   <li>For every Java source file under {@code projectRoot}, scan for
 *       {@code Tr.info}, {@code Tr.warning}, {@code Tr.error}, {@code Tr.fatal},
 *       and {@code Tr.audit} calls that pass a string literal matching a known
 *       message key. Collect {@code rel_path:line} for each hit.</li>
 *   <li>Write a single flat TSV:
 *       {@code msg_code TAB msg_key TAB msg_text TAB explanation TAB useraction TAB nlsprops_file TAB java_callers}
 *       sorted by msg_code then msg_key. {@code java_callers} is a
 *       semicolon-separated list of {@code rel_path:line} entries (or {@code -}).</li>
 * </ol>
 *
 * <p><b>Column summary (7 columns):</b>
 * <pre>
 * 1. msg_code      CWWKS0008I
 * 2. msg_key       SECURITY_SERVICE_READY
 * 3. msg_text      The security service is ready.
 * 4. explanation   This message is for informational purposes only.
 * 5. useraction    No action is required.
 * 6. nlsprops_file com.ibm.ws.security.ready.service/resources/.../SecurityReadyServiceMessages.nlsprops
 * 7. java_callers  com.ibm.ws.security.ready.service/src/.../SecurityReadyServiceImpl.java:223
 * </pre>
 */
public final class MtagsComp {

    private MtagsComp() {}

    /** Regex for a CW-style message code: letters, digits, then a severity letter. */
    private static final Pattern CODE_PATTERN =
        Pattern.compile("^(CW[A-Z0-9]{4,}[A-Z]):\\s*(.*)");

    /** Matches a locale suffix just before the .nlsprops extension: _de, _fr, _zh_TW, etc. */
    private static final Pattern LOCALE_SUFFIX =
        Pattern.compile("_[a-z]{2}(?:_[A-Z]{2})?$");

    /** Matches Tr.info/warning/error/fatal/audit calls with a string-literal key argument. */
    private static final Pattern TR_CALL =
        Pattern.compile("Tr\\.(?:info|warning|error|fatal|audit)\\s*\\([^,]+,\\s*\"([^\"]+)\"");

    /**
     * Scans {@code projectRoot} and writes the mtags index to {@code outFile}.
     */
    public static void run(File projectRoot, File outFile) throws IOException {
        String absRoot = projectRoot.getAbsolutePath();

        // ------------------------------------------------------------------ //
        // Step 1: collect nlsprops files (English only)
        // ------------------------------------------------------------------ //
        List<File> nlsFiles = findNlsFiles(projectRoot);

        // ------------------------------------------------------------------ //
        // Step 2: parse every nlsprops file → list of MessageRecord
        // ------------------------------------------------------------------ //
        // msg_key → MessageRecord  (first definition wins)
        Map<String, MessageRecord> byKey = new LinkedHashMap<>();
        // msg_code → MessageRecord (first definition wins)
        Map<String, MessageRecord> byCode = new TreeMap<>();

        for (File nls : nlsFiles) {
            String relNls = relativize(absRoot, nls);
            parseNlsProps(nls, relNls, byKey, byCode);
        }

        if (byCode.isEmpty()) {
            System.out.println("[mtags] No message codes found — skipping.");
            outFile.getParentFile().mkdirs();
            outFile.createNewFile();
            return;
        }

        // ------------------------------------------------------------------ //
        // Step 3: scan Java source for Tr call sites
        // ------------------------------------------------------------------ //
        Map<String, List<String>> callers = findCallers(projectRoot, absRoot, byKey.keySet());

        // ------------------------------------------------------------------ //
        // Step 4: write TSV
        // ------------------------------------------------------------------ //
        outFile.getParentFile().mkdirs();
        File tmp = new File(outFile.getAbsolutePath() + ".tmp");

        List<String> rows = new ArrayList<>();
        for (MessageRecord rec : byCode.values()) {
            List<String> sites = callers.getOrDefault(rec.msgKey, Collections.emptyList());
            String callersStr = sites.isEmpty() ? "-" : String.join(";", sites);
            rows.add(
                rec.msgCode     + "\t" +
                rec.msgKey      + "\t" +
                sanitize(rec.msgText)       + "\t" +
                sanitize(rec.explanation)   + "\t" +
                sanitize(rec.useraction)    + "\t" +
                rec.nlsFile     + "\t" +
                callersStr
            );
        }
        // already sorted by msg_code (byCode is a TreeMap)
        Files.write(tmp.toPath(), rows, StandardCharsets.UTF_8);
        Util.atomicMove(tmp, outFile);

        System.out.printf("[mtags] Indexed %,d message codes from %d nlsprops files%n",
            byCode.size(), nlsFiles.size());
    }

    // -----------------------------------------------------------------------
    // nlsprops discovery
    // -----------------------------------------------------------------------

    private static List<File> findNlsFiles(File root) throws IOException {
        try (Stream<Path> s = Files.walk(root.toPath())) {
            return s
                .filter(p -> p.toString().endsWith(".nlsprops"))
                .filter(p -> !p.toString().contains(File.separator + "build" + File.separator))
                .filter(p -> isEnglishNls(p.getFileName().toString()))
                .map(Path::toFile)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * Returns {@code true} if the filename is an English (base) nlsprops file —
     * i.e. it does not end with a locale suffix before {@code .nlsprops}.
     */
    static boolean isEnglishNls(String fileName) {
        if (!fileName.endsWith(".nlsprops")) return false;
        String stem = fileName.substring(0, fileName.length() - ".nlsprops".length());
        return !LOCALE_SUFFIX.matcher(stem).find();
    }

    // -----------------------------------------------------------------------
    // nlsprops parsing
    // -----------------------------------------------------------------------

    private static void parseNlsProps(File nls, String relNls,
                                       Map<String, MessageRecord> byKey,
                                       Map<String, MessageRecord> byCode) throws IOException {
        // Read all lines, joining continuation lines (ending with \)
        List<String> rawLines = readContinuationLines(nls);

        // Build a temporary key→value map for this file
        Map<String, String> props = new LinkedHashMap<>();
        for (String line : rawLines) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            props.put(k, v);
        }

        // Extract message records: only base keys that contain a CW code
        for (Map.Entry<String, String> e : props.entrySet()) {
            String key = e.getKey();
            // Skip explanation/useraction suffix lines
            if (key.endsWith(".explanation") || key.endsWith(".useraction")) continue;
            String value = e.getValue();

            Matcher m = CODE_PATTERN.matcher(value);
            if (!m.matches()) continue;

            String code    = m.group(1);
            String text    = m.group(2).trim();
            String expl    = props.getOrDefault(key + ".explanation", "-").trim();
            String action  = props.getOrDefault(key + ".useraction", "-").trim();

            // First definition wins
            if (byCode.containsKey(code)) continue;

            MessageRecord rec = new MessageRecord(code, key, text, expl, action, relNls);
            byKey.put(key, rec);
            byCode.put(code, rec);
        }
    }

    /**
     * Reads a properties file, joining continuation lines (lines ending with {@code \}).
     */
    private static List<String> readContinuationLines(File f) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.ISO_8859_1))) {
            StringBuilder current = null;
            String line;
            while ((line = r.readLine()) != null) {
                if (current == null) {
                    if (line.endsWith("\\")) {
                        current = new StringBuilder(line.substring(0, line.length() - 1));
                    } else {
                        result.add(line);
                    }
                } else {
                    String trimmed = line.stripLeading();
                    if (trimmed.endsWith("\\")) {
                        current.append(trimmed, 0, trimmed.length() - 1);
                    } else {
                        current.append(trimmed);
                        result.add(current.toString());
                        current = null;
                    }
                }
            }
            if (current != null) result.add(current.toString());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Java call-site scanning
    // -----------------------------------------------------------------------

    private static Map<String, List<String>> findCallers(File projectRoot, String absRoot,
                                                           Set<String> keys) throws IOException {
        Map<String, List<String>> callers = new HashMap<>();

        try (Stream<Path> s = Files.walk(projectRoot.toPath())) {
            s.filter(p -> p.toString().endsWith(".java"))
             .filter(p -> !p.toString().contains(File.separator + "build" + File.separator))
             .forEach(p -> {
                 try {
                     scanJavaFile(p.toFile(), absRoot, keys, callers);
                 } catch (IOException ignored) {}
             });
        }
        return callers;
    }

    private static void scanJavaFile(File javaFile, String absRoot,
                                      Set<String> keys,
                                      Map<String, List<String>> callers) throws IOException {
        String rel = relativize(absRoot, javaFile);
        List<String> lines = Files.readAllLines(javaFile.toPath(), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.contains("Tr.")) continue;
            Matcher m = TR_CALL.matcher(line);
            while (m.find()) {
                String key = m.group(1);
                if (keys.contains(key)) {
                    callers.computeIfAbsent(key, k -> new ArrayList<>())
                           .add(rel + ":" + (i + 1));
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String relativize(String absRoot, File file) {
        String abs = file.getAbsolutePath();
        String prefix = absRoot + File.separator;
        if (abs.startsWith(prefix)) return abs.substring(prefix.length()).replace('\\', '/');
        return abs.replace('\\', '/');
    }

    /** Replace embedded tabs/newlines so the TSV row stays on one line. */
    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "-";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // -----------------------------------------------------------------------
    // Inner record
    // -----------------------------------------------------------------------

    static final class MessageRecord {
        final String msgCode;
        final String msgKey;
        final String msgText;
        final String explanation;
        final String useraction;
        final String nlsFile;

        MessageRecord(String msgCode, String msgKey, String msgText,
                      String explanation, String useraction, String nlsFile) {
            this.msgCode     = msgCode;
            this.msgKey      = msgKey;
            this.msgText     = msgText;
            this.explanation = explanation;
            this.useraction  = useraction;
            this.nlsFile     = nlsFile;
        }
    }
}
