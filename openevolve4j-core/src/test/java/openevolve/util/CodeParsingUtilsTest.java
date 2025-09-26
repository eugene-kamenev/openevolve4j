package openevolve.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

public class CodeParsingUtilsTest {
	
    @Test
    public void testExtractChanges_SingleChange() {
        String llmResponse = """
            src/main.java
            <<<<<<< SEARCH
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }
            =======
            public class MyClass {
                public void newMethod() {
                    System.out.println("new");
                }
            }
            >>>>>>> REPLACE
            """;
        
        List<String> paths = Arrays.asList("src/main.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertEquals(1, result.size());
        CodeParsingUtils.DiffBlock block = result.get(0);
        assertEquals("src/main.java", block.getFile());
        assertEquals("public class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
                     block.getSearchText());
        assertEquals("public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}",
                     block.getReplaceText());
    }

    @Test
    public void testExtractChanges_MultipleChanges() {
        String llmResponse = """
            src/main.java
            <<<<<<< SEARCH
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }
            =======
            public class MyClass {
                public void newMethod() {
                    System.out.println("new");
                }
            }
            >>>>>>> REPLACE
            
            src/other.java
            <<<<<<< SEARCH
            int x = 10;
            =======
            int x = 20;
            >>>>>>> REPLACE
            """;
        
        List<String> paths = Arrays.asList("src/main.java", "src/other.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertEquals(2, result.size());
        
        // First block
        CodeParsingUtils.DiffBlock block1 = result.get(0);
        assertEquals("src/main.java", block1.getFile());
        assertEquals("public class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
                     block1.getSearchText());
        
        // Second block
        CodeParsingUtils.DiffBlock block2 = result.get(1);
        assertEquals("src/other.java", block2.getFile());
        assertEquals("int x = 10;", block2.getSearchText());
        assertEquals("int x = 20;", block2.getReplaceText());
    }

    @Test
    public void testExtractChanges_NoMatches() {
        String llmResponse = """
            Some other text without any diff blocks.
            """;
        
        List<String> paths = Arrays.asList("src/main.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertTrue(result.isEmpty());
    }

    @Test
    public void testApplyDiff_SingleChange() {
        String originalCode = """
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }
            """;
        
        CodeParsingUtils.DiffBlock block = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "public class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
            "public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}"
        );
        
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList(block);
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        
        assertEquals("public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}",
                     result.strip());
    }

    @Test
    public void testApplyDiff_MultipleChanges() {
        String originalCode = """
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
                
                int x = 10;
            }
            """;
        
        CodeParsingUtils.DiffBlock block1 = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "public class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
            "public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}"
        );
        
        CodeParsingUtils.DiffBlock block2 = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "int x = 10;",
            "int x = 20;"
        );
        
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList(block1, block2);
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        
        assertEquals("public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}\n\nint x = 20;",
                     result.strip());
    }

    @Test
    public void testApplyDiff_EmptyDiffList() {
        String originalCode = "public class MyClass { }";
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList();
        
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        assertEquals("public class MyClass { }", result.strip());
    }

    @Test
    public void testApplyDiff_NullDiffList() {
        String originalCode = "public class MyClass { }";
        
        String result = CodeParsingUtils.applyDiff(originalCode, null);
        assertEquals("public class MyClass { }", result.strip());
    }
    
    // Additional test cases
    
