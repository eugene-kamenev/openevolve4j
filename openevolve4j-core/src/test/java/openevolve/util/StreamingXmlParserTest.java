package openevolve.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for StreamingXmlParser covering various free text scenarios
 * with embedded XML content.
 */
@DisplayName("StreamingXmlParser Tests")
public class StreamingXmlParserTest {

    private XmlMapper xmlMapper;
    private StreamingXmlParser<Person> personParser;
    private StreamingXmlParser<Book> bookParser;
    private Consumer<Person> personConsumer;
    private Consumer<Book> bookConsumer;
    private List<Person> capturedPersons;
    private List<Book> capturedBooks;

    @BeforeEach
    void setUp() {
        xmlMapper = new XmlMapper();
        capturedPersons = new ArrayList<>();
        capturedBooks = new ArrayList<>();
        
        // Create real consumers that capture parsed objects
        personConsumer = capturedPersons::add;
        bookConsumer = capturedBooks::add;
        
        // Initialize parsers
        personParser = new StreamingXmlParser<>(xmlMapper, new TypeReference<Person>() {});
        bookParser = new StreamingXmlParser<>(xmlMapper, new TypeReference<Book>() {});
        
        personParser.consume(personConsumer);
        bookParser.consume(bookConsumer);
    }

    // Test data classes
    @JacksonXmlRootElement(localName = "person")
    public static class Person {
        private String name;
        private int age;
        private String email;

        public Person() {}

        public Person(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return age == person.age && 
                   Objects.equals(name, person.name) && 
                   Objects.equals(email, person.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age, email);
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + ", email='" + email + "'}";
        }
    }

    @JacksonXmlRootElement(localName = "book")
    public static class Book {
        private String title;
        private String author;
        private String isbn;

        public Book() {}

        public Book(String title, String author, String isbn) {
            this.title = title;
            this.author = author;
            this.isbn = isbn;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public String getIsbn() { return isbn; }
        public void setIsbn(String isbn) { this.isbn = isbn; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Book book = (Book) o;
            return Objects.equals(title, book.title) && 
                   Objects.equals(author, book.author) && 
                   Objects.equals(isbn, book.isbn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, author, isbn);
        }

        @Override
        public String toString() {
            return "Book{title='" + title + "', author='" + author + "', isbn='" + isbn + "'}";
        }
    }

    @Nested
    @DisplayName("Basic XML Parsing Tests")
    class BasicXmlParsingTests {

        @Test
        @DisplayName("Should parse simple XML element embedded in free text")
        void shouldParseSimpleXmlInFreeText() {
            String input = "Here is some text before <person><name>John Doe</name><age>30</age><email>john@example.com</email></person> and some text after.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("John Doe", person.getName());
            assertEquals(30, person.getAge());
            assertEquals("john@example.com", person.getEmail());
            
            assertEquals(input, output); // Should return all input including XML
        }

        @Test
        @DisplayName("Should parse multiple XML elements in free text")
        void shouldParseMultipleXmlElements() {
            String input = "First person: <person><name>Alice</name><age>25</age><email>alice@test.com</email></person>" +
                          " Second person: <person><name>Bob</name><age>35</age><email>bob@test.com</email></person>" +
                          " End of text.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(2, capturedPersons.size());
            
            Person alice = capturedPersons.get(0);
            assertEquals("Alice", alice.getName());
            assertEquals(25, alice.getAge());
            
            Person bob = capturedPersons.get(1);
            assertEquals("Bob", bob.getName());
            assertEquals(35, bob.getAge());
            
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should parse self-closing XML tags")
        void shouldParseSelfClosingXmlTags() {
            String input = "Here's a book: <book title=\"Test Book\" author=\"Test Author\" isbn=\"123-456\"/> Done.";
            
            String output = bookParser.feedText(input, true);
            
            assertEquals(1, capturedBooks.size());
            Book book = capturedBooks.get(0);
            assertEquals("Test Book", book.getTitle());
            assertEquals("Test Author", book.getAuthor());
            assertEquals("123-456", book.getIsbn());
        }

        @Test
        @DisplayName("Should handle XML with attributes and nested elements")
        void shouldParseXmlWithAttributesAndNesting() {
            String input = "Complex example: <person><name>Jane Smith</name><age>28</age><email>jane@company.org</email></person> finished.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Jane Smith", person.getName());
            assertEquals(28, person.getAge());
            assertEquals("jane@company.org", person.getEmail());
        }

