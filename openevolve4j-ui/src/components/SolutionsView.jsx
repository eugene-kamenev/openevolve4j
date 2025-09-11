import React, { useState, useEffect, useContext } from 'react';
import { 
  FileCode, 
  Play, 
  Clock, 
  TrendingUp, 
  Database, 
  RefreshCw, 
  Eye, 
  Download,
  Hash,
  Calendar,
  Target,
  Layers,
  ChevronDown,
  ChevronRight,
  GitBranch
} from 'lucide-react';
import { ConfigContext } from '../App';
import CodeEditor from './editor/CodeEditor';

const SolutionsView = ({ config }) => {
  const { solutions, setSolutions, sendWsRequest } = useContext(ConfigContext);
  const [loading, setLoading] = useState(false);
  const [selectedSolution, setSelectedSolution] = useState(null);
  const [sortField, setSortField] = useState('iteration');
  const [sortDirection, setSortDirection] = useState('desc');
  const [expandedHistoryItems, setExpandedHistoryItems] = useState(new Set());
  const [selectedDiffFile, setSelectedDiffFile] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);

  const configSolutions = solutions[config?.id] || [];

  // Fetch solutions when component mounts or config changes
  useEffect(() => {
    if (config?.id) {
      fetchSolutions();
    }
  }, [config?.id]);

  const fetchSolutions = async () => {
    if (!config?.id) return;
    
    setLoading(true);
    try {
      sendWsRequest({
        type: 'GET_SOLUTIONS',
        id: config.id
      }).then(response => {
        if (response.id && response.solutions) {
          setSolutions(prev => ({ ...prev, [response.id]: response.solutions }));
        }
      });
    } catch (error) {
      console.error('Error fetching solutions:', error);
    } finally {
      setLoading(false);
    }
  };

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
      return new Date(dateString).toLocaleString();
    } catch {
      return dateString;
    }
  };

  const formatScore = (fitness) => {
    if (!fitness || typeof fitness.score !== 'number') return '-';
    return fitness.score.toFixed(3);
  };

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
          <div className="header-actions">
            <button 
              className="oe-btn outline" 
              onClick={fetchSolutions}
              disabled={loading}
            >
              <RefreshCw size={16} className={loading ? 'spinning' : ''} />
              Refresh
            </button>
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
              {configSolutions.length > 0 ? Math.max(...configSolutions.map(s => s.iteration || 0)) : 0}
            </div>
            <div className="stat-label">Max Iteration</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {configSolutions.length > 0 
                ? formatScore({ score: Math.max(...configSolutions.map(s => s.fitness?.score || 0)) })
                : '-'
              }
            </div>
            <div className="stat-label">Best Score</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {new Set(configSolutions.map(s => s.islandId)).size}
            </div>
            <div className="stat-label">Active Islands</div>
          </div>
        </div>
      </div>

      {/* Solutions Table */}
      <div className="solutions-table-container">
        {loading && configSolutions.length === 0 ? (
          <div className="loading-state">
            <RefreshCw size={20} className="spinning" />
            <span>Loading solutions...</span>
          </div>
        ) : configSolutions.length === 0 ? (
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
                    className={`sortable ${sortField === 'iteration' ? sortDirection : ''}`}
                    onClick={() => handleSort('iteration')}
                  >
                    <Target size={14} /> Iteration
                  </th>
                  <th 
                    className={`sortable ${sortField === 'score' ? sortDirection : ''}`}
                    onClick={() => handleSort('score')}
                  >
                    <TrendingUp size={14} /> Score
                  </th>
                  <th 
                    className={`sortable ${sortField === 'islandId' ? sortDirection : ''}`}
                    onClick={() => handleSort('islandId')}
                  >
                    <Layers size={14} /> Island
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
                      <td className="iteration">
                        <span className="iteration-badge">{solution.iteration || 0}</span>
                      </td>
                      <td className="score">
                        <span className="score-value">{JSON.stringify(solution.fitness)}</span>
                      </td>
                      <td className="island">
                        <span className="island-badge">#{solution.islandId || 0}</span>
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
                  <span className="detail-label">Iteration:</span>
                  <span>{selectedSolution.iteration}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Island:</span>
                  <span>#{selectedSolution.islandId}</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Parent ID:</span>
                  <code>{selectedSolution.solution?.parentId || 'None'}</code>
                </div>
              </div>
            </div>
            
            {selectedSolution.fitness && (
              <div className="detail-section">
                <h5>Fitness</h5>
                <div className="detail-grid">
                  <div className="detail-item">
                    <span className="detail-label">Value:</span>
                    <code className="metric-value">{JSON.stringify(selectedSolution.fitness, null, 2)}</code>
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
                              <span className="history-iteration">Iter: {historySolution.iteration}</span>
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
    </div>
  );
};

export default SolutionsView;
