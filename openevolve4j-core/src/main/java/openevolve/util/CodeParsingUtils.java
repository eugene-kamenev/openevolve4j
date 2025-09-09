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
     * Represents an evolve block with start line, end line, and content
     */
    public static class EvolveBlock {
        private final int startLine;
        private final int endLine;
        private final String content;

        public EvolveBlock(int startLine, int endLine, String content) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.content = content;
        }

        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getContent() { return content; }
    }

    /**
     * Represents a diff block with search and replace text
     */
    public static class DiffBlock {
        private final String searchText;
        private final String replaceText;

        public DiffBlock(String searchText, String replaceText) {
            this.searchText = searchText;
            this.replaceText = replaceText;
        }

        public String getSearchText() { return searchText; }
        public String getReplaceText() { return replaceText; }
    }

    /**
     * Parse evolve blocks from code
     *
     * @param code Source code with evolve blocks
     * @return List of evolve blocks
     */
    public static List<EvolveBlock> parseEvolveBlocks(String code) {
        String[] lines = code.split("\n");
        List<EvolveBlock> blocks = new ArrayList<>();

        boolean inBlock = false;
        int startLine = -1;
        List<String> blockContent = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            if (line.contains("# EVOLVE-BLOCK-START") || line.contains("// EVOLVE-BLOCK-START")) {
                inBlock = true;
                startLine = i;
                blockContent.clear();
            } else if ((line.contains("# EVOLVE-BLOCK-END") || line.contains("// EVOLVE-BLOCK-END")) && inBlock) {
                inBlock = false;
                blocks.add(new EvolveBlock(startLine, i, String.join("\n", blockContent)));
            } else if (inBlock) {
                blockContent.add(line);
            }
        }

        return blocks;
    }

    /**
     * Apply a diff to the original code
     *
     * @param originalCode Original source code
     * @param diffText Diff in the SEARCH/REPLACE format
     * @return Modified code
     */
    public static String applyDiff(String originalCode, String diffText) {
        String[] originalLines = originalCode.split("\n");
        List<String> resultLines = new ArrayList<>();
        for (String line : originalLines) {
            resultLines.add(line);
        }

        List<DiffBlock> diffBlocks = extractDiffs(diffText);

        for (DiffBlock diffBlock : diffBlocks) {
            String[] searchLines = diffBlock.getSearchText().split("\n");
            String[] replaceLines = diffBlock.getReplaceText().split("\n");

            // Find where the search pattern starts in the original code
            for (int i = 0; i <= resultLines.size() - searchLines.length; i++) {
                boolean matches = true;
                for (int j = 0; j < searchLines.length; j++) {
                    if (!resultLines.get(i + j).equals(searchLines[j])) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    // Replace the matched section
                    for (int j = 0; j < searchLines.length; j++) {
                        resultLines.remove(i);
                    }
                    for (int j = 0; j < replaceLines.length; j++) {
                        resultLines.add(i + j, replaceLines[j]);
                    }
                    break;
                }
            }
        }

        return String.join("\n", resultLines);
    }

    /**
     * Extract diff blocks from the diff text
     *
     * @param diffText Diff in the SEARCH/REPLACE format
     * @return List of diff blocks
     */
    public static List<DiffBlock> extractDiffs(String diffText) {
        List<DiffBlock> diffBlocks = new ArrayList<>();
        
        Pattern diffPattern = Pattern.compile(
            "<<<<<<< SEARCH\\n(.*?)=======\\n(.*?)>>>>>>> REPLACE",
            Pattern.DOTALL
        );
        
        Matcher matcher = diffPattern.matcher(diffText);
        while (matcher.find()) {
            String searchText = matcher.group(1).replaceAll("\\s+$", "");
            String replaceText = matcher.group(2).replaceAll("\\s+$", "");
            diffBlocks.add(new DiffBlock(searchText, replaceText));
        }

        return diffBlocks;
    }

    /**
     * Extract a full rewrite from an LLM response
     *
     * @param llmResponse Response from the LLM
     * @param language Programming language
     * @return Extracted code or empty if not found
     */
    public static Optional<String> parseFullRewrite(String llmResponse, String language) {
        // Try language-specific code block first
        Pattern codeBlockPattern = Pattern.compile(
            "```" + language + "\\n(.*?)```",
            Pattern.DOTALL
        );
        
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

    /**
     * Create a human-readable summary of the diff
     *
     * @param diffBlocks List of diff blocks
     * @return Summary string
     */
    public static String formatDiffSummary(List<DiffBlock> diffBlocks) {
        List<String> summary = new ArrayList<>();

        for (int i = 0; i < diffBlocks.size(); i++) {
            DiffBlock diffBlock = diffBlocks.get(i);
            String[] searchLines = diffBlock.getSearchText().strip().split("\n");
            String[] replaceLines = diffBlock.getReplaceText().strip().split("\n");

            String changeSummary;
            if (searchLines.length == 1 && replaceLines.length == 1) {
                changeSummary = String.format(
                    "Change %d: '%s' to '%s'",
                    i + 1, searchLines[0], replaceLines[0]
                );
            } else {
                String searchSummary = searchLines.length > 1 ?
                    searchLines.length + " lines" : searchLines[0];
                String replaceSummary = replaceLines.length > 1 ?
                    replaceLines.length + " lines" : replaceLines[0];
                changeSummary = String.format(
                    "Change %d: Replace %s with %s",
                    i + 1, searchSummary, replaceSummary
                );
            }
            summary.add(changeSummary);
        }

        return String.join("\n", summary);
    }

    /**
     * Try to determine the language of a code snippet
     *
     * @param code Code snippet
     * @return Detected language or "unknown"
     */
    public static String extractCodeLanguage(String code) {
        // Python patterns
        if (Pattern.compile("^(import|from|def|class)\\s", Pattern.MULTILINE).matcher(code).find()) {
            return "python";
        }
        
        // Java patterns
        if (Pattern.compile("^(package|import java|public class)", Pattern.MULTILINE).matcher(code).find()) {
            return "java";
        }
        
        // C/C++ patterns
        if (Pattern.compile("^(#include|int main|void main)", Pattern.MULTILINE).matcher(code).find()) {
            return "cpp";
        }
        
        // JavaScript patterns
        if (Pattern.compile("^(function|var|let|const|console\\.log)", Pattern.MULTILINE).matcher(code).find()) {
            return "javascript";
        }
        
        // Rust patterns
        if (Pattern.compile("^(module|fn|let mut|impl)", Pattern.MULTILINE).matcher(code).find()) {
            return "rust";
        }
        
        // SQL patterns
        if (Pattern.compile("^(SELECT|CREATE TABLE|INSERT INTO)", Pattern.MULTILINE).matcher(code).find()) {
            return "sql";
        }

        return "unknown";
    }

    /**
     * Count lines of code (excluding empty lines and comments)
     *
     * @param code Source code
     * @return Number of non-empty, non-comment lines
     */
    public static int countLinesOfCode(String code) {
        String[] lines = code.split("\n");
        int count = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("#") &&
                !trimmed.startsWith("*") &&
                !trimmed.equals("/*") &&
                !trimmed.equals("*/")) {
                count++;
            }
        }
        
        return count;
    }

    /**
     * Extract imports/dependencies from code
     *
     * @param code Source code
     * @param language Programming language
     * @return List of import statements
     */
    public static List<String> extractImports(String code, String language) {
        List<String> imports = new ArrayList<>();
        String[] lines = code.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            switch (language.toLowerCase()) {
                case "python":
                    if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                        imports.add(trimmed);
                    }
                    break;
                case "java":
                    if (trimmed.startsWith("import ")) {
                        imports.add(trimmed);
                    }
                    break;
                case "javascript":
                    if (trimmed.startsWith("import ") || trimmed.contains("require(")) {
                        imports.add(trimmed);
                    }
                    break;
                case "cpp":
                    if (trimmed.startsWith("#include")) {
                        imports.add(trimmed);
                    }
                    break;
            }
        }
        
        return imports;
    }
}
