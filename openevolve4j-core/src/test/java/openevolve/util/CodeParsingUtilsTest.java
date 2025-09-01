package openevolve.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests for code utilities in openevolve.util.CodeParsingUtils
 */
public class CodeParsingUtilsTest {

    @Test
    void testExtractDiffs() {
        // Test extracting diffs from a response
        String diffText = """
        Let's improve this code:

        <<<<<<< SEARCH
        def hello():
            print("Hello")
        =======
        def hello():
            print("Hello, World!")
        >>>>>>> REPLACE

        Another change:

        <<<<<<< SEARCH
        x = 1
        =======
        x = 2
        >>>>>>> REPLACE
        """;

        List<CodeParsingUtils.DiffBlock> diffs = CodeParsingUtils.extractDiffs(diffText);
        assertEquals(2, diffs.size());
        
        assertEquals(
            "def hello():\n    print(\"Hello\")",
            diffs.get(0).getSearchText()
        );
        assertEquals(
            "def hello():\n    print(\"Hello, World!\")",
            diffs.get(0).getReplaceText()
        );
        assertEquals("x = 1", diffs.get(1).getSearchText());
        assertEquals("x = 2", diffs.get(1).getReplaceText());
    }

    @Test
    void testApplyDiff() {
        // Test applying diffs to code
        String originalCode = """
        def hello():
            print("Hello")

        x = 1
        y = 2
        """;

        String diffText = """
        <<<<<<< SEARCH
        def hello():
            print("Hello")
        =======
        def hello():
            print("Hello, World!")
        >>>>>>> REPLACE

        <<<<<<< SEARCH
        x = 1
        =======
        x = 2
        >>>>>>> REPLACE
        """;

        String expectedCode = """
        def hello():
            print("Hello, World!")

        x = 2
        y = 2
        """;

        String result = CodeParsingUtils.applyDiff(originalCode, diffText);
        assertEquals(expectedCode.trim(), result.trim());
    }

    @Test
    void testApplyDiffJavaCode() {
        // Test applying diffs to Java code
        String originalCode = """
        public class Hello {
            public void sayHello() {
                System.out.println("Hello");
            }
            
            private int x = 1;
        }""";

        String diffText = """
        <<<<<<< SEARCH
            public void sayHello() {
                System.out.println("Hello");
            }
        =======
            public void sayHello() {
                System.out.println("Hello, World!");
            }
        >>>>>>> REPLACE

        <<<<<<< SEARCH
            private int x = 1;
        =======
            private int x = 2;
        >>>>>>> REPLACE
        """;

        String expectedCode = """
        public class Hello {
            public void sayHello() {
                System.out.println("Hello, World!");
            }
            
            private int x = 2;
        }""";

        String result = CodeParsingUtils.applyDiff(originalCode, diffText);
        assertEquals(expectedCode.trim(), result.trim());
    }

    @Test
    void testExtractDiffsNoMatches() {
        // Test extracting diffs when there are no SEARCH/REPLACE blocks
        String diffText = "This is just regular text without any diff blocks.";
        
        List<CodeParsingUtils.DiffBlock> diffs = CodeParsingUtils.extractDiffs(diffText);
        assertEquals(0, diffs.size());
    }

    @Test
    void testApplyDiffNoMatches() {
        // Test applying diff when search text doesn't match
        String originalCode = """
        def hello():
            print("Hello")
        """;

        String diffText = """
        <<<<<<< SEARCH
        def goodbye():
            print("Goodbye")
        =======
        def goodbye():
            print("Goodbye, World!")
        >>>>>>> REPLACE
        """;

        String result = CodeParsingUtils.applyDiff(originalCode, diffText);
        // Should return original code unchanged when no matches found
        assertEquals(originalCode.trim(), result.trim());
    }

    @Test
    void testParseFullRewriteWithLanguage() {
        // Test extracting full rewrite with language-specific code block
        String llmResponse = """
        Here's the improved Java code:
        
        ```java
        public class Improved {
            public void method() {
                System.out.println("Improved!");
            }
        }
        ```
        
        This is better because...
        """;

        Optional<String> result = CodeParsingUtils.parseFullRewrite(llmResponse, "java");
        assertTrue(result.isPresent());
        assertEquals("""
        public class Improved {
            public void method() {
                System.out.println("Improved!");
            }
        }""", result.get());
    }

    @Test
    void testParseFullRewriteGenericCodeBlock() {
        // Test extracting full rewrite with generic code block
        String llmResponse = """
        Here's the code:
        
        ```
        function hello() {
            console.log("Hello!");
        }
        ```
        """;

        Optional<String> result = CodeParsingUtils.parseFullRewrite(llmResponse, "javascript");
        assertTrue(result.isPresent());
        assertEquals("""
        function hello() {
            console.log("Hello!");
        }""", result.get());
    }

    @Test
    void testParseFullRewriteFallbackToPlainText() {
        // Test fallback to plain text when no code blocks found
        String llmResponse = "This is just plain text without code blocks.";

        Optional<String> result = CodeParsingUtils.parseFullRewrite(llmResponse, "java");
        assertTrue(result.isPresent());
        assertEquals("This is just plain text without code blocks.", result.get());
    }

