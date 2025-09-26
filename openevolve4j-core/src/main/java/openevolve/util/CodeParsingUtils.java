package openevolve.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for code parsing, diffing, and manipulation
 */
public class CodeParsingUtils {

    /**
     * Represents a diff block with search and replace text
     */
    public static class DiffBlock {
        private final String searchText;
        private final String replaceText;
        private final String file;

        public DiffBlock(String file, String searchText, String replaceText) {
            this.file = file;
            this.searchText = searchText;
            this.replaceText = replaceText;
        }

        public String getFile() {
            return file;
        }

        public String getSearchText() {
            return searchText;
        }

        public String getReplaceText() {
            return replaceText;
        }
    }

    /**
     * Apply a diff to the original code
     *
     * @param originalCode Original source code
     * @param diffText Diff in the SEARCH/REPLACE format
     * @return Modified code
     */
    public static String applyDiff(String originalCode, List<DiffBlock> diffBlocks) {
        if (diffBlocks == null || diffBlocks.isEmpty()) {
            return originalCode;
        }

        // Preserve original line ending style if CRLF was present
        boolean hadCRLF = originalCode.contains("\r\n");

        // Normalize to \n for consistent processing
        String content = normalizeNewlines(originalCode);

        for (DiffBlock diffBlock : diffBlocks) {
            String searchText = diffBlock.getSearchText() == null ? ""
                    : normalizeNewlines(diffBlock.getSearchText());
            String replaceText = diffBlock.getReplaceText() == null ? ""
                    : normalizeNewlines(diffBlock.getReplaceText());

            if (searchText.isEmpty()) {
                // Nothing to match; skip this block
                continue;
            }

            // 1) Exact substring replacement (first occurrence) for multi-line blocks
            Optional<String> updated = tryMultiLineExact(content, searchText, replaceText);
            if (updated.isPresent()) {
                content = updated.get();
                continue;
            }

            // 1b) Single-line per-line replacement: drop indentation if line equals trimmed
            updated = trySingleLinePerLine(content, searchText, replaceText);
            if (updated.isPresent()) {
                content = updated.get();
                continue;
            }

            // 2) Contiguous line-based replacement allowing trailing whitespace differences only
            String[] contentLinesArr = content.split("\n", -1);
            List<String> contentLines = new ArrayList<>();
            for (String l : contentLinesArr)
                contentLines.add(l);
            String[] searchLines = searchText.split("\n", -1);
            String[] replaceLines = replaceText.split("\n", -1);

            updated = tryContiguousWindow(contentLines, searchLines, replaceLines);
            if (updated.isPresent()) {
                content = updated.get();
                continue;
            }

            // 3) Ordered (non-contiguous) match on trimmed lines: remove all matched lines and
            // insert replacement
            updated = tryOrderedNonContiguous(contentLines, searchLines, replaceLines);
            if (updated.isPresent()) {
                content = updated.get();
                continue;
            }

            // 4) Anchor-based large block replacement: find first and last matching non-empty
            // trimmed lines
            updated = tryAnchorBased(contentLines, searchLines, replaceLines);
            if (updated.isPresent()) {
                content = updated.get();
                continue;
            }

            // If no replacement, leave block as-is
        }

        // Restore CRLF if the original contained it
        return restoreOriginalNewlines(content, hadCRLF);
    }

