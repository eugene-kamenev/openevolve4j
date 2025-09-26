import React, { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  ChevronDown,
  ChevronRight,
  FileText,
  Hash,
  Info,
  Layers,
  X
} from 'lucide-react';
import Modal from './Modal';
import ObjectViewer from './ObjectViewer';
import CodeEditor from './CodeEditor';
import { fetchSolutionAncestors } from '../services/problems.js';

function formatFitness(metrics, fitness = {}) {
  if (fitness?.error) {
    return `Error: ${fitness.error}`;
  }
  return Object.keys(metrics)
    .map((m) => `${m}: ${typeof fitness[m] === 'number' ? fitness[m].toFixed(3) : fitness[m]}`).join(', ');
}

function truncateId(id) {
  if (!id) return '—';
  return id.length > 8 ? `${id.substring(0, 8)}` : id;
}

function getLanguageFromFilename(name) {
  if (!name) return 'plaintext';
  const ext = name.split('.').pop().toLowerCase();
  switch (ext) {
    case 'js':
    case 'jsx':
      return 'javascript';
    case 'ts':
    case 'tsx':
      return 'typescript';
    case 'py':
      return 'python';
    case 'java':
      return 'java';
    case 'json':
      return 'json';
    case 'xml':
      return 'xml';
    case 'md':
      return 'markdown';
    case 'sql':
      return 'sql';
    case 'html':
      return 'html';
    case 'css':
      return 'css';
    case 'yaml':
    case 'yml':
      return 'yaml';
    case 'sh':
    case 'bash':
      return 'shell';
    case 'txt':
    default:
      return 'plaintext';
  }
}