    @Test
    void testFormatDiffSummary() {
        // Test creating human-readable summary of diffs
        List<CodeParsingUtils.DiffBlock> diffBlocks = List.of(
            new CodeParsingUtils.DiffBlock("old line", "new line"),
            new CodeParsingUtils.DiffBlock(
                "multiple\nold\nlines", 
                "multiple\nnew\nlines"
            )
        );

        String summary = CodeParsingUtils.formatDiffSummary(diffBlocks);
        assertTrue(summary.contains("Change 1: 'old line' to 'new line'"));
        assertTrue(summary.contains("Change 2: Replace 3 lines with 3 lines"));
    }

    @Test
    void testCalculateEditDistance() {
        // Test Levenshtein edit distance calculation
        assertEquals(0, CodeParsingUtils.calculateEditDistance("hello", "hello"));
        assertEquals(1, CodeParsingUtils.calculateEditDistance("hello", "helo"));
        assertEquals(2, CodeParsingUtils.calculateEditDistance("hello", "help"));
        assertEquals(5, CodeParsingUtils.calculateEditDistance("", "hello"));
        assertEquals(5, CodeParsingUtils.calculateEditDistance("hello", ""));
    }

    @Test
    void testExtractCodeLanguage() {
        // Test language detection
        String pythonCode = """
        import sys
        def main():
            print("Hello")
        """;
        assertEquals("python", CodeParsingUtils.extractCodeLanguage(pythonCode));

        String javaCode = """
        package com.example;
        public class Test {
            public static void main(String[] args) {}
        }
        """;
        assertEquals("java", CodeParsingUtils.extractCodeLanguage(javaCode));

        String jsCode = """
        function test() {
            console.log("test");
        }
        """;
        assertEquals("javascript", CodeParsingUtils.extractCodeLanguage(jsCode));

        String unknownCode = "This is not code";
        assertEquals("unknown", CodeParsingUtils.extractCodeLanguage(unknownCode));
    }

    @Test
    void testCountLinesOfCode() {
        // Test counting lines of code (excluding comments and empty lines)
        String code = """
        // This is a comment
        public class Test {
            
            public void method() {
                System.out.println("Hello"); // inline comment
            }
            
            /*
             * Block comment
             */
        }
        """;

        int count = CodeParsingUtils.countLinesOfCode(code);
        assertEquals(5, count); // Lines: class declaration, method declaration, println, method close, class close
    }

    @Test
    void testExtractImportsJava() {
        // Test extracting imports from Java code
        String javaCode = """
        package com.example;
        
        import java.util.List;
        import java.util.ArrayList;
        import static org.junit.Assert.*;
        
        public class Test {
            // class content
        }
        """;

        List<String> imports = CodeParsingUtils.extractImports(javaCode, "java");
        assertEquals(3, imports.size());
        assertTrue(imports.contains("import java.util.List;"));
        assertTrue(imports.contains("import java.util.ArrayList;"));
        assertTrue(imports.contains("import static org.junit.Assert.*;"));
    }

    @Test
    void testExtractImportsPython() {
        // Test extracting imports from Python code
        String pythonCode = """
        import os
        import sys
        from datetime import datetime
        from typing import List, Dict
        
        def main():
            pass
        """;

        List<String> imports = CodeParsingUtils.extractImports(pythonCode, "python");
        assertEquals(4, imports.size());
        assertTrue(imports.contains("import os"));
        assertTrue(imports.contains("import sys"));
        assertTrue(imports.contains("from datetime import datetime"));
        assertTrue(imports.contains("from typing import List, Dict"));
    }

    @Test
    void testParseEvolveBlocks() {
        // Test parsing evolve blocks from code
        String codeWithBlocks = """
        public class Test {
            // EVOLVE-BLOCK-START
            public void oldMethod() {
                System.out.println("old");
            }
            // EVOLVE-BLOCK-END
            
            public void regularMethod() {
                System.out.println("regular");
            }
            
            // EVOLVE-BLOCK-START
            private int x = 1;
            // EVOLVE-BLOCK-END
        }
        """;

        List<CodeParsingUtils.EvolveBlock> blocks = CodeParsingUtils.parseEvolveBlocks(codeWithBlocks);
        assertEquals(2, blocks.size());
        
        CodeParsingUtils.EvolveBlock firstBlock = blocks.get(0);
        assertTrue(firstBlock.getContent().contains("public void oldMethod()"));
        
        CodeParsingUtils.EvolveBlock secondBlock = blocks.get(1);
        assertTrue(secondBlock.getContent().contains("private int x = 1;"));
    }

    @Test
    void testParseEvolveBlocksPython() {
        // Test parsing evolve blocks from Python code with # comments
        String pythonCodeWithBlocks = """
        class Test:
            # EVOLVE-BLOCK-START
            def old_method(self):
                print("old")
            # EVOLVE-BLOCK-END
            
            def regular_method(self):
                print("regular")
        """;

        List<CodeParsingUtils.EvolveBlock> blocks = CodeParsingUtils.parseEvolveBlocks(pythonCodeWithBlocks);
        assertEquals(1, blocks.size());
        
        CodeParsingUtils.EvolveBlock block = blocks.get(0);
        assertTrue(block.getContent().contains("def old_method(self):"));
    }
}
