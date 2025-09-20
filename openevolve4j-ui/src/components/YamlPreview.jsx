import React, { useState } from 'react';
import { Eye, Copy } from 'lucide-react';
import yaml from 'js-yaml';

const YamlPreview = ({ config, isOpen, onClose }) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const generateYaml = () => {
    // Create a clean config object for YAML export
    const cleanConfig = {
      type: config.type || 'MAPELITES',
      promptPath: config.promptPath,
      llm: config.llm,
      solution: config.solution,
      // MAPELITES fields
      selection: config.selection,
      migration: config.migration,
      repository: config.repository,
      mapelites: config.mapelites,
      // TREE fields
      llmGroups: config.llmGroups,
      iterations: config.iterations,
      explorationConstant: config.explorationConstant,
      metrics: config.metrics
    };
    
    return yaml.dump(cleanConfig, { 
      indent: 2,
      lineWidth: -1,
      noRefs: true,
      sortKeys: false
    });
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(generateYaml());
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  return (
    <div className="yaml-preview-overlay" onClick={onClose}>
      <div className="yaml-preview-modal" onClick={e => e.stopPropagation()}>
        <div className="yaml-preview-header">
          <h3>YAML Configuration Preview</h3>
          <div className="yaml-preview-actions">
            <button
              onClick={handleCopy}
              className="btn btn-secondary"
              title="Copy to clipboard"
            >
              <Copy size={16} />
              {copied ? 'Copied!' : 'Copy'}
            </button>
            <button onClick={onClose} className="btn btn-secondary">
              Close
            </button>
          </div>
        </div>
        <div className="yaml-preview-content">
          <pre><code>{generateYaml()}</code></pre>
        </div>
      </div>
    </div>
  );
};

export default YamlPreview;