function CollapsibleSection({ title, icon: Icon, defaultOpen = true, children, badge, isOpen, onToggle }) {
  const [open, setOpen] = useState(isOpen ?? defaultOpen);
  const IconComponent = Icon ?? Info;

  useEffect(() => {
    if (typeof isOpen === 'boolean') setOpen(isOpen);
  }, [isOpen]);

  function handleToggle() {
    if (typeof onToggle === 'function') {
      onToggle(!open);
    } else {
      setOpen((p) => !p);
    }
  }

  return (
    <section className="solution-section">
      <button
        type="button"
        className="solution-section__trigger"
        onClick={handleToggle}
        aria-expanded={open}
      >
        <span className="solution-section__title">
          <IconComponent size={16} />
          {title}
        </span>
        <span className="solution-section__meta">
          {badge ? <span className="section-badge">{badge}</span> : null}
          {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        </span>
      </button>
      {open && <div className="solution-section__body">{children}</div>}
    </section>
  );
}

function ActionSection({ title, icon: Icon, badge, onClick }) {
  const IconComponent = Icon ?? Info;
  return (
    <section className="solution-section">
      <button
        type="button"
        className="solution-section__trigger"
        onClick={onClick}
        aria-expanded={false}
      >
        <span className="solution-section__title">
          <IconComponent size={16} />
          {title}
        </span>
        <span className="solution-section__meta">
          {badge ? <span className="section-badge">{badge}</span> : null}
          <ChevronRight size={16} />
        </span>
      </button>
    </section>
  );
}

export default function SolutionDetailsPanel({ problem, solution, onClose }) {
  const payload = solution?.payload ?? {};
  const metrics = problem?.config?.metrics ?? {};
  const files = useMemo(() => payload?.data?.files ?? {}, [payload]);
  const fileEntries = useMemo(() => Object.entries(files), [files]);
  const hasFiles = fileEntries.length > 0;
  const metadata = payload?.data?.metadata ?? null;
  const [activeFile, setActiveFile] = useState(null);
  const [filesOpen, setFilesOpen] = useState(false);
  const [metaOpen, setMetaOpen] = useState(false);
  const [ancestors, setAncestors] = useState(null);
  const [ancestorsError, setAncestorsError] = useState(null);
  const [diffOpen, setDiffOpen] = useState(false);
  const [diffBaseId, setDiffBaseId] = useState(null);
  const [diffTargetId, setDiffTargetId] = useState(null);
  const [diffFile, setDiffFile] = useState(null);

  useEffect(() => {
    if (hasFiles) {
      setActiveFile(fileEntries[0]?.[0] ?? null);
    } else {
      setActiveFile(null);
    }
  }, [solution, hasFiles, fileEntries]);

  const versionOptions = useMemo(() => {
    const options = [];
    if (payload) {
      const currentId = payload.id ?? (solution?.id ?? 'current');
      options.push({
        id: currentId,
        label: `Current · ${truncateId(currentId)}`,
        files: payload?.data?.files ?? {},
        payload
      });
    }
    if (Array.isArray(ancestors)) {
      ancestors.forEach((anc, index) => {
        const ancestorId = anc?.id ?? `ancestor-${index + 1}`;
        options.push({
          id: ancestorId,
          label: `Ancestor ${index + 1} · ${truncateId(ancestorId)}`,
          files: anc?.data?.files ?? {},
          payload: anc
        });
      });
    }
    return options;
  }, [payload, solution?.id, ancestors]);

  const versionMap = useMemo(() => (
    versionOptions.reduce((acc, option) => {
      if (option.id != null) {
        acc[option.id] = option;
      }
      return acc;
    }, {})
  ), [versionOptions]);

  const currentVersionId = payload?.id ?? solution?.id ?? null;

  const diffFileNames = useMemo(() => {
    if (!diffOpen) return [];
    const baseFiles = diffBaseId ? versionMap[diffBaseId]?.files ?? {} : {};
    const targetFiles = diffTargetId ? versionMap[diffTargetId]?.files ?? {} : {};
    const names = new Set([...Object.keys(baseFiles), ...Object.keys(targetFiles)]);
    return Array.from(names).sort((a, b) => a.localeCompare(b));
  }, [diffOpen, versionMap, diffBaseId, diffTargetId]);

  useEffect(() => {
    if (!diffOpen) return;
    if (diffFileNames.length === 0) {
      setDiffFile(null);
      return;
    }
    if (!diffFileNames.includes(diffFile ?? '')) {
      setDiffFile(diffFileNames[0]);
    }
  }, [diffOpen, diffFileNames, diffFile]);

  useEffect(() => {
    if (!diffOpen) return;
    if (diffBaseId && versionMap[diffBaseId]) return;
    if (versionOptions.length) {
      setDiffBaseId(versionOptions[0].id);
    }
  }, [diffOpen, diffBaseId, versionMap, versionOptions]);

  useEffect(() => {
    if (!diffOpen) return;
    if (diffTargetId && versionMap[diffTargetId]) return;
    if (versionOptions.length) {
      const fallback = versionOptions.find((option) => option.id !== diffBaseId) ?? versionOptions[0];
      setDiffTargetId(fallback?.id ?? null);
    }
  }, [diffOpen, diffTargetId, diffBaseId, versionMap, versionOptions]);

  const baseVersion = diffBaseId ? versionMap[diffBaseId] : null;
  const targetVersion = diffTargetId ? versionMap[diffTargetId] : null;

  useEffect(() => {
    let mounted = true;
    async function loadAncestors() {
      setAncestors(null);
      setAncestorsError(null);
      const problemId = payload?.problemId || solution?.problemId || payload?.data?.problemId;
      const solutionId = payload?.id || solution?.id;
      if (!problemId || !solutionId) {
        setAncestorsError('Problem ID or Solution ID not available');
        setAncestors([]);
        return;
      }
      try {
        const data = await fetchSolutionAncestors(problemId, solutionId);
        if (!mounted) return;
        setAncestors(Array.isArray(data) ? data : []);
      } catch (err) {
        if (!mounted) return;
        setAncestorsError(err.message || String(err));
      }
    }

    if (solution) {
      loadAncestors();
    }

    return () => {
      mounted = false;
    };
  }, [payload, solution]);

  if (!solution) return null;

  const fitnessEntries = Object.entries(payload?.fitness ?? {});
  const activeFileContent = activeFile ? files[activeFile] : '';

  return (
    <aside className="solution-details-panel" aria-label="Solution details">
      <header className="solution-details-header">
        <div>
          <p className="solution-details-eyebrow">Solution</p>
          <h3 className="solution-details-title" title={payload?.id}>{truncateId(payload?.id)}</h3>
          <p className="solution-details-subtitle">
            {solution?.dateCreated ? new Date(solution.dateCreated).toLocaleString() : 'Creation date unknown'}
          </p>
        </div>
        <button type="button" className="solution-details-close" onClick={onClose} aria-label="Clear selection">
          <X size={16} />
        </button>
      </header>

      <div className="solution-details-tags">
        {payload?.runId && (
          <span className="pill"><Activity size={14} /> Run {truncateId(payload.runId)}</span>
        )}
        {payload?.parentId && (
          <span className="pill"><Hash size={14} /> Parent {truncateId(payload.parentId)}</span>
        )}
        {payload?.data?.metadata?.llmModel && (
          <span className="pill"><Layers size={14} /> {payload.data.metadata.llmModel}</span>
        )}
      </div>

      <div className="solution-details-scroll">
        {/* Summary removed per request - keep the panel focused on Fitness, Source files and Metadata (modals) */}

        {fitnessEntries.length > 0 && (
          <CollapsibleSection
            title="Fitness metrics"
            icon={Activity}
            defaultOpen={false}
            badge={fitnessEntries.length}
          >
            <div className="solution-fitness-grid">
              {fitnessEntries.map(([metric, value]) => (
                <div key={metric} className="fitness-chip">
                  <span>{metric}</span>
                  <strong>{typeof value === 'number' ? value.toFixed(3) : value}</strong>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}

        <ActionSection
          title="Source files"
          icon={FileText}
          badge={hasFiles ? fileEntries.length : '0'}
          onClick={() => setFilesOpen(true)}
        />

        <ActionSection
          title="Metadata"
          icon={Layers}
          badge={metadata ? Object.keys(metadata).length : '0'}
          onClick={() => setMetaOpen(true)}
        />

        <CollapsibleSection
          title="Ancestors"
          icon={Hash}
          defaultOpen={false}
          badge={ancestors ? ancestors.length : '0'}
        >
          <div style={{ display: 'block' }}>
            {ancestorsError ? (
              <div style={{ padding: 12, color: 'var(--color-text-muted)' }}>{ancestorsError}</div>
            ) : ancestors === null ? (
              <div style={{ padding: 12 }}>Loading ancestors…</div>
            ) : ancestors.length === 0 ? (
              <div style={{ padding: 12 }}>No ancestors found for this solution.</div>
            ) : (
              <div style={{ display: 'grid', gap: 6 }}>
                {ancestors.map((anc) => (
                  <button
                    key={anc.id || JSON.stringify(anc)}
                    type="button"
                    onClick={() => {
                      const ancestorId = anc?.id ?? null;
                      if (ancestorId && versionMap[ancestorId]) {
                        setDiffBaseId(ancestorId);
                        setDiffTargetId((currentVersionId && currentVersionId !== ancestorId) ? currentVersionId : (versionOptions.find((opt) => opt.id !== ancestorId)?.id ?? ancestorId));
                      } else if (versionOptions.length) {
                        const fallbackBase = versionOptions.find((opt) => opt.payload === anc)?.id ?? versionOptions[0].id;
                        setDiffBaseId(fallbackBase);
                        setDiffTargetId((currentVersionId && currentVersionId !== fallbackBase) ? currentVersionId : (versionOptions.find((opt) => opt.id !== fallbackBase)?.id ?? fallbackBase));
                      }
                      setDiffFile(null);
                      setDiffOpen(true);
                    }}
                    style={{ display: 'grid', color: 'var(--color-text-muted)', gridTemplateColumns: '1fr 2fr 1fr', gap: 8, alignItems: 'center', padding: '6px 8px', borderRadius: 6, border: '1px solid var(--color-border)', background: 'var(--color-surface)', textAlign: 'left', width: '100%', cursor: 'pointer' }}
                  >
                    <div style={{ fontVariant: 'tabular-nums', fontSize: 13 }}>{truncateId(anc.id)}</div>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {(anc.fitness) ? formatFitness(metrics, anc.fitness)
                       : <div style={{ color: 'var(--color-text-muted)' }}>No fitness</div>}
                    </div>
                    <div style={{ textAlign: 'right', color: 'var(--color-text-muted)', fontSize: 13 }}>{anc?.data?.metadata?.llmModel || anc?.metadata?.llmModel || '—'}</div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </CollapsibleSection>
      </div>

      {/* Files modal */}
      <Modal open={filesOpen} title={`Source files — ${truncateId(payload?.id)}`} onClose={() => setFilesOpen(false)}>
        <div style={{ width: '80vw', maxWidth: 900, height: '70vh', display: 'flex', gap: 12 }}>
          {hasFiles ? (
            <div style={{ display: 'flex', width: '100%', gap: 12 }}>
              <div style={{ width: 220, overflowY: 'auto' }}>
                {fileEntries.map(([fileName]) => (
                  <button
                    key={fileName}
                    type="button"
                    className={`solution-files__item ${activeFile === fileName ? 'active' : ''}`}
                    onClick={() => setActiveFile(fileName)}
                    style={{ display: 'block', width: '100%', textAlign: 'left', padding: '8px 10px', marginBottom: 6 }}
                  >
                    {fileName}
                  </button>
                ))}
              </div>
              <div style={{ flex: 1, minHeight: 0 }}>
                {activeFile ? (
                  <CodeEditor
                    code={activeFileContent || ''}
                    language={getLanguageFromFilename(activeFile)}
                    theme="vs-dark"
                    height="100%"
                    options={{ readOnly: true, minimap: { enabled: false } }}
                  />
                ) : (
                  <div style={{ padding: 24, color: 'var(--color-text-muted)' }}>Select a file to preview.</div>
                )}
              </div>
            </div>
          ) : (
            <div style={{ padding: 20 }}>No source files were emitted for this solution.</div>
          )}
        </div>
      </Modal>

      {/* Metadata modal */}
      <Modal open={metaOpen} title={`Metadata — ${truncateId(payload?.id)}`} onClose={() => setMetaOpen(false)}>
        <div style={{ maxHeight: '60vh', overflow: 'auto' }}>
          {metadata ? <ObjectViewer data={metadata} /> : <div style={{ padding: 20 }}>No metadata available.</div>}
        </div>
      </Modal>

      {/* Diff modal */}
      <Modal
        open={diffOpen}
        title="Compare solutions"
        onClose={() => {
          setDiffOpen(false);
          setDiffFile(null);
        }}
        contentStyle={{
          maxWidth: 'min(95vw, 1280px)',
          width: 'min(95vw, 1280px)'
        }}
        bodyStyle={{
          overflowY: 'hidden',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {versionOptions.length < 2 ? (
          <div className="solution-diff__empty">
            Need at least two versions to compare.
          </div>
        ) : (
          <div className="solution-diff">
            <div className="solution-diff__selectors">
              <label className="solution-diff__selector">
                <span>From</span>
                <select
                  value={diffBaseId ?? ''}
                  onChange={(event) => {
                    setDiffBaseId(event.target.value);
                    setDiffFile(null);
                  }}
                >
                  {versionOptions.map((option) => (
                    <option key={option.id} value={option.id}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label className="solution-diff__selector">
                <span>To</span>
                <select
                  value={diffTargetId ?? ''}
                  onChange={(event) => {
                    setDiffTargetId(event.target.value);
                    setDiffFile(null);
                  }}
                >
                  {versionOptions.map((option) => (
                    <option key={option.id} value={option.id}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>
            {!baseVersion || !targetVersion ? (
              <div className="solution-diff__empty">Select two versions to compare.</div>
            ) : diffFileNames.length === 0 ? (
              <div className="solution-diff__empty" style={{ color: 'var(--color-text-muted)' }}>
                No overlapping files between the selected versions.
              </div>
            ) : (
              <div className="solution-diff__body">
                <div className="solution-diff__files">
                  <div className="solution-files__list">
                    {diffFileNames.map((fileName) => (
                      <button
                        key={fileName}
                        type="button"
                        onClick={() => setDiffFile(fileName)}
                        className={`solution-files__item ${diffFile === fileName ? 'active' : ''}`}
                      >
                        {fileName}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="solution-diff__viewer">
                  {diffFile ? (
                    <>
                      <div className="solution-diff__viewer-header">
                        <div className="solution-diff__file-name" title={diffFile}>{diffFile}</div>
                        <div className="solution-diff__legend">
                          <span><strong>From:</strong> {baseVersion?.label}</span>
                          <span><strong>To:</strong> {targetVersion?.label}</span>
                        </div>
                      </div>
                      <div className="solution-diff__viewer-editor">
                        <CodeEditor
                          key={`${diffBaseId}:${diffTargetId}:${diffFile}`}
                          isDiff
                          language={getLanguageFromFilename(diffFile)}
                          originalCode={baseVersion?.files?.[diffFile] ?? ''}
                          modifiedCode={targetVersion?.files?.[diffFile] ?? ''}
                          theme="vs-dark"
                          height="100%"
                          options={{ readOnly: true, minimap: { enabled: false }, automaticLayout: true }}
                        />
                      </div>
                    </>
                  ) : (
                    <div className="solution-diff__empty">Select a file to preview differences.</div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </aside>
  );
}
