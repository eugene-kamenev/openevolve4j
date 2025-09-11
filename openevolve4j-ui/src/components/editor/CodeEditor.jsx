import React, { useState } from "react";
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

  const handleChange = (newValue) => {
    if (onChange) {
      onChange(newValue);
    } else {
      setInternalCode(newValue);
    }
  };

  if (isDiff) {
    return (
      <div className="h-screen w-full">
        <DiffEditor
          height={height}
          language={language}
          original={originalCode}
          modified={modifiedCode}
          theme={theme}
          options={{
            fontSize: 14,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            wordWrap: "on",
            readOnly: true,
            renderSideBySide: true,
            ...props.options
          }}
          {...props}
        />
      </div>
    );
  }

  return (
    <div className="h-screen w-full">
      <Editor
        height={height}
        defaultLanguage={language}
        value={code || internalCode}
        theme={theme}
        onChange={handleChange}
        options={{
          fontSize: 14,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: "on",
          ...props.options
        }}
        {...props}
      />
    </div>
  );
}

export default CodeEditor;
