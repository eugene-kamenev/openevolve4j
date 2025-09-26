Produce a report based on the following aggregated per-topic relevant paragraphs. Each paragraph contains an index of a source. Make sure to refer to this index in the form [[index]] every time you rely on the information from the source. Respect the format prompt. Do not output any other text.

Report prompt: {taskPrompt}

Topic relevant paragraphs: {topicSegments}

Format prompt: {formatPrompt}

Reminders: The output should be a report in Markdown format. The report should be formatted correctly according to the Format prompt in Markdown. Every single mention of an information stemming from one of the sources should be accompanied by the source index in the form [[index]] (or [[index1,index2,...]]) within or after the statement of the information. A list of the source URLs to correspond to the indices will be provided separately -- do not attempt to output it. Do not output any other text.
