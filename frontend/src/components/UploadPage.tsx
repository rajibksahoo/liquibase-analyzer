import { useState, useRef, DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { inspectChangelog, executeChangelog } from '../api/client';

const SESSION_KEY = 'changelog_property_values';

type Step = 'upload' | 'properties';

export default function UploadPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  // ── file / options state ──────────────────────────────────────────────────
  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<'embedded' | 'external'>('embedded');
  const [snapshotName, setSnapshotName] = useState('');
  const [externalUrl, setExternalUrl] = useState('');
  const [externalUser, setExternalUser] = useState('');
  const [externalPassword, setExternalPassword] = useState('');
  const [dragging, setDragging] = useState(false);

  // ── two-step flow state ───────────────────────────────────────────────────
  const [step, setStep] = useState<Step>('upload');
  const [uploadToken, setUploadToken] = useState('');
  const [unresolvedProps, setUnresolvedProps] = useState<string[]>([]);
  const [propValues, setPropValues] = useState<Record<string, string>>({});

  // ── feedback ──────────────────────────────────────────────────────────────
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // ── mutations ─────────────────────────────────────────────────────────────

  const inspectMutation = useMutation({
    mutationFn: (f: File) => inspectChangelog(f),
    onSuccess: (data) => {
      if (!data.uploadToken) {
        setError('Inspection failed – could not process the uploaded file.');
        return;
      }
      setUploadToken(data.uploadToken);

      if (data.unresolvedProperties.length === 0) {
        // No placeholders → execute immediately
        submitExecute(data.uploadToken, {});
        return;
      }

      // Pre-fill from sessionStorage so users don't retype the same values
      const saved: Record<string, string> = JSON.parse(
        sessionStorage.getItem(SESSION_KEY) ?? '{}'
      );
      const initial: Record<string, string> = {};
      data.unresolvedProperties.forEach((name) => {
        initial[name] = saved[name] ?? '';
      });
      setPropValues(initial);
      setUnresolvedProps(data.unresolvedProperties);
      setStep('properties');
    },
    onError: (err: Error) => {
      setError(err.message);
    },
  });

  const executeMutation = useMutation({
    mutationFn: (formData: FormData) => executeChangelog(formData),
    onSuccess: (data) => {
      if (data.success && data.snapshotId) {
        setSuccess(data.message);
        setError(null);
        queryClient.invalidateQueries({ queryKey: ['snapshots'] });
        setTimeout(() => navigate(`/snapshots/${data.snapshotId}`), 1000);
      } else {
        setError(data.message);
        setSuccess(null);
      }
    },
    onError: (err: Error) => {
      setError(err.message);
      setSuccess(null);
    },
  });

  const isPending = inspectMutation.isPending || executeMutation.isPending;

  // ── helpers ───────────────────────────────────────────────────────────────

  function submitExecute(token: string, properties: Record<string, string>) {
    const formData = new FormData();
    formData.append('uploadToken', token);
    formData.append('mode', mode);
    if (snapshotName) formData.append('snapshotName', snapshotName);
    if (mode === 'external') {
      formData.append('externalUrl', externalUrl);
      formData.append('externalUser', externalUser);
      formData.append('externalPassword', externalPassword);
    }
    if (Object.keys(properties).length > 0) {
      formData.append('properties', JSON.stringify(properties));
    }
    executeMutation.mutate(formData);
  }

  // ── handlers ──────────────────────────────────────────────────────────────

  const handleAnalyze = () => {
    if (!file) { setError('Please select a ZIP file'); return; }
    setError(null);
    setSuccess(null);
    inspectMutation.mutate(file);
  };

  const handleExecute = () => {
    // Persist entered values to sessionStorage for this browser session
    const existing: Record<string, string> = JSON.parse(
      sessionStorage.getItem(SESSION_KEY) ?? '{}'
    );
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ...existing, ...propValues }));
    submitExecute(uploadToken, propValues);
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped?.name.endsWith('.zip')) {
      setFile(dropped);
      setError(null);
    } else {
      setError('Please drop a .zip file');
    }
  };

  const handleBack = () => {
    setStep('upload');
    setUnresolvedProps([]);
    setPropValues({});
    setUploadToken('');
    setError(null);
  };

  // ── render ────────────────────────────────────────────────────────────────

  return (
    <div className="page">
      <div className="page-header">
        <h1>Upload Changelog</h1>
        <p>Upload a Liquibase changelog ZIP file to execute and analyze.</p>
      </div>

      {error   && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      {/* ── Step 1: file + options ── */}
      {step === 'upload' && (
        <div className="card">
          <div
            className={`drop-zone ${dragging ? 'dragging' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            {file ? (
              <p>
                Selected: <span className="file-name">{file.name}</span>{' '}
                ({(file.size / 1024).toFixed(1)} KB)
              </p>
            ) : (
              <>
                <p><strong>Drop ZIP file here</strong></p>
                <p>or click to browse</p>
              </>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept=".zip"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) { setFile(f); setError(null); }
              }}
            />
          </div>

          <div className="form-group" style={{ marginTop: 20 }}>
            <label>Snapshot Name (optional)</label>
            <input
              type="text"
              placeholder="My Schema Snapshot"
              value={snapshotName}
              onChange={(e) => setSnapshotName(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label>Execution Mode</label>
            <select value={mode} onChange={(e) => setMode(e.target.value as 'embedded' | 'external')}>
              <option value="embedded">Embedded PostgreSQL</option>
              <option value="external">External PostgreSQL</option>
            </select>
          </div>

          {mode === 'external' && (
            <>
              <div className="form-group">
                <label>JDBC URL</label>
                <input
                  type="text"
                  placeholder="jdbc:postgresql://localhost:5432/mydb"
                  value={externalUrl}
                  onChange={(e) => setExternalUrl(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Username</label>
                <input
                  type="text"
                  value={externalUser}
                  onChange={(e) => setExternalUser(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Password</label>
                <input
                  type="password"
                  value={externalPassword}
                  onChange={(e) => setExternalPassword(e.target.value)}
                />
              </div>
            </>
          )}

          <button
            className="btn btn-primary"
            onClick={handleAnalyze}
            disabled={isPending}
            style={{ marginTop: 8 }}
          >
            {isPending ? (
              <><span className="spinner" /> Analyzing...</>
            ) : (
              'Analyze'
            )}
          </button>
        </div>
      )}

      {/* ── Step 2: fill in unresolved properties ── */}
      {step === 'properties' && (
        <div className="card">
          <p style={{ marginBottom: 16 }}>
            The changelog references the following parameters that have no defined values.
            Enter a value for each before executing.
          </p>

          {unresolvedProps.map((name) => (
            <div className="form-group" key={name}>
              <label>{name}</label>
              <input
                type="text"
                placeholder={`Value for \${${name}}`}
                value={propValues[name] ?? ''}
                onChange={(e) =>
                  setPropValues((prev) => ({ ...prev, [name]: e.target.value }))
                }
              />
            </div>
          ))}

          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <button
              className="btn btn-secondary"
              onClick={handleBack}
              disabled={isPending}
            >
              Back
            </button>
            <button
              className="btn btn-primary"
              onClick={handleExecute}
              disabled={isPending}
            >
              {isPending ? (
                <><span className="spinner" /> Executing...</>
              ) : (
                'Execute & Analyze'
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
