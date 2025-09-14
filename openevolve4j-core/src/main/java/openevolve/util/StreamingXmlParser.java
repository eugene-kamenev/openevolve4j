package openevolve.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * A streaming XML handler that processes XML data in a non-blocking manner. It uses Jackson's XML
 * streaming API to parse XML data and allows custom processing of found XML nodes. It was designed
 * to parse valid XML structures out of a stream of free text. The idea is that it will start
 * parsing when it finds a '<' and stop when it finds the matching closing tag for root elements.
 *
 * NOTE: Performance and memory usage has not been analyzed. Use requiresOutput = false, if text
 * output is not required, to avoid buffering.
 */
public class StreamingXmlParser<T> {

	private static final char[] empty = new char[0];
	private static final String fenceBlock = "```xml";

	private final char[] charOutputBuffer = new char[1];
	private final StringBuffer outBuffer;
	private final StringBuffer feedBuffer;
	private final StringBuffer xmlBuffer;
	private final XMLInputFactory xmlInputFactory;
	private final XmlMapper xmlMapper;
	private final boolean requiresOutput;
	private XMLStreamReader xmlReader;
	private boolean buffering = false;
	private boolean shouldStop = false;
	private Consumer<T> xmlConsumer;
	private TypeReference<T> typeRef;
	private int inFenceBlock = 0;

	public StreamingXmlParser(XmlMapper mapper, boolean requiresOutput, TypeReference<T> typeRef) {
		this.xmlInputFactory = XMLInputFactory.newFactory();
		this.xmlMapper = mapper;
		this.requiresOutput = requiresOutput;
		this.outBuffer = new StringBuffer();
		this.feedBuffer = new StringBuffer();
		this.typeRef = typeRef;
		this.xmlBuffer = new StringBuffer();
		resetParser();
	}

	public StreamingXmlParser(XmlMapper mapper, TypeReference<T> typeRef) {
		this(mapper, true, typeRef);
	}

	public void consume(Consumer<T> xmlConsumer) {
		this.xmlConsumer = xmlConsumer;
	}

	public void shouldStop() {
		this.shouldStop = true;
	}

	public boolean isStopped() {
		return this.shouldStop;
	}

	public void close() {
		try {
			if (xmlReader != null) {
				this.shouldStop = false;
				this.xmlReader.close();
			}
		} catch (XMLStreamException ignored) {
		}
	}

	public String feedText(String chunk) {
		return feedText(chunk, false);
	}

	public String feedText(String chunk, boolean isLast) {
		if (chunk == null || chunk.isEmpty()) {
			return chunk;
		}
		var last = chunk.length() - 1;
		feedBuffer.setLength(0);
		for (int i = 0; i <= last; i++) {
			if (shouldStop) {
				break;
			}
			var feedResult = feedChar(chunk.charAt(i), i == last && isLast);
			if (feedResult.length > 0) {
				feedBuffer.append(feedResult);
			}
		}
		return feedBuffer.toString();
	}

