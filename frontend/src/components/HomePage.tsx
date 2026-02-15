import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listSnapshots, getLiquibaseStatus } from '../api/client';

export default function HomePage() {
  const { data: snapshots } = useQuery({
    queryKey: ['snapshots'],
    queryFn: listSnapshots,
  });

  const { data: status } = useQuery({
    queryKey: ['liquibase-status'],
    queryFn: getLiquibaseStatus,
  });

  return (
    <div className="page">
      <div className="page-header">
        <h1>Liquibase Schema Analyzer</h1>
        <p>Upload Liquibase changelogs, introspect PostgreSQL schemas, and visualize ER diagrams.</p>
      </div>

      <div className="card">
        <h3>Quick Start</h3>
        <ol style={{ padding: '12px 0 0 20px', lineHeight: 2 }}>
          <li>
            <Link to="/upload">Upload a Liquibase changelog ZIP</Link> containing{' '}
            <code>db.changelog-master.xml</code>
          </li>
          <li>The changelog will execute against an embedded PostgreSQL instance</li>
          <li>The resulting schema is introspected and saved as a snapshot</li>
          <li>View the interactive ER diagram with zoom, pan, and table selection</li>
        </ol>
      </div>

      {status && (
        <div className="card">
          <h3>System Status</h3>
          <p style={{ marginTop: 8 }}>
            Embedded PostgreSQL:{' '}
            <strong style={{ color: status.embeddedPgRunning ? '#16a34a' : '#94a3b8' }}>
              {status.embeddedPgRunning ? `Running (port ${status.embeddedPgPort})` : 'Not started'}
            </strong>
          </p>
        </div>
      )}

      {snapshots && snapshots.length > 0 && (
        <div className="card">
          <h3>Recent Snapshots</h3>
          <table style={{ width: '100%', marginTop: 12, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--color-border)' }}>
                <th style={{ padding: '8px 12px' }}>Name</th>
                <th style={{ padding: '8px 12px' }}>Schema</th>
                <th style={{ padding: '8px 12px' }}>Tables</th>
                <th style={{ padding: '8px 12px' }}>Captured</th>
              </tr>
            </thead>
            <tbody>
              {snapshots.map((s) => (
                <tr key={s.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                  <td style={{ padding: '8px 12px' }}>
                    <Link to={`/snapshots/${s.id}`}>{s.name}</Link>
                  </td>
                  <td style={{ padding: '8px 12px' }}>{s.schemaName}</td>
                  <td style={{ padding: '8px 12px' }}>{s.tableCount}</td>
                  <td style={{ padding: '8px 12px', color: 'var(--color-text-secondary)', fontSize: 13 }}>
                    {new Date(s.capturedAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
