import React, { useState, useEffect } from "react";
import Editor, { DiffEditor } from "@monaco-editor/react";

function CodeEditor({
  code,
  onChange,
  language = "javascript",
  theme = "vs-dark",
  height = "90vh",
  isDiff = false,
  originalCode = "",
  modifiedCode = "",
  ...props
}) {
  const [internalCode, setInternalCode] = useState(code || "// Start typing...");

  // Keep internal code in sync when `code` prop changes (useful for read-only previews)
  useEffect(() => {
    if (!onChange) {
      setInternalCode(code || "// Start typing...");
    }
  }, [code, onChange]);

  const handleChange = (newValue) => {
    if (onChange) {
      onChange(newValue);
    } else {
      setInternalCode(newValue);
    }
  };

  if (isDiff) {
    const diffOptions = {
      fontSize: 14,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      wordWrap: "on",
      readOnly: true,
      renderSideBySide: true,
      automaticLayout: true,
      ...(props.options || {})
    };

    return (
      <div style={{ height, width: '100%' }}>
        <DiffEditor
          height={height}
          language={language}
          original={originalCode}
          modified={modifiedCode}
          theme={theme}
          options={diffOptions}
          {...props}
        />
      </div>
    );
  }
  const editorOptions = {
    fontSize: 14,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: "on",
    automaticLayout: true,
    ...(props.options || {})
  };

  return (
    <div style={{ height, width: '100%' }}>
      <Editor
        height={height}
        language={language}
        defaultLanguage={language}
        value={code || internalCode}
        theme={theme}
        onChange={handleChange}
        options={editorOptions}
        {...props}
      />
    </div>
  );
}

export default CodeEditor;
