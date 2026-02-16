import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { ErNodeData } from '../types';

function TableNode({ data, id }: { data: ErNodeData; id: string }) {
  return (
    <div
      style={{
        background: '#fff',
        border: '1px solid #d1d5db',
        borderRadius: 6,
        minWidth: 260,
        fontSize: 13,
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        overflow: 'hidden',
      }}
    >
      {/* Table header */}
      <div
        style={{
          background: 'var(--color-table-header, #1e293b)',
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
        {(data.columns as ErNodeData['columns']).map((col, idx) => (
          <div
            key={col.name}
            style={{
              display: 'flex',
              alignItems: 'center',
              padding: '4px 12px',
              borderBottom:
                idx < (data.columns as ErNodeData['columns']).length - 1
                  ? '1px solid #f1f5f9'
                  : 'none',
              position: 'relative',
              gap: 6,
              minHeight: 28,
            }}
          >
            {/* Source handle (for FK going out from this column) */}
            {col.foreignKey && (
              <Handle
                type="source"
                position={Position.Right}
                id={`${id}-${col.name}-source`}
                style={{
                  background: 'var(--color-fk, #8b5cf6)',
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
                  background: 'var(--color-pk, #f59e0b)',
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
                  background: '#fef3c7',
                  color: '#92400e',
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
                style={{
                  background: '#ede9fe',
                  color: '#5b21b6',
                  fontSize: 10,
                  fontWeight: 700,
                  padding: '1px 4px',
                  borderRadius: 3,
                  flexShrink: 0,
                }}
              >
                FK
              </span>
            )}

            {/* Column name */}
            <span
              style={{
                fontWeight: col.primaryKey ? 600 : 400,
                flex: 1,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {col.name}
            </span>

            {/* Type + nullable */}
            <span
              style={{
                color: '#94a3b8',
                fontSize: 11,
                flexShrink: 0,
              }}
            >
              {col.dataType}
              {!col.nullable && (
                <span style={{ color: '#ef4444', marginLeft: 2 }}>*</span>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default memo(TableNode);