        @Test
        @DisplayName("Should handle XML at the beginning of text")
        void shouldParseXmlAtBeginning() {
            String input = "<person><name>Start Person</name><age>40</age><email>start@test.com</email></person> This comes after XML.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Start Person", person.getName());
            assertEquals(40, person.getAge());
        }

        @Test
        @DisplayName("Should handle XML at the end of text")
        void shouldParseXmlAtEnd() {
            String input = "This comes before XML <person><name>End Person</name><age>45</age><email>end@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("End Person", person.getName());
            assertEquals(45, person.getAge());
        }

        @Test
        @DisplayName("Should handle text with no XML")
        void shouldHandleTextWithNoXml() {
            String input = "This is just plain text with no XML elements at all.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(0, capturedPersons.size());
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle empty input")
        void shouldHandleEmptyInput() {
            String output = personParser.feedText("", true);
            
            assertEquals(0, capturedPersons.size());
            assertEquals("", output);
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            String output = personParser.feedText(null, true);
            
            assertEquals(0, capturedPersons.size());
            assertNull(output);
        }
    }

    @Nested
    @DisplayName("XML Fence Block Parsing Tests")
    class XmlFenceBlockParsingTests {

        @Test
        @DisplayName("Should parse XML inside ```xml fence blocks")
        void shouldParseXmlInFenceBlocks() {
            String input = "Here's some markdown text:\n\n```xml\n<person><name>Fence Person</name><age>33</age><email>fence@test.com</email></person>\n```\n\nMore text after.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Fence Person", person.getName());
            assertEquals(33, person.getAge());
            assertEquals("fence@test.com", person.getEmail());
            
            assertTrue(output.contains("Here's some markdown text:"));
            assertTrue(output.contains("More text after."));
        }

        @Test
        @DisplayName("Should handle multiple XML fence blocks")
        void shouldHandleMultipleFenceBlocks() {
            String input = "First block:\n```xml\n<person><name>First</name><age>20</age><email>first@test.com</email></person>\n```\n" +
                          "Second block:\n```xml\n<person><name>Second</name><age>30</age><email>second@test.com</email></person>\n```\nDone.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(2, capturedPersons.size());
            assertEquals("First", capturedPersons.get(0).getName());
            assertEquals("Second", capturedPersons.get(1).getName());
        }

        @Test
        @DisplayName("Should ignore incomplete fence blocks")
        void shouldIgnoreIncompleteFenceBlocks() {
            String input = "This has ```x which is not a complete fence block.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(0, capturedPersons.size());
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle fence blocks without closing")
        void shouldHandleFenceBlocksWithoutClosing() {
            String input = "Start ```xml\n<person><name>Incomplete</name><age>25</age><email>incomplete@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Incomplete", person.getName());
        }

        @Test
        @DisplayName("Should handle empty fence blocks")
        void shouldHandleEmptyFenceBlocks() {
            String input = "Empty block: ```xml\n```\nDone.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(0, capturedPersons.size());
            assertTrue(output.contains("Empty block:"));
            assertTrue(output.contains("Done."));
        }

