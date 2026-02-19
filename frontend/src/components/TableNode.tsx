import { memo, useCallback } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { ErNodeData } from '../types';

interface HighlightedFk {
  edgeId: string;
  sourceNodeId: string;
  sourceColumn: string;
  targetNodeId: string;
  targetColumn: string;
}

interface TableNodeData extends ErNodeData {
  searchQuery?: string;
  highlightedFk?: HighlightedFk | null;
  onFkClick?: (nodeId: string, columnName: string) => void;
}

function nodeMatchesSearch(data: TableNodeData, query: string): boolean {
  if (!query) return true;
  const q = query.toLowerCase();
  if (data.tableName.toLowerCase().includes(q)) return true;
  return (data.columns as ErNodeData['columns']).some((col) =>
    col.name.toLowerCase().includes(q)
  );
}

function columnMatchesSearch(colName: string, query: string): boolean {
  if (!query) return false;
  return colName.toLowerCase().includes(query.toLowerCase());
}

function TableNode({ data, id }: { data: TableNodeData; id: string }) {
  const searchQuery = (data.searchQuery as string) || '';
  const highlightedFk = data.highlightedFk as HighlightedFk | null | undefined;
  const onFkClick = data.onFkClick as ((nodeId: string, columnName: string) => void) | undefined;

  const matches = nodeMatchesSearch(data, searchQuery);
  const isDimmed = searchQuery !== '' && !matches;
  const tableNameMatches = searchQuery !== '' && data.tableName.toLowerCase().includes(searchQuery.toLowerCase());

  const handleFkClick = useCallback(
    (colName: string) => {
      onFkClick?.(id, colName);
    },
    [onFkClick, id]
  );

  // Check if this node/column is part of the highlighted FK
  const isSourceHighlighted = highlightedFk?.sourceNodeId === id;
  const isTargetHighlighted = highlightedFk?.targetNodeId === id;

  return (
    <div
      style={{
        background: 'var(--color-node-bg)',
        border: '1px solid var(--color-node-border)',
        borderRadius: 6,
        minWidth: 260,
        fontSize: 13,
        boxShadow: '0 1px 3px var(--color-node-shadow)',
        overflow: 'hidden',
        color: 'var(--color-text)',
        opacity: isDimmed ? 0.25 : 1,
        transition: 'opacity 0.2s',
      }}
    >
      {/* Table header */}
      <div
        style={{
          background: 'var(--color-table-header)',
          color: '#fff',
          padding: '8px 12px',
          fontWeight: 600,
          fontSize: 14,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <span>{data.tableName}</span>
        {data.comment && (
          <span title={data.comment} style={{ fontSize: 11, opacity: 0.7 }}>
            i
          </span>
        )}
      </div>

      {/* Columns */}
      <div>
        {(data.columns as ErNodeData['columns']).map((col, idx) => {
          const isSearchHighlighted =
            searchQuery !== '' && matches && !tableNameMatches && columnMatchesSearch(col.name, searchQuery);
          const isFkSourceCol = isSourceHighlighted && col.name === highlightedFk?.sourceColumn;
          const isFkTargetCol = isTargetHighlighted && col.name === highlightedFk?.targetColumn;

          return (
            <div
              key={col.name}
              style={{
                display: 'flex',
                alignItems: 'center',
                padding: '4px 12px',
                borderBottom:
                  idx < (data.columns as ErNodeData['columns']).length - 1
                    ? '1px solid var(--color-column-divider)'
                    : 'none',
                position: 'relative',
                gap: 6,
                minHeight: 28,
                background: isFkSourceCol
                  ? 'var(--color-fk-badge-bg)'
                  : isFkTargetCol
                    ? 'var(--color-pk-badge-bg)'
                    : isSearchHighlighted
                      ? 'rgba(59, 130, 246, 0.08)'
                      : 'transparent',
                transition: 'background 0.2s',
              }}
            >
              {/* Source handle (for FK going out from this column) */}
              {col.foreignKey && (
                <Handle
                  type="source"
                  position={Position.Right}
                  id={`${id}-${col.name}-source`}
                  style={{
                    background: 'var(--color-fk)',
                    width: 8,
                    height: 8,
                    right: -4,
                  }}
                />
              )}

              {/* Target handle (for FK coming into this column) */}
              {col.primaryKey && (
                <Handle
                  type="target"
                  position={Position.Left}
                  id={`${id}-${col.name}-target`}
                  style={{
                    background: 'var(--color-pk)',
                    width: 8,
                    height: 8,
                    left: -4,
                  }}
                />
              )}

              {/* PK/FK badges */}
              {col.primaryKey && (
                <span
                  style={{
                    background: 'var(--color-pk-badge-bg)',
                    color: 'var(--color-pk-badge-text)',
                    fontSize: 10,
                    fontWeight: 700,
                    padding: '1px 4px',
                    borderRadius: 3,
                    flexShrink: 0,
                  }}
                >
                  PK
                </span>
              )}
              {col.foreignKey && (
                <span
                  onClick={(e) => {
                    e.stopPropagation();
                    handleFkClick(col.name);
                  }}
                  style={{
                    background: 'var(--color-fk-badge-bg)',
                    color: 'var(--color-fk-badge-text)',
                    fontSize: 10,
                    fontWeight: 700,
                    padding: '1px 4px',
                    borderRadius: 3,
                    flexShrink: 0,
                    cursor: 'pointer',
                  }}
                >
                  FK
                </span>
              )}

              {/* Column name */}
              <span
                onClick={col.foreignKey ? (e) => { e.stopPropagation(); handleFkClick(col.name); } : undefined}
                style={{
                  fontWeight: col.primaryKey ? 600 : 400,
                  flex: 1,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  cursor: col.foreignKey ? 'pointer' : 'default',
                }}
              >
                {col.name}
              </span>

              {/* Type + nullable */}
              <span
                style={{
                  color: 'var(--color-type-text)',
                  fontSize: 11,
                  flexShrink: 0,
                }}
              >
                {col.dataType}
                {!col.nullable && (
                  <span style={{ color: 'var(--color-danger)', marginLeft: 2 }}>*</span>
                )}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default memo(TableNode);