    @Test
    public void testExtractChanges_WithDifferentFilePattern() {
        String llmResponse = """
            src/test/MyClass.java
            <<<<<<< SEARCH
            public class MyClass {
                private int value;
            }
            =======
            public class MyClass {
                private int value;
                private String name;
            }
            >>>>>>> REPLACE
            """;
        
        List<String> paths = Arrays.asList("src/test/MyClass.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertEquals(1, result.size());
        CodeParsingUtils.DiffBlock block = result.get(0);
        assertEquals("src/test/MyClass.java", block.getFile());
        assertEquals("public class MyClass {\n    private int value;\n}",
                     block.getSearchText());
        assertEquals("public class MyClass {\n    private int value;\n    private String name;\n}",
                     block.getReplaceText());
    }
    
    @Test
    public void testExtractChanges_MultipleFilesWithSamePattern() {
        String llmResponse = """
            src/A.java
            <<<<<<< SEARCH
            class A { }
            =======
            class A {
                int value;
            }
            >>>>>>> REPLACE
            
            src/B.java
            <<<<<<< SEARCH
            class B { }
            =======
            class B {
                int value;
            }
            >>>>>>> REPLACE
            """;
        
        List<String> paths = Arrays.asList("src/A.java", "src/B.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertEquals(2, result.size());
        assertEquals("src/A.java", result.get(0).getFile());
        assertEquals("src/B.java", result.get(1).getFile());
    }
    
    @Test
    public void testExtractChanges_WithExtraWhitespace() {
        String llmResponse = """
            src/main.java
            
            
            <<<<<<< SEARCH
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }
            =======
            public class MyClass {
                public void newMethod() {
                    System.out.println("new");
                }
            }
            >>>>>>> REPLACE
            """;
        
        List<String> paths = Arrays.asList("src/main.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);
        
        assertEquals(1, result.size());
        CodeParsingUtils.DiffBlock block = result.get(0);
        assertEquals("src/main.java", block.getFile());
    }
    
    @Test
    public void testApplyDiff_ReplaceAtEndOfFile() {
        String originalCode = """
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }""";
        
        CodeParsingUtils.DiffBlock block = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "public class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
            "public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}"
        );
        
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList(block);
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        
        assertEquals("public class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}",
                     result.strip());
    }
    
    @Test
    public void testApplyDiff_ReplaceAtBeginningOfFile() {
        String originalCode = """
            // This is a comment
            public class MyClass {
                public void oldMethod() {
                    System.out.println("old");
                }
            }""";
        
        CodeParsingUtils.DiffBlock block = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "// This is a comment\npublic class MyClass {\n    public void oldMethod() {\n        System.out.println(\"old\");\n    }\n}",
            "// This is a comment\npublic class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}"
        );
        
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList(block);
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        
        assertEquals("// This is a comment\npublic class MyClass {\n    public void newMethod() {\n        System.out.println(\"new\");\n    }\n}",
                     result.strip());
    }
    
    @Test
    public void testApplyDiff_SingleLineReplacements() {
        String originalCode = """
            int x = 10;
            int y = 20;""";
        
        CodeParsingUtils.DiffBlock block1 = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "int x = 10;",
            "int x = 30;"
        );
        
        CodeParsingUtils.DiffBlock block2 = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "int y = 20;",
            "int y = 40;"
        );
        
        List<CodeParsingUtils.DiffBlock> diffBlocks = Arrays.asList(block1, block2);
        String result = CodeParsingUtils.applyDiff(originalCode, diffBlocks);
        
