import React, { useState, useEffect, useContext, useMemo } from 'react';
import { 
  FileCode, 
  Eye, 
  Hash,
  Calendar,
  ChevronDown,
  ChevronRight,
  GitBranch,
  Database,
  TrendingUp
} from 'lucide-react';
import { ConfigContext } from '../ConfigContext';
import { RunsApi, SolutionsApi, ModelsApi } from '../services/api';
import CodeEditor from './editor/CodeEditor';
import { formatScore } from '../utils/formatters';
import SolutionsBrowser from './SolutionsBrowser';

// Enhanced JSON viewer component with expandable keys
const MetadataViewer = ({ data, level = 0 }) => {
  const [expandedKeys, setExpandedKeys] = useState(() => {
    const initialExpanded = new Set();
    if (level === 0 && data && typeof data === 'object' && !Array.isArray(data)) {
      initialExpanded.add('root');
    }
    return initialExpanded;
  });
  
  const toggleKey = (key) => {
    const newExpanded = new Set(expandedKeys);
    if (newExpanded.has(key)) newExpanded.delete(key); else newExpanded.add(key);
    setExpandedKeys(newExpanded);
  };

  const renderValue = (value, key = null, currentLevel = level) => {
    if (value === null || value === undefined) return <span className="json-null">null</span>;
    if (typeof value === 'string') {
      if (value.includes('\n')) return <pre className="json-string multiline">{value}</pre>;
      return <span className="json-string">{value}</span>;
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
                  {idx < value.length - 1 && <span className="json-comma">,</span>}
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
                  {idx < entries.length - 1 && <span className="json-comma">,</span>}
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

const SolutionsView = ({ config }) => {
  const { solutions, fetchSolutions } = useContext(ConfigContext);
  const [selectedSolution, setSelectedSolution] = useState(null);
  const [expandedHistoryItems, setExpandedHistoryItems] = useState(new Set());
  const [selectedDiffFile, setSelectedDiffFile] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [showMetadataModal, setShowMetadataModal] = useState(false);
  const [browserSolutions, setBrowserSolutions] = useState([]);
  const configSolutions = browserSolutions.length > 0 ? browserSolutions : (solutions[config?.id] || []);

  // Reset selection when list context changes (browser refetch will call onSolutionsLoaded)
  useEffect(() => {
    setSelectedSolution(null);
    setExpandedHistoryItems(new Set());
    setSelectedDiffFile(null);
    setSelectedFile(null);
  }, [config?.id]);

  const bestSolutionForDisplay = useMemo(() => {
    if (!Array.isArray(configSolutions) || configSolutions.length === 0) return null;
    // Use the first metric key if provided, else 'score'
    const metricKeys = config?.config?.metrics ? Object.keys(config.config.metrics) : [];
    const key = metricKeys[0] || 'score';
    return configSolutions.reduce((best, cur) => {
      const b = best?.fitness?.[key] ?? best?.fitness?.score;
      const c = cur?.fitness?.[key] ?? cur?.fitness?.score;
      if (b == null) return cur;
      if (c == null) return best;
      return c > b ? cur : best;
    }, null);
  }, [configSolutions, config?.config?.metrics]);

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    try { return new Date(dateString).toLocaleString(); } catch { return String(dateString); }
  };

  const findSolutionById = (id) => configSolutions.find(sol => sol.id === id);

  const getEvolutionHistory = (solution) => {
    const history = [];
    let current = solution;
    const visited = new Set();
    while (current && current.parentId && !visited.has(current.id)) {
      visited.add(current.id);
      const parent = findSolutionById(current.parentId);
      if (parent) { history.push(parent); current = parent; } else { break; }
    }
    return history;
  };

  const toggleHistoryItem = (itemId) => {
    const s = new Set(expandedHistoryItems);
    if (s.has(itemId)) s.delete(itemId); else s.add(itemId);
    setExpandedHistoryItems(s);
  };

  const handleFileSelect = (currentSolution, parentSolution, filename) => {
    setSelectedDiffFile({ filename, currentContent: currentSolution.solution?.files?.[filename] || '', parentContent: parentSolution.solution?.files?.[filename] || '', currentSolution, parentSolution });
  };

  const handleFileView = (solution, filename) => {
    setSelectedFile({ filename, content: solution.solution?.files?.[filename] || '', solution });
  };

  return (
    <div className="solutions-view">
      <SolutionsBrowser
        problemId={config?.id}
        metrics={config?.config?.metrics}
        onRowClick={setSelectedSolution}
        highlightIds={selectedSolution ? [selectedSolution.id] : []}
        onSolutionsLoaded={setBrowserSolutions}
        renderActions={(solution) => (
          <button
            className="oe-btn ghost sm"
            onClick={() => setSelectedSolution(solution)}
            title="View details"
          >
            <Eye size={14} />
          </button>
        )}
      />

      {selectedSolution && (
        <div className="solution-details">
          <div className="details-header">
            <h4>Solution Details</h4>
            <button className="oe-btn ghost sm" onClick={() => setSelectedSolution(null)}>×</button>
          </div>
          <div className="details-content">
            <div className="detail-section">
              <h5>Basic Info</h5>
              <div className="detail-grid">
                <div className="detail-item"><span className="detail-label">ID:</span><code>{selectedSolution.id}</code></div>
                <div className="detail-item"><span className="detail-label">Parent ID:</span><code>{selectedSolution.parentId || 'None'}</code></div>
                <div className="detail-item"><span className="detail-label">LLM Model:</span><span>{selectedSolution.solution?.metadata?.llmModel || '-'}</span></div>
                <div className="detail-item"><button className="oe-btn outline sm" onClick={() => setShowMetadataModal(true)}>View Metadata</button></div>
              </div>
            </div>
            {selectedSolution.fitness && (
              <div className="detail-section">
                <h5>Fitness</h5>
                <div className="detail-grid">
                  <div className="detail-item"><span className="detail-label">Value:</span><code className="json-code">{JSON.stringify(selectedSolution.fitness, null, 2)}</code></div>
                </div>
              </div>
            )}
            {(() => {
              const evolutionHistory = getEvolutionHistory(selectedSolution);
              return evolutionHistory.length > 0 && (
                <div className="detail-section">
                  <h5><GitBranch size={16} /> Evolution History ({evolutionHistory.length} generations)</h5>
                  <div className="evolution-history">
                    {evolutionHistory.map((historySolution) => {
                      const isExpanded = expandedHistoryItems.has(historySolution.id);
                      const hasFiles = historySolution.solution?.files && Object.keys(historySolution.solution.files).length > 0;
                      return (
                        <div key={historySolution.id} className="history-item">
                          <div className="history-header" onClick={() => hasFiles && toggleHistoryItem(historySolution.id)} style={{ cursor: hasFiles ? 'pointer' : 'default' }}>
                            <div className="history-info">
                              {hasFiles ? (isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />) : (<div style={{ width: 14 }} />)}
                              <code className="history-id">{historySolution.id?.substring(0, 8)}</code>
                              <span className="history-score">Score: {formatScore(historySolution.fitness, config?.config?.metrics)}</span>
                            </div>
                          </div>
                          {isExpanded && hasFiles && (
                            <div className="history-files">
                              {Object.keys(historySolution.solution.files).map(filename => (
                                <div key={filename} className="history-file-item" onClick={() => handleFileSelect(selectedSolution, historySolution, filename)}>
                                  <FileCode size={12} />
                                  <span>{filename}</span>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })()}
            {selectedSolution.solution?.files && Object.keys(selectedSolution.solution.files).length > 0 && (
              <div className="detail-section">
                <h5>Files ({Object.keys(selectedSolution.solution.files).length})</h5>
                <div className="files-list">
                  {Object.keys(selectedSolution.solution.files).map(filename => (
                    <div key={filename} className="file-item clickable" onClick={() => handleFileView(selectedSolution, filename)}>
                      <FileCode size={14} />
                      <span>{filename}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {selectedDiffFile && (
        <div className="diff-modal-overlay" onClick={() => setSelectedDiffFile(null)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>File Diff: {selectedDiffFile.filename}</h4>
              <div className="diff-info">
                <span className="diff-label">Current ({selectedDiffFile.currentSolution.id?.substring(0, 8)}) ↔ Parent ({selectedDiffFile.parentSolution.id?.substring(0, 8)})</span>
              </div>
              <button className="oe-btn ghost sm" onClick={() => setSelectedDiffFile(null)}>×</button>
            </div>
            <div className="diff-modal-content">
              <CodeEditor isDiff={true} originalCode={selectedDiffFile.parentContent} modifiedCode={selectedDiffFile.currentContent} language="javascript" height="70vh" options={{ readOnly: true, renderSideBySide: true }} />
            </div>
          </div>
        </div>
      )}

      {selectedFile && (
        <div className="diff-modal-overlay" onClick={() => setSelectedFile(null)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>File: {selectedFile.filename}</h4>
              <div className="diff-info">
                <span className="diff-label">Solution: {selectedFile.solution.id?.substring(0, 8)}</span>
              </div>
              <button className="oe-btn ghost sm" onClick={() => setSelectedFile(null)}>×</button>
            </div>
            <div className="diff-modal-content">
              <CodeEditor code={selectedFile.content} language="javascript" height="70vh" options={{ readOnly: true }} />
            </div>
          </div>
        </div>
      )}

      {showMetadataModal && selectedSolution && (
        <div className="diff-modal-overlay" onClick={() => setShowMetadataModal(false)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>Solution Metadata</h4>
              <button className="oe-btn ghost sm" onClick={() => setShowMetadataModal(false)}>×</button>
            </div>
            <div className="diff-modal-content">
              <div className="metadata-modal-content">
                <MetadataViewer data={selectedSolution.solution?.metadata} />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default SolutionsView;
