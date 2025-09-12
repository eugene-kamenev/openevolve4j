// Enhanced JSON viewer component with expandable keys
const MetadataViewer = ({ data, level = 0, name = null }) => {
  const [expandedKeys, setExpandedKeys] = useState(new Set());
  
  const toggleKey = (key) => {
    const newExpanded = new Set(expandedKeys);
    if (newExpanded.has(key)) {
      newExpanded.delete(key);
    } else {
      newExpanded.add(key);
    }
    setExpandedKeys(newExpanded);
  };

  const renderValue = (value, key = null, currentLevel = level) => {
    if (value === null || value === undefined) {
      return <span className="json-null">null</span>;
    }
    
    if (typeof value === 'string') {
      // Check if string contains newlines
      if (value.includes('\n')) {
        return <pre className="json-string multiline">{value}</pre>;
      }
      return <span className="json-string">{value}</span>;
    }
    
    if (typeof value === 'number') {
      return <span className="json-number">{value}</span>;
    }
    
    if (typeof value === 'boolean') {
      return <span className="json-boolean">{String(value)}</span>;
    }
    
    if (Array.isArray(value)) {
      const arrayKey = key || 'array';
      const isExpanded = expandedKeys.has(arrayKey);
      const isEmpty = value.length === 0;
      
      if (isEmpty) {
        return <span className="json-bracket">[]</span>;
      }
      
      return (
        <div className="json-container">
          <div 
            className="json-header clickable" 
            onClick={() => toggleKey(arrayKey)}
          >
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
      
      if (isEmpty) {
        return <span className="json-bracket">{'{}'}</span>;
      }
      
      return (
        <div className="json-container">
          <div 
            className="json-header clickable" 
            onClick={() => toggleKey(objectKey)}
          >
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

  return (
    <div className="json-viewer" style={{ '--level': level }}>
      {name && <div className="json-root-name">{name}</div>}
      {renderValue(data, name || 'root')}
    </div>
  );
};
import React, { useState, useEffect, useContext } from 'react';
import { 
  FileCode, 
  Play, 
  Clock, 
  TrendingUp, 
  Database, 
  Eye, 
  Download,
  Hash,
  Calendar,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  GitBranch
} from 'lucide-react';
import { ConfigContext } from '../App';
import CodeEditor from './editor/CodeEditor';
import { formatScore } from '../utils/formatters';

const SolutionsView = ({ config }) => {
  const { solutions, setSolutions, bestSolutions, sendWsRequest, fetchSolutions } = useContext(ConfigContext);
  const [selectedSolution, setSelectedSolution] = useState(null);
  const [sortField, setSortField] = useState('iteration');
  const [sortDirection, setSortDirection] = useState('desc');
  const [expandedHistoryItems, setExpandedHistoryItems] = useState(new Set());
  const [selectedDiffFile, setSelectedDiffFile] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [showMetadataModal, setShowMetadataModal] = useState(false);

  const configSolutions = solutions[config?.id] || [];

  const handleSort = (field) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('desc');
    }
  };

  const sortedSolutions = [...configSolutions].sort((a, b) => {
    let aValue = a[sortField];
    let bValue = b[sortField];
    
    // Handle nested properties
    if (sortField === 'score') {
      aValue = a.fitness?.score || 0;
      bValue = b.fitness?.score || 0;
    } else if (sortField === 'dateCreated') {
      aValue = a.solution?.dateCreated || '';
      bValue = b.solution?.dateCreated || '';
    }
    
    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
    return 0;
  });

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    try {
      return new Date(dateString / 1000).toLocaleString();
    } catch {
      return dateString;
    }
  };

  // use shared formatScore util

  // Helper function to find a solution by ID
  const findSolutionById = (id) => {
    return configSolutions.find(sol => sol.id === id);
  };

  // Helper function to get evolution history (recursive parent lookup)
  const getEvolutionHistory = (solution) => {
    const history = [];
    let current = solution;
    let visited = new Set(); // Prevent infinite loops
    
    while (current && current.solution?.parentId && !visited.has(current.id)) {
      visited.add(current.id);
      const parent = findSolutionById(current.solution.parentId);
      if (parent) {
        history.push(parent);
        current = parent;
      } else {
        break;
      }
    }
    
    return history;
  };

  // Helper function to toggle history item expansion
  const toggleHistoryItem = (itemId) => {
    const newExpanded = new Set(expandedHistoryItems);
    if (newExpanded.has(itemId)) {
      newExpanded.delete(itemId);
    } else {
      newExpanded.add(itemId);
    }
    setExpandedHistoryItems(newExpanded);
  };

  // Helper function to handle file selection for diff view
  const handleFileSelect = (currentSolution, parentSolution, filename) => {
    setSelectedDiffFile({
      filename,
      currentContent: currentSolution.solution?.files?.[filename] || '',
      parentContent: parentSolution.solution?.files?.[filename] || '',
      currentSolution,
      parentSolution
    });
  };

  // Helper function to handle regular file viewing
  const handleFileView = (solution, filename) => {
    setSelectedFile({
      filename,
      content: solution.solution?.files?.[filename] || '',
      solution
    });
  };

  return (
    <div className="solutions-view">
      <div className="solutions-header">
        <div className="header-content">
          <div className="header-info">
            <h3><Database size={20} /> Solutions</h3>
            <p className="text-dim">
              Evolution history for <span className="text-accent">{config?.name}</span>
            </p>
          </div>
        </div>

        {/* Quick Stats */}
        <div className="solutions-stats">
          <div className="stat-item">
            <div className="stat-value">{configSolutions.length}</div>
            <div className="stat-label">Total Solutions</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {(() => {
                const bestSolution = bestSolutions[config?.id];
                if (bestSolution) {
                  const shortId = bestSolution.id?.substring(0, 8) || 'unknown';
                  const score = formatScore(bestSolution.fitness, config?.config?.metrics);
                  return `${shortId}: ${score}`;
                }
                return configSolutions.length > 0 ? 'No best solution set' : '-';
              })()}
            </div>
            <div className="stat-label">Best Score</div>
          </div>
        </div>
      </div>

      {/* Solutions Table */}
      <div className="solutions-table-container">
        {configSolutions.length === 0 ? (
          <div className="empty-state">
            <Database size={48} />
            <h4>No Solutions Yet</h4>
            <p className="text-dim">Solutions will appear here once evolution starts</p>
          </div>
        ) : (
          <div className="solutions-table-wrapper">
            <table className="solutions-table">
              <thead>
                <tr>
                  <th 
                    className={`sortable ${sortField === 'id' ? sortDirection : ''}`}
                    onClick={() => handleSort('id')}
                  >
                    <Hash size={14} /> ID
                  </th>
                  <th 
                    className={`sortable ${sortField === 'score' ? sortDirection : ''}`}
                  >
                    <TrendingUp size={14} /> Score {config?.config?.metrics ? `(${Object.keys(config.config.metrics).join(', ')})` : ''}
                  </th>
                  <th 
                    className={`sortable ${sortField === 'dateCreated' ? sortDirection : ''}`}
                    onClick={() => handleSort('dateCreated')}
                  >
                    <Calendar size={14} /> Created
                  </th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedSolutions.map((solution) => {
                  return (
                    <tr 
                      key={solution.id}
                      className={selectedSolution?.id === solution.id ? 'selected' : ''}
                      onClick={() => setSelectedSolution(solution)}
                    >
                      <td className="solution-id">
                        <code>{solution.id?.substring(0, 8) || '-'}</code>
                      </td>
                      <td className="score">
                        <span className="score-value">{formatScore(solution.fitness, config.config.metrics)}</span>
                      </td>
                      <td className="created">
                        <span className="date-text">
                          {formatDate(solution.solution?.dateCreated)}
                        </span>
                      </td>
                      <td className="actions">
                        <button 
                          className="oe-btn ghost sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedSolution(solution);
                          }}
                        >
                          <Eye size={14} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Solution Details Panel */}
      {selectedSolution && (
        <div className="solution-details">
          <div className="details-header">
            <h4>Solution Details</h4>
            <button 
              className="oe-btn ghost sm"
              onClick={() => setSelectedSolution(null)}
            >
              ×
            </button>
          </div>
          <div className="details-content">
            <div className="detail-section">
              <h5>Basic Info</h5>
              <div className="detail-grid">
                <div className="detail-item">
                  <span className="detail-label">ID:</span>
                  <code>{selectedSolution.id}</code>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Parent ID:</span>
                  <code>{selectedSolution.solution?.parentId || 'None'}</code>
                </div>
                <div className="detail-item">
                  <span className="detail-label">LLM Model:</span>
                  <span>{selectedSolution.solution?.metadata?.llmModel || '-'}</span>
                </div>
                <div className="detail-item">
                  <button
                    className="oe-btn outline sm"
                    onClick={() => setShowMetadataModal(true)}
                  >
                    View Metadata
                  </button>
                </div>
              </div>
            </div>
            {selectedSolution.fitness && (
              <div className="detail-section">
                <h5>Fitness</h5>
                <div className="detail-grid">
                  <div className="detail-item">
                    <span className="detail-label">Value:</span>
                    <code className="json-code">{JSON.stringify(selectedSolution.fitness, null, 2)}</code>
                  </div>
                </div>
              </div>
            )}
            {/* Evolution History Section */}
            {(() => {
              const evolutionHistory = getEvolutionHistory(selectedSolution);
              return evolutionHistory.length > 0 && (
                <div className="detail-section">
                  <h5><GitBranch size={16} /> Evolution History ({evolutionHistory.length} generations)</h5>
                  <div className="evolution-history">
                    {evolutionHistory.map((historySolution, index) => {
                      const isExpanded = expandedHistoryItems.has(historySolution.id);
                      const hasFiles = historySolution.solution?.files && Object.keys(historySolution.solution.files).length > 0;
                      return (
                        <div key={historySolution.id} className="history-item">
                          <div 
                            className="history-header"
                            onClick={() => hasFiles && toggleHistoryItem(historySolution.id)}
                            style={{ cursor: hasFiles ? 'pointer' : 'default' }}
                          >
                            <div className="history-info">
                              {hasFiles ? (
                                isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />
                              ) : (
                                <div style={{ width: 14 }} />
                              )}
                              <span className="history-generation">Gen {evolutionHistory.length - index}</span>
                              <code className="history-id">{historySolution.id?.substring(0, 8)}</code>
                              <span className="history-score">Score: {formatScore(historySolution.fitness)}</span>
                            </div>
                          </div>
                          {isExpanded && hasFiles && (
                            <div className="history-files">
                              {Object.keys(historySolution.solution.files).map(filename => (
                                <div 
                                  key={filename}
                                  className="history-file-item"
                                  onClick={() => handleFileSelect(selectedSolution, historySolution, filename)}
                                >
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
                    <div 
                      key={filename} 
                      className="file-item clickable"
                      onClick={() => handleFileView(selectedSolution, filename)}
                    >
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
      {/* Diff View Modal */}
      {selectedDiffFile && (
        <div className="diff-modal-overlay" onClick={() => setSelectedDiffFile(null)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>
                File Diff: {selectedDiffFile.filename}
              </h4>
              <div className="diff-info">
                <span className="diff-label">
                  Current ({selectedDiffFile.currentSolution.id?.substring(0, 8)}) ↔ 
                  Parent ({selectedDiffFile.parentSolution.id?.substring(0, 8)})
                </span>
              </div>
              <button 
                className="oe-btn ghost sm"
                onClick={() => setSelectedDiffFile(null)}
              >
                ×
              </button>
            </div>
            <div className="diff-modal-content">
              <CodeEditor
                isDiff={true}
                originalCode={selectedDiffFile.parentContent}
                modifiedCode={selectedDiffFile.currentContent}
                language="javascript"
                height="70vh"
                options={{
                  readOnly: true,
                  renderSideBySide: true
                }}
              />
            </div>
          </div>
        </div>
      )}
      {/* File Viewer Modal */}
      {selectedFile && (
        <div className="diff-modal-overlay" onClick={() => setSelectedFile(null)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>
                File: {selectedFile.filename}
              </h4>
              <div className="diff-info">
                <span className="diff-label">
                  Solution: {selectedFile.solution.id?.substring(0, 8)}
                </span>
              </div>
              <button 
                className="oe-btn ghost sm"
                onClick={() => setSelectedFile(null)}
              >
                ×
              </button>
            </div>
            <div className="diff-modal-content">
              <CodeEditor
                code={selectedFile.content}
                language="javascript"
                height="70vh"
                options={{
                  readOnly: true
                }}
              />
            </div>
          </div>
        </div>
      )}
      {/* Metadata Modal */}
      {showMetadataModal && selectedSolution && (
        <div className="diff-modal-overlay" onClick={() => setShowMetadataModal(false)}>
          <div className="diff-modal" onClick={(e) => e.stopPropagation()}>
            <div className="diff-modal-header">
              <h4>Solution Metadata</h4>
              <button
                className="oe-btn ghost sm"
                onClick={() => setShowMetadataModal(false)}
              >
                ×
              </button>
            </div>
            <div className="diff-modal-content">
              <div className="metadata-modal-content">
                <MetadataViewer data={selectedSolution.solution?.metadata} name="metadata" />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default SolutionsView;