        assertEquals("int x = 30;\nint y = 40;", result.strip());
    }
    
    @Test
    public void testParseFullRewrite_LanguageSpecificBlock() {
        String llmResponse = """
            Some text before.
            ```java
            public class TestClass {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            ```
            Some text after.""";
        
        String result = CodeParsingUtils.parseFullRewrite(llmResponse, "java").orElse("");
        
        assertEquals("public class TestClass {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}",
                     result.strip());
    }
    
    @Test
    public void testParseFullRewrite_AnyCodeBlock() {
        String llmResponse = """
            Some text before.
            ```python
            def hello():
                print("Hello")
            ```
            Some text after.""";
        
        String result = CodeParsingUtils.parseFullRewrite(llmResponse, "java").orElse("");
        
        assertEquals("def hello():\n    print(\"Hello\")", result.strip());
    }
    
    @Test
    public void testParseFullRewrite_NoCodeBlock() {
        String llmResponse = """
            Some text without code blocks.
            Just plain text here.
            """;
        
        String result = CodeParsingUtils.parseFullRewrite(llmResponse, "java").orElse("");
        
        assertEquals("Some text without code blocks.\nJust plain text here.", result.strip());
    }

    // New additional test cases

    @Test
    public void testExtractChanges_IgnoresUnknownPaths() {
        String llmResponse = """
            src/keep.java
            <<<<<<< SEARCH
            class Keep { }
            =======
            class Keep { int x; }
            >>>>>>> REPLACE

            src/ignore.java
            <<<<<<< SEARCH
            class Ignore { }
            =======
            class Ignore { int y; }
            >>>>>>> REPLACE
            """;

        List<String> paths = Arrays.asList("src/keep.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);

        assertEquals(1, result.size());
        assertEquals("src/keep.java", result.get(0).getFile());
        assertEquals("class Keep { }", result.get(0).getSearchText());
        assertEquals("class Keep { int x; }", result.get(0).getReplaceText());
    }

    @Test
    public void testExtractChanges_CRLFLineEndings() {
        String llmResponse =
            "src/main.java\r\n" +
            "<<<<<<< SEARCH\r\n" +
            "int a = 1;\r\n" +
            "=======\r\n" +
            "int a = 2;\r\n" +
            ">>>>>>> REPLACE\r\n";

        List<String> paths = Arrays.asList("src/main.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);

        assertEquals(1, result.size());
        assertEquals("src/main.java", result.get(0).getFile());
        assertEquals("int a = 1;", result.get(0).getSearchText());
        assertEquals("int a = 2;", result.get(0).getReplaceText());
    }

    @Test
    public void testApplyDiff_NoMatchLeavesOriginal() {
        String originalCode = """
            class Demo {
                void f() {}
            }
            """;

        CodeParsingUtils.DiffBlock block = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "void g() {}",
            "void h() {}"
        );

        String result = CodeParsingUtils.applyDiff(originalCode, Arrays.asList(block));
        assertEquals(originalCode.strip(), result.strip());
    }

    @Test
    public void testApplyDiff_OverlappingSequentialReplacements() {
        String originalCode = "abc123xyz";

        CodeParsingUtils.DiffBlock first = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "abc123",
            "ABC"
        );

        CodeParsingUtils.DiffBlock second = new CodeParsingUtils.DiffBlock(
            "src/main.java",
            "ABCxyz",
            "DONE"
        );

        String result = CodeParsingUtils.applyDiff(originalCode, Arrays.asList(first, second));
        assertEquals("DONE", result.strip());
    }

    @Test
    public void testExtractChanges_FilePathWithSpaces() {
        String llmResponse = """
            src/My Class.java
            <<<<<<< SEARCH
            class MyClass { }
            =======
            class MyClass { String name; }
            >>>>>>> REPLACE
            """;

        List<String> paths = Arrays.asList("src/My Class.java");
        List<CodeParsingUtils.DiffBlock> result = CodeParsingUtils.extractChanges(llmResponse, paths);

        assertEquals(1, result.size());
        assertEquals("src/My Class.java", result.get(0).getFile());
        assertEquals("class MyClass { }", result.get(0).getSearchText());
        assertEquals("class MyClass { String name; }", result.get(0).getReplaceText());
    }

    @Test
    public void testParseFullRewrite_PrefersTargetLanguageAmongMany() {
        String llmResponse = """
            Intro text.
            ```python
            def py():
                pass
            ```
            Between blocks.
            ```java
            class J { }
            ```
            Outro.
            """;

        String result = CodeParsingUtils.parseFullRewrite(llmResponse, "java").orElse("");
        assertEquals("class J { }", result.strip());
    }

    @Test
    public void testParseFullRewrite_FirstOfMultipleTargetBlocks() {
        String llmResponse = """
            ```java
            class First { }
            ```
            some text
            ```java
            class Second { }
            ```
            """;

        String result = CodeParsingUtils.parseFullRewrite(llmResponse, "java").orElse("");
        // Expect to choose the first matching code block
        assertEquals("class First { }", result.strip());
    }
}
