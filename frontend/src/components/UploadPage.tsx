import { useState, useRef, DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { executeChangelog } from '../api/client';

export default function UploadPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<'embedded' | 'external'>('embedded');
  const [snapshotName, setSnapshotName] = useState('');
  const [externalUrl, setExternalUrl] = useState('');
  const [externalUser, setExternalUser] = useState('');
  const [externalPassword, setExternalPassword] = useState('');
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const mutation = useMutation({
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

  const handleSubmit = () => {
    if (!file) {
      setError('Please select a ZIP file');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('mode', mode);
    if (snapshotName) formData.append('snapshotName', snapshotName);
    if (mode === 'external') {
      formData.append('externalUrl', externalUrl);
      formData.append('externalUser', externalUser);
      formData.append('externalPassword', externalPassword);
    }

    mutation.mutate(formData);
  };

  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped && dropped.name.endsWith('.zip')) {
      setFile(dropped);
      setError(null);
    } else {
      setError('Please drop a .zip file');
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Upload Changelog</h1>
        <p>Upload a Liquibase changelog ZIP file to execute and analyze.</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

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
              Selected: <span className="file-name">{file.name}</span> (
              {(file.size / 1024).toFixed(1)} KB)
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
          onClick={handleSubmit}
          disabled={mutation.isPending}
          style={{ marginTop: 8 }}
        >
          {mutation.isPending ? (
            <>
              <span className="spinner" />
              Executing...
            </>
          ) : (
            'Execute & Analyze'
          )}
        </button>
      </div>
    </div>
  );
}