        @Test
        @DisplayName("Should differentiate between fence blocks and inline XML")
        void shouldDifferentiateFenceBlocksAndInlineXml() {
            String input = "Inline: <person><name>Inline</name><age>40</age><email>inline@test.com</email></person>\n" +
                          "Fence: ```xml\n<person><name>Fence</name><age>50</age><email>fence@test.com</email></person>\n```";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(2, capturedPersons.size());
            assertEquals("Inline", capturedPersons.get(0).getName());
            assertEquals("Fence", capturedPersons.get(1).getName());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed XML gracefully")
        void shouldHandleMalformedXml() {
            String input = "This has malformed XML: <person><name>Broken<age>30</person> and continues.";
            
            String output = personParser.feedText(input, true);
            
            // Should not crash and should treat as regular text
            assertEquals(0, capturedPersons.size());
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle incomplete XML at end of input")
        void shouldHandleIncompleteXmlAtEnd() {
            String input = "Text with incomplete XML: <person><name>Incomplete";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(0, capturedPersons.size());
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle XML with missing closing tags")
        void shouldHandleXmlWithMissingClosingTags() {
            String input = "XML missing close: <person><name>No Close</name><age>25</age>";
            
            String output = personParser.feedText(input, true);
            
            // Should try to parse but fail gracefully
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle XML declarations")
        void shouldHandleXmlDeclarations() {
            String input = "XML with declaration: <?xml version=\"1.0\" encoding=\"UTF-8\"?><person><name>Declared</name><age>35</age><email>declared@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Declared", person.getName());
            assertEquals(35, person.getAge());
        }

        @Test
        @DisplayName("Should handle XML with namespaces")
        void shouldHandleXmlWithNamespaces() {
            String input = "XML with namespace: <ns:person xmlns:ns=\"http://example.com\"><ns:name>Namespaced</ns:name><ns:age>40</ns:age><ns:email>ns@test.com</ns:email></ns:person>";
            
            String output = personParser.feedText(input, true);
            
            // May or may not parse depending on Jackson configuration, but shouldn't crash
            assertNotNull(output);
            assertTrue(output.contains("Namespaced"));
        }

        @Test
        @DisplayName("Should handle XML with CDATA sections")
        void shouldHandleXmlWithCdata() {
            String input = "XML with CDATA: <person><name><![CDATA[John & Jane]]></name><age>30</age><email>cdata@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            // Should handle CDATA gracefully
            assertNotNull(output);
            assertTrue(output.contains("John & Jane"));
        }

        @Test
        @DisplayName("Should handle very long XML content")
        void shouldHandleVeryLongXmlContent() {
            StringBuilder longName = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longName.append("VeryLongName");
            }
            
            String input = "Long XML: <person><name>" + longName + "</name><age>30</age><email>long@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            // Should handle large content without issues
            assertNotNull(output);
            assertTrue(output.contains("Long XML:"));
        }

        @Test
        @DisplayName("Should handle XML with special characters")
        void shouldHandleXmlWithSpecialCharacters() {
            String input = "Special chars: <person><name>José María</name><age>30</age><email>jose.maria@español.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("José María", person.getName());
            assertEquals("jose.maria@español.com", person.getEmail());
        }

        @Test
        @DisplayName("Should handle nested angle brackets in text")
        void shouldHandleNestedAngleBrackets() {
            String input = "Math expression: 2 < 3 < 4 and code: if (x < y) { return x; } done.";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(0, capturedPersons.size());
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle mixed valid and invalid XML")
        void shouldHandleMixedValidAndInvalidXml() {
            String input = "Invalid: <person><name>Broken<age>30</person> Valid: <person><name>Good</name><age>25</age><email>good@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            // Should parse the valid XML and treat invalid as text
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Good", person.getName());
            assertEquals(25, person.getAge());
        }
    }

    @Nested
    @DisplayName("Streaming Behavior Tests")
    class StreamingBehaviorTests {

        @Test
        @DisplayName("Should handle character-by-character feeding")
        void shouldHandleCharacterByCharacterFeeding() {
            String xmlContent = "<person><name>Streamed</name><age>30</age><email>stream@test.com</email></person>";
            String fullInput = "Text before " + xmlContent + " text after";
            
            StringBuilder output = new StringBuilder();
            
            // Feed character by character
            for (int i = 0; i < fullInput.length(); i++) {
                boolean isLast = i == fullInput.length() - 1;
                String char_output = personParser.feedText(String.valueOf(fullInput.charAt(i)), isLast);
                output.append(char_output);
            }
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Streamed", person.getName());
            assertEquals(30, person.getAge());
            
            assertEquals(fullInput, output.toString());
        }

        @Test
        @DisplayName("Should handle partial chunk feeding")
        void shouldHandlePartialChunkFeeding() {
            String input = "Start <person><name>Chunked</name><age>35</age><email>chunk@test.com</email></person> end";
            
            StringBuilder output = new StringBuilder();
            
            // Feed in chunks
            output.append(personParser.feedText("Start <person><na", false));
            output.append(personParser.feedText("me>Chunked</name><age>", false));
            output.append(personParser.feedText("35</age><email>chunk@test.com", false));
            output.append(personParser.feedText("</email></person> end", true));
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Chunked", person.getName());
            assertEquals(35, person.getAge());
            
            assertEquals(input, output.toString());
        }

        @Test
        @DisplayName("Should handle whitespace correctly")
        void shouldHandleWhitespaceCorrectly() {
            String input = "   \n\t  <person>  \n  <name>  Spaced  </name>  \n  <age>40</age>  \n  <email>space@test.com</email>  \n  </person>  \n\t  ";
            
            String output = personParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("  Spaced  ", person.getName()); // Whitespace should be preserved in content
            assertEquals(40, person.getAge());
            
            assertEquals(input, output);
        }

        @Test
        @DisplayName("Should handle multiple isLast=false calls followed by isLast=true")
        void shouldHandleMultipleNonLastCalls() {
            StringBuilder output = new StringBuilder();
            
            output.append(personParser.feedText("Begin ", false));
            output.append(personParser.feedText("<person><name>Multi", false));
            output.append(personParser.feedText("</name><age>25</age>", false));
            output.append(personParser.feedText("<email>multi@test.com</email></person>", false));
            output.append(personParser.feedText(" end", true));
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Multi", person.getName());
            assertEquals(25, person.getAge());
            
            String expected = "Begin <person><name>Multi</name><age>25</age><email>multi@test.com</email></person> end";
            assertEquals(expected, output.toString());
        }

        @Test
        @DisplayName("Should handle empty strings in stream")
        void shouldHandleEmptyStringsInStream() {
            StringBuilder output = new StringBuilder();
            
            output.append(personParser.feedText("", false));
            output.append(personParser.feedText("Start ", false));
            output.append(personParser.feedText("", false));
            output.append(personParser.feedText("<person><name>Empty", false));
            output.append(personParser.feedText("", false));
            output.append(personParser.feedText("Test</name><age>30</age><email>empty@test.com</email></person>", false));
            output.append(personParser.feedText("", false));
            output.append(personParser.feedText(" end", true));
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("EmptyTest", person.getName());
            
            assertEquals("Start <person><name>EmptyTest</name><age>30</age><email>empty@test.com</email></person> end", output.toString());
        }

        @Test
        @DisplayName("Should handle large input in small chunks")
        void shouldHandleLargeInputInSmallChunks() {
            StringBuilder largeContent = new StringBuilder();
            largeContent.append("Large text before: ");
            for (int i = 0; i < 100; i++) {
                largeContent.append("This is repetitive text. ");
            }
            largeContent.append("<person><name>Large</name><age>50</age><email>large@test.com</email></person>");
            for (int i = 0; i < 100; i++) {
                largeContent.append(" More repetitive text.");
            }
            
            String input = largeContent.toString();
            StringBuilder output = new StringBuilder();
            
            // Feed in 10-character chunks
            for (int i = 0; i < input.length(); i += 10) {
                int end = Math.min(i + 10, input.length());
                boolean isLast = end == input.length();
                String chunk = input.substring(i, end);
                output.append(personParser.feedText(chunk, isLast));
            }
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("Large", person.getName());
            assertEquals(50, person.getAge());
            
            assertEquals(input, output.toString());
        }
    }

    @Nested
    @DisplayName("Output Modes and Consumer Behavior Tests")
    class OutputModesAndConsumerBehaviorTests {

        @Test
        @DisplayName("Should respect requiresOutput=false flag")
        void shouldRespectRequiresOutputFalse() {
            StreamingXmlParser<Person> noOutputParser = new StreamingXmlParser<>(xmlMapper, false, new TypeReference<Person>() {});
            noOutputParser.consume(personConsumer);
            
            String input = "Text before <person><name>NoOutput</name><age>30</age><email>no@test.com</email></person> text after";
            
            String output = noOutputParser.feedText(input, true);
            
            assertEquals(1, capturedPersons.size());
            Person person = capturedPersons.get(0);
            assertEquals("NoOutput", person.getName());
            
            // With requiresOutput=false, output should be empty or minimal
            assertTrue(output.isEmpty() || output.length() < input.length());
        }

        @Test
        @DisplayName("Should work without consumer")
        void shouldWorkWithoutConsumer() {
            StreamingXmlParser<Person> noConsumerParser = new StreamingXmlParser<>(xmlMapper, new TypeReference<Person>() {});
            // Don't set any consumer
            
            String input = "Text with <person><name>NoConsumer</name><age>25</age><email>no.consumer@test.com</email></person> XML";
            
            // Should not crash without a consumer
            assertDoesNotThrow(() -> {
                String output = noConsumerParser.feedText(input, true);
                assertNotNull(output);
            });
        }

        @Test
        @DisplayName("Should handle consumer that throws exceptions")
        void shouldHandleConsumerThatThrowsExceptions() {
            Consumer<Person> throwingConsumer = person -> {
                // Simulate processing that throws an exception
                if (person != null) {
                    throw new RuntimeException("Consumer error");
                }
            };
            
            personParser.consume(throwingConsumer);
            
            String input = "Test <person><name>ThrowingConsumer</name><age>30</age><email>throwing@test.com</email></person> end";
            
            // Should handle consumer exceptions gracefully
            assertDoesNotThrow(() -> {
                String output = personParser.feedText(input, true);
                assertNotNull(output);
            });
        }

        @Test
        @DisplayName("Should support stop functionality")
        void shouldSupportStopFunctionality() {
            List<Person> stoppedPersons = new ArrayList<>();
            Consumer<Person> stoppingConsumer = person -> {
                stoppedPersons.add(person);
                if (stoppedPersons.size() >= 1) {
                    personParser.shouldStop();
                }
            };
            
            personParser.consume(stoppingConsumer);
            
            String input = "First: <person><name>First</name><age>20</age><email>first@test.com</email></person> " +
                          "Second: <person><name>Second</name><age>30</age><email>second@test.com</email></person>";
            
            String output = personParser.feedText(input, true);
            
            // Should stop after processing first person
            assertEquals(1, stoppedPersons.size());
            assertEquals("First", stoppedPersons.get(0).getName());
            assertTrue(personParser.isStopped());
        }

        @Test
        @DisplayName("Should handle close operation")
        void shouldHandleCloseOperation() {
            String input = "Test <person><name>Closeable</name><age>25</age><email>close@test.com</email></person>";
            
            personParser.feedText(input, false); // Don't mark as last yet
            
            // Close the parser
            assertDoesNotThrow(() -> personParser.close());
            
            // Parser should be usable after close
            assertFalse(personParser.isStopped());
            
            // Can continue feeding after close
            assertDoesNotThrow(() -> {
                String output = personParser.feedText(" end", true);
                assertNotNull(output);
            });
        }

        @Test
        @DisplayName("Should handle multiple consumers via method calls")
        void shouldHandleMultipleConsumerUpdates() {
            List<Person> firstList = new ArrayList<>();
            List<Person> secondList = new ArrayList<>();
            
            // Set first consumer
            personParser.consume(firstList::add);
            
            String input1 = "First: <person><name>First</name><age>20</age><email>first@test.com</email></person>";
            personParser.feedText(input1, true);
            
            assertEquals(1, firstList.size());
            assertEquals(0, secondList.size());
            
            // Change to second consumer
            personParser.consume(secondList::add);
            
            String input2 = "Second: <person><name>Second</name><age>30</age><email>second@test.com</email></person>";
            personParser.feedText(input2, true);
            
            assertEquals(1, firstList.size()); // Should not receive new items
            assertEquals(1, secondList.size()); // Should receive new items
            assertEquals("Second", secondList.get(0).getName());
        }

        @Test
        @DisplayName("Should work with different TypeReference types")
        void shouldWorkWithDifferentTypeReferences() {
            String bookInput = "Book: <book><title>Test Book</title><author>Test Author</author><isbn>123-456</isbn></book>";
            
            String output = bookParser.feedText(bookInput, true);
            
            assertEquals(1, capturedBooks.size());
            Book book = capturedBooks.get(0);
            assertEquals("Test Book", book.getTitle());
            assertEquals("Test Author", book.getAuthor());
            assertEquals("123-456", book.getIsbn());
            
            assertEquals(bookInput, output);
        }
    }
}
