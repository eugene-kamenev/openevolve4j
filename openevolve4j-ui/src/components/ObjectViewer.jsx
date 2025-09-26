// Enhanced JSON viewer component with expandable keys
import React, { useState } from 'react';
import { ChevronDown, ChevronRight, Copy, Check } from 'lucide-react';

export default function ObjectViewer({ data, level = 0 }) {
  const [expandedKeys, setExpandedKeys] = useState(() => {
    const initialExpanded = new Set();
    if (level === 0 && data && typeof data === 'object' && !Array.isArray(data)) {
      initialExpanded.add('root');
    }
    return initialExpanded;
  });
  const [copiedKeys, setCopiedKeys] = useState(() => new Set());

  const copyToClipboard = (text, key, e) => {
    if (e && e.stopPropagation) e.stopPropagation();
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(text).then(() => {
      const next = new Set(copiedKeys);
      next.add(key);
      setCopiedKeys(next);
      setTimeout(() => {
        const next2 = new Set(next);
        next2.delete(key);
        setCopiedKeys(next2);
      }, 1500);
    }).catch(() => {
      // ignore clipboard failures silently
    });
  };
  
  const toggleKey = (key) => {
    const newExpanded = new Set(expandedKeys);
    if (newExpanded.has(key)) newExpanded.delete(key); else newExpanded.add(key);
    setExpandedKeys(newExpanded);
  };

  const renderValue = (value, key = null, currentLevel = level) => {
    if (value === null || value === undefined) return <span className="json-null">null</span>;
    if (typeof value === 'string') {
      const copyKey = key || `str-${currentLevel}`;
      const isCopied = copiedKeys.has(copyKey);
      if (value.includes('\n')) {
        return (
          <span className="json-string-multiline-with-copy">
            <button
              className="json-copy-btn"
              onClick={(e) => copyToClipboard(value, copyKey, e)}
              title="Copy"
              aria-label="Copy"
            >
              {isCopied ? <Check size={14} /> : <Copy size={14} />}
            </button>
            <pre className="json-string multiline">{value}</pre>
          </span>
        );
      }
      return (
        <span className="json-string-with-copy">
          <button
            className="json-copy-btn"
            onClick={(e) => copyToClipboard(value, copyKey, e)}
            title="Copy"
            aria-label="Copy"
          >
            {isCopied ? <Check size={14} /> : <Copy size={14} />}
          </button>
          <span className="json-string">{value}</span>
        </span>
      );
    }
    if (typeof value === 'number') return <span className="json-number">{value}</span>;
    if (typeof value === 'boolean') return <span className="json-boolean">{String(value)}</span>;
    if (Array.isArray(value)) {
      const arrayKey = key || 'array';
      const isExpanded = expandedKeys.has(arrayKey);
      const isEmpty = value.length === 0;
      if (isEmpty) return <span className="json-bracket">[]</span>;
      return (
        <div className="json-container">
          <div className="json-header clickable" onClick={() => toggleKey(arrayKey)}>
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <span className="json-bracket">[</span>
            <span className="json-count">{value.length} {value.length === 1 ? 'item' : 'items'}</span>
            {!isExpanded && <span className="json-bracket">]</span>}
          </div>
          {isExpanded && (
            <div className="json-body">
              {value.map((item, idx) => (
                <div key={idx} className="json-item">
                  <span className="json-index">{idx}:</span>
                  {renderValue(item, `${arrayKey}[${idx}]`, currentLevel + 1)}
                </div>
              ))}
              <span className="json-bracket">]</span>
            </div>
          )}
        </div>
      );
    }
    if (typeof value === 'object') {
      const objectKey = key || 'object';
      const isExpanded = expandedKeys.has(objectKey);
      const entries = Object.entries(value);
      const isEmpty = entries.length === 0;
      if (isEmpty) return <span className="json-bracket">{'{}'}</span>;
      return (
        <div className="json-container">
          <div className="json-header clickable" onClick={() => toggleKey(objectKey)}>
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <span className="json-bracket">{'{'}</span>
            <span className="json-count">{entries.length} {entries.length === 1 ? 'property' : 'properties'}</span>
            {!isExpanded && <span className="json-bracket">{'}'}</span>}
          </div>
          {isExpanded && (
            <div className="json-body">
              {entries.map(([objKey, objValue], idx) => (
                <div key={objKey} className="json-property">
                  <span className="json-key">"{objKey}":</span>
                  {renderValue(objValue, `${objectKey}.${objKey}`, currentLevel + 1)}
                </div>
              ))}
              <span className="json-bracket">{'}'}</span>
            </div>
          )}
        </div>
      );
    }
    return <span className="json-string">{String(value)}</span>;
  };

  return <div className="json-viewer" style={{ '--level': level }}>{renderValue(data, 'root')}</div>;
};
