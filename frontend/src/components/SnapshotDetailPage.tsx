import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getSnapshot, getDiagram, getSubsetDiagram, deleteSnapshot } from '../api/client';
import ErDiagram from './ErDiagram';
import TableSelector from './TableSelector';

type Tab = 'diagram' | 'tables' | 'subset';

export default function SnapshotDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const snapshotId = Number(id);

  const [activeTab, setActiveTab] = useState<Tab>('diagram');

  const { data: snapshot, isLoading: snapshotLoading } = useQuery({
    queryKey: ['snapshot', snapshotId],
    queryFn: () => getSnapshot(snapshotId),
    enabled: !!id,
  });

  const { data: diagram, isLoading: diagramLoading } = useQuery({
    queryKey: ['diagram', snapshotId],
    queryFn: () => getDiagram(snapshotId),
    enabled: !!id && activeTab === 'diagram',
  });

  const subsetMutation = useMutation({
    mutationFn: (params: { selectedTables: string[]; includeIndirect: boolean }) =>
      getSubsetDiagram({
        snapshotId,
        selectedTables: params.selectedTables,
        includeIndirect: params.includeIndirect,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteSnapshot(snapshotId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['snapshots'] });
      navigate('/');
    },
  });

  if (snapshotLoading) {
    return (
      <div className="page">
        <div className="loading">
          <span className="spinner" /> Loading snapshot...
        </div>
      </div>
    );
  }

  if (!snapshot) {
    return (
      <div className="page">
        <div className="alert alert-error">Snapshot not found</div>
      </div>
    );
  }

  const tableNames = snapshot.tables.map((t) => t.name);

  return (
    <div className="page" style={{ display: 'flex', flexDirection: 'column', padding: 0 }}>
      {/* Header */}
      <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--color-border)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <h1 style={{ fontSize: 20, fontWeight: 700 }}>{snapshot.name}</h1>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 13, marginTop: 2 }}>
              Schema: {snapshot.schemaName} | {snapshot.tables.length} tables |{' '}
              {new Date(snapshot.capturedAt).toLocaleString()}
            </p>
          </div>
          <button
            className="btn btn-danger btn-sm"
            onClick={() => {
              if (confirm('Delete this snapshot?')) deleteMutation.mutate();
            }}
          >
            Delete
          </button>
        </div>

        {/* Tabs */}
        <div className="tabs" style={{ marginTop: 12, marginBottom: 0 }}>
          <button
            className={`tab ${activeTab === 'diagram' ? 'active' : ''}`}
            onClick={() => setActiveTab('diagram')}
          >
            ER Diagram
          </button>
          <button
            className={`tab ${activeTab === 'subset' ? 'active' : ''}`}
            onClick={() => setActiveTab('subset')}
          >
            Table Subset
          </button>
          <button
            className={`tab ${activeTab === 'tables' ? 'active' : ''}`}
            onClick={() => setActiveTab('tables')}
          >
            Table Details
          </button>
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        {activeTab === 'diagram' && (
          <>
            {diagramLoading ? (
              <div className="loading">
                <span className="spinner" /> Loading diagram...
              </div>
            ) : diagram ? (
              <ErDiagram diagramData={diagram} />
            ) : null}
          </>
        )}

        {activeTab === 'subset' && (
          <div style={{ padding: 24, flex: 1, display: 'flex', flexDirection: 'column' }}>
            <TableSelector
              tables={tableNames}
              onApply={(selected, includeIndirect) =>
                subsetMutation.mutate({ selectedTables: selected, includeIndirect })
              }
            />
            {subsetMutation.isPending && (
              <div className="loading">
                <span className="spinner" /> Generating subset...
              </div>
            )}
            {subsetMutation.data && (
              <div style={{ flex: 1, minHeight: 400, marginTop: 16 }}>
                <ErDiagram diagramData={subsetMutation.data} />
              </div>
            )}
          </div>
        )}

        {activeTab === 'tables' && (
          <div style={{ padding: 24, overflowY: 'auto' }}>
            {snapshot.tables.map((table) => (
              <div key={table.name} className="card">
                <h3 style={{ marginBottom: 8 }}>
                  {table.name}
                  {table.comment && (
                    <span
                      style={{
                        fontSize: 12,
                        color: 'var(--color-text-secondary)',
                        fontWeight: 400,
                        marginLeft: 8,
                      }}
                    >
                      {table.comment}
                    </span>
                  )}
                </h3>

                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--color-border)', textAlign: 'left' }}>
                      <th style={{ padding: '6px 8px' }}>Column</th>
                      <th style={{ padding: '6px 8px' }}>Type</th>
                      <th style={{ padding: '6px 8px' }}>Nullable</th>
                      <th style={{ padding: '6px 8px' }}>Default</th>
                      <th style={{ padding: '6px 8px' }}>Keys</th>
                    </tr>
                  </thead>
                  <tbody>
                    {table.columns.map((col) => (
                      <tr key={col.name} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '6px 8px', fontWeight: col.primaryKey ? 600 : 400 }}>
                          {col.name}
                        </td>
                        <td style={{ padding: '6px 8px', color: '#64748b' }}>{col.nativeType}</td>
                        <td style={{ padding: '6px 8px' }}>{col.nullable ? 'YES' : 'NO'}</td>
                        <td style={{ padding: '6px 8px', color: '#94a3b8', fontSize: 12 }}>
                          {col.defaultValue || '-'}
                        </td>
                        <td style={{ padding: '6px 8px' }}>
                          {col.primaryKey && (
                            <span
                              style={{
                                background: '#fef3c7',
                                color: '#92400e',
                                fontSize: 10,
                                padding: '1px 4px',
                                borderRadius: 3,
                                marginRight: 4,
                              }}
                            >
                              PK
                            </span>
                          )}
                          {col.foreignKey && (
                            <span
                              style={{
                                background: '#ede9fe',
                                color: '#5b21b6',
                                fontSize: 10,
                                padding: '1px 4px',
                                borderRadius: 3,
                              }}
                            >
                              FK
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {table.foreignKeys.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <strong style={{ fontSize: 12 }}>Foreign Keys:</strong>
                    <ul style={{ paddingLeft: 20, fontSize: 12, color: '#64748b', marginTop: 4 }}>
                      {table.foreignKeys.map((fk) => (
                        <li key={fk.name}>
                          {fk.name}: {fk.columnName} -&gt; {fk.referencedTable}.{fk.referencedColumn}{' '}
                          (ON DELETE {fk.deleteRule})
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {table.indexes.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <strong style={{ fontSize: 12 }}>Indexes:</strong>
                    <ul style={{ paddingLeft: 20, fontSize: 12, color: '#64748b', marginTop: 4 }}>
                      {table.indexes.map((idx) => (
                        <li key={idx.name}>
                          {idx.name} ({idx.type}
                          {idx.unique ? ', UNIQUE' : ''}): [{idx.columns.join(', ')}]
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