    private static Optional<String> tryMultiLineExact(String content, String searchText,
            String replaceText) {
        if (searchText.indexOf('\n') >= 0) {
            int at = content.indexOf(searchText);
            if (at >= 0) {
                String out = content.substring(0, at) + replaceText
                        + content.substring(at + searchText.length());
                return Optional.of(out);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> trySingleLinePerLine(String content, String searchText,
            String replaceText) {
        if (searchText.indexOf('\n') < 0) {
            String[] linesArr = content.split("\n", -1);
            for (int li = 0; li < linesArr.length; li++) {
                String line = linesArr[li];
                int pos = line.indexOf(searchText);
                if (pos >= 0) {
                    if (line.trim().equals(searchText.trim())) {
                        linesArr[li] = replaceText;
                    } else {
                        linesArr[li] = line.replace(searchText, replaceText);
                    }
                    return Optional.of(String.join("\n", linesArr));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> tryContiguousWindow(List<String> contentLines,
            String[] searchLines, String[] replaceLines) {
        int win = searchLines.length;
        for (int i = 0; i <= contentLines.size() - win; i++) {
            boolean allMatch = true;
            for (int j = 0; j < win; j++) {
                String a = rtrim(contentLines.get(i + j));
                String b = rtrim(searchLines[j]);
                if (!a.equals(b)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                List<String> outLines = new ArrayList<>(contentLines);
                for (int k = 0; k < win; k++)
                    outLines.remove(i);
                for (int k = 0; k < replaceLines.length; k++)
                    outLines.add(i + k, replaceLines[k]);
                return Optional.of(String.join("\n", outLines));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> tryOrderedNonContiguous(List<String> contentLines,
            String[] searchLines, String[] replaceLines) {
        List<Integer> matchedIndices = new ArrayList<>();
        int startIdx = 0;
        for (String sLine : searchLines) {
            String sTrim = sLine.trim();
            int foundAt = -1;
            for (int i = startIdx; i < contentLines.size(); i++) {
                if (contentLines.get(i).trim().equals(sTrim)) {
                    foundAt = i;
                    break;
                }
            }
            if (foundAt == -1) {
                matchedIndices.clear();
                break;
            }
            matchedIndices.add(foundAt);
            startIdx = foundAt + 1;
        }

        if (!matchedIndices.isEmpty() && matchedIndices.size() == searchLines.length) {
            List<String> outLines = new ArrayList<>(contentLines);
            for (int k = matchedIndices.size() - 1; k >= 0; k--) {
                int idx = matchedIndices.get(k);
                outLines.remove(idx);
            }
            int insertPos = matchedIndices.get(0);
            for (int j = 0; j < replaceLines.length; j++) {
                outLines.add(insertPos + j, replaceLines[j]);
            }
            return Optional.of(String.join("\n", outLines));
        }
        return Optional.empty();
    }

    private static Optional<String> tryAnchorBased(List<String> contentLines, String[] searchLines,
            String[] replaceLines) {
        if (searchLines.length >= 10) { // modest threshold to avoid tiny accidental edits
            int firstAnchor = -1;
            for (String sLine : searchLines) {
                String probe = sLine.trim();
                if (probe.isEmpty())
                    continue;
                for (int i = 0; i < contentLines.size(); i++) {
                    if (contentLines.get(i).trim().equals(probe)) {
                        firstAnchor = i;
                        break;
                    }
                }
                if (firstAnchor != -1)
                    break;
            }
            if (firstAnchor != -1) {
                int lastAnchor = -1;
                for (int si = searchLines.length - 1; si >= 0; si--) {
                    String probe = searchLines[si].trim();
                    if (probe.isEmpty())
                        continue;
                    for (int i = contentLines.size() - 1; i >= firstAnchor; i--) {
                        if (contentLines.get(i).trim().equals(probe)) {
                            lastAnchor = i;
                            break;
                        }
                    }
                    if (lastAnchor != -1)
                        break;
                }
                if (lastAnchor != -1 && lastAnchor >= firstAnchor
                        && (lastAnchor - firstAnchor) >= 5) {
                    List<String> outLines = new ArrayList<>(contentLines);
                    for (int i = lastAnchor; i >= firstAnchor; i--)
                        outLines.remove(i);
                    for (int j = 0; j < replaceLines.length; j++)
                        outLines.add(firstAnchor + j, replaceLines[j]);
                    return Optional.of(String.join("\n", outLines));
                }
            }
        }
        return Optional.empty();
    }

    private static String normalizeNewlines(String s) {
        return s.replace("\r\n", "\n");
    }

    private static String restoreOriginalNewlines(String s, boolean hadCRLF) {
        return hadCRLF ? s.replace("\n", "\r\n") : s;
    }

    private static String rtrim(String s) {
        if (s == null || s.isEmpty())
            return s == null ? "" : s;
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                i--;
            } else {
                break;
            }
        }
        return s.substring(0, i + 1);
    }

    /**
     * Extract diff blocks from the diff text
     *
     * @param diffText Diff in the SEARCH/REPLACE format
     * @return List of diff blocks
     */
    public static List<DiffBlock> extractChanges(String llmResponse, List<String> paths) {
        List<DiffBlock> diffBlocks = new ArrayList<>();

        // Quote each path to avoid regex metacharacters interfering (e.g., dots)
        List<String> quoted = new ArrayList<>();
        for (String p : paths) {
            quoted.add(Pattern.quote(p));
        }
        String pathString = "(" + String.join("|", quoted) + ")";

        Pattern diffPattern = Pattern.compile(pathString + // file path (captured)
                "\\s*<<<<<<<\\s*.*?\\R" + // <<<<<<< (with optional label)
                "(.*?)\\R" + // search block (group 2, multiline)
                "=======\\s*\\R" + // ======= divider
                "(.*?)\\R" + // replace block (group 3, multiline)
                ">>>>>>>", // >>>>>>> (with optional label)
                Pattern.DOTALL);

        Matcher matcher = diffPattern.matcher(llmResponse);
        while (matcher.find()) {
            String file = matcher.group(1);
            String searchText = matcher.group(2); // search section
            String replaceText = matcher.group(3); // replace section
            diffBlocks.add(new DiffBlock(file, searchText, replaceText));
        }

        return diffBlocks;
    }

    public static Optional<String> parseFullRewrite(String llmResponse, String language) {
        // Try language-specific code block first
        Pattern codeBlockPattern =
                Pattern.compile("```" + language + "\\n(.*?)```", Pattern.DOTALL);

        Matcher matcher = codeBlockPattern.matcher(llmResponse);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).strip());
        }

        // Fallback to any code block
        codeBlockPattern = Pattern.compile("```.*?\\n(.*?)```", Pattern.DOTALL);
        matcher = codeBlockPattern.matcher(llmResponse);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).strip());
        }

        // Fallback to plain text
        return Optional.of(llmResponse);
    }
}