	private char[] feedChar(char ch, boolean isLast) {
		try {
			if (!buffering) {
				if (isLast && requiresOutput) {
					outBuffer.append(ch);
					return returnRemainingBuffer();
				} else if (ch == '<') {
					buffering = true;
					inFenceBlock = 0;
					feedCharToXmlBuffer(ch, isLast);
					return empty;
				} else if (inFenceBlock == 0 && ch == '`') {
					inFenceBlock++;
					outBuffer.append(ch);
					return empty;
				} else if (inFenceBlock > 0 && !buffering) {
					outBuffer.append(ch);
					String currentOutBufferContent = outBuffer.toString();

					if (currentOutBufferContent.equals(fenceBlock)) {
						buffering = true;
						inFenceBlock = 0; // Reset fence block detection state
						xmlBuffer.setLength(0);
						return empty; // Char consumed as part of the fence block
					} else if (fenceBlock.startsWith(currentOutBufferContent)) {
						// Partial fence block, continue accumulating
						return empty;
					} else {
						// Not a fence block (e.g., "```x")
						// The content of outBuffer should be returned.
						inFenceBlock = 0;
						return returnRemainingBuffer(); // This will return currentOutBufferContent
														// and clear outBuffer
					}
				} else if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
					// Output whitespace immediately if not in fence block
					if (requiresOutput) {
						charOutputBuffer[0] = ch;
						return charOutputBuffer;
					}
					return empty;
				} else {
					// Output any buffered text (should only be from fence block logic)
					if (outBuffer.length() > 0) {
						return returnRemainingBuffer();
					}
					// Output non-XML, non-fence, non-whitespace character immediately
					if (requiresOutput) {
						charOutputBuffer[0] = ch;
						return charOutputBuffer;
					}
					return empty;
				}
			} else {
				return feedCharToXmlBuffer(ch, isLast);
			}
		} catch (Exception e) {
			// Save the current buffer before reset
			String remaining = outBuffer.toString();
			resetParser();
			return processRemainingBufferAfterError(remaining, isLast);
		}
	}

	private char[] feedCharToXmlBuffer(char ch, boolean isLast)
			throws XMLStreamException, IOException {
		xmlBuffer.append(ch);

		// Try to parse what we have so far
		if (ch == '>') {
			String xmlContent = xmlBuffer.toString().trim();

			// Check if we have a complete XML structure
			if (isCompleteXmlStructure(xmlContent)) {
				try {
					// Parse the complete XML
					parseXmlContent(xmlContent);

					resetParser();
					if (requiresOutput) {
						outBuffer.append(xmlContent);
						return returnRemainingBuffer();
					}
					
					return empty;
				} catch (Exception e) {
					// If parsing fails, treat as regular text
					resetParser();
					if (requiresOutput) {
						outBuffer.append(xmlContent);
						return returnRemainingBuffer();
					}
					return empty;
				}
			}
		}

		// If this is the last character and we're still buffering, try to parse what we have
		if (isLast && xmlBuffer.length() > 0) {
			String xmlContent = xmlBuffer.toString().trim();
			try {
				parseXmlContent(xmlContent);
				if (requiresOutput) {
					outBuffer.append(xmlContent);
					return returnRemainingBuffer();
				}
			} catch (Exception e) {
				// If parsing fails, output as regular text
				if (requiresOutput) {
					outBuffer.append(xmlContent);
					return returnRemainingBuffer();
				}
			}
			resetParser();
		}

		return empty;
	}

	private boolean isCompleteXmlStructure(String xmlContent) {
		// Simple heuristic: check if we have matching tags
		if (xmlContent.startsWith("<?xml")) {
			// Look for the end of the XML declaration and start of root element
			int declarationEnd = xmlContent.indexOf("?>");
			if (declarationEnd == -1)
				return false;
			return hasMatchingRootTags(xmlContent.substring(declarationEnd + 2).trim());
		} else if (xmlContent.startsWith("<")) {
			return hasMatchingRootTags(xmlContent);
		}
		return false;
	}

	private boolean hasMatchingRootTags(String xmlContent) {
		// Find the first opening tag
		int firstTagStart = xmlContent.indexOf('<');
		if (firstTagStart == -1)
			return false;

		int firstTagEnd = xmlContent.indexOf('>', firstTagStart);
		if (firstTagEnd == -1)
			return false;

		String firstTag = xmlContent.substring(firstTagStart, firstTagEnd + 1);

		// Handle self-closing tags
		if (firstTag.endsWith("/>")) {
			return true;
		}

		// Extract tag name
		String tagName = firstTag.substring(1);
		int spaceIndex = tagName.indexOf(' ');
		if (spaceIndex != -1) {
			tagName = tagName.substring(0, spaceIndex);
		}
		if (tagName.endsWith(">")) {
			tagName = tagName.substring(0, tagName.length() - 1);
		}

		// Look for closing tag
		String closingTag = "</" + tagName + ">";
		return xmlContent.contains(closingTag);
	}

	private void parseXmlContent(String xmlContent) throws XMLStreamException, IOException {
		if (xmlContent.trim().isEmpty()) {
			return;
		}

		try {
			ByteArrayInputStream inputStream =
					new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
			XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);

			// Find the first start element and parse it
			while (reader.hasNext()) {
				int eventType = reader.next();
				if (eventType == XMLStreamReader.START_ELEMENT) {
					T node = xmlMapper.readValue(reader, typeRef);
					if (xmlConsumer != null && node != null) {
						xmlConsumer.accept(node);
					}
					break; // Only parse the first element
				}
			}

			reader.close();
		} catch (JsonProcessingException e) {
			// Skip malformed XML
		}
	}

	private char[] returnRemainingBuffer() {
		if (outBuffer.length() > 0) {
			var chars = outBuffer.toString().toCharArray();
			outBuffer.setLength(0);
			return chars;
		}
		return empty;
	}

	private char[] processRemainingBufferAfterError(String remaining, boolean isLast) {
		int nextXmlStart = remaining.indexOf('<', 1);

		if (nextXmlStart >= 0 && nextXmlStart < remaining.length()) {
			outBuffer.setLength(0); // Clear output buffer

			var prefixStr =
					(requiresOutput && nextXmlStart > 0) ? remaining.substring(0, nextXmlStart)
							: "";
			var restStr = remaining.substring(nextXmlStart);

			var resultBuilder = new StringBuilder();
			for (int i = 0; i < restStr.length(); i++) {
				boolean lastChar = isLast && i == restStr.length() - 1;
				char[] processed = feedChar(restStr.charAt(i), lastChar);
				if (processed.length > 0) {
					resultBuilder.append(processed);
				}
			}

			return (prefixStr + resultBuilder).toCharArray();
		}

		if (requiresOutput && !remaining.isEmpty()) {
			outBuffer.setLength(0);
			return remaining.toCharArray();
		}

		outBuffer.setLength(0);
		return empty;
	}

	private void resetParser() {
		buffering = false;
		inFenceBlock = 0;
		xmlBuffer.setLength(0);
		try {
			if (xmlReader != null) {
				xmlReader.close();
			}
			xmlReader = null;
		} catch (XMLStreamException e) {
			// Ignore
		}
	}
}
