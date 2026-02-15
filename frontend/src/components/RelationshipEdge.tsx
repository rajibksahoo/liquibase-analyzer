import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type EdgeProps,
} from '@xyflow/react';
import type { ErEdgeData } from '../types';

export default function RelationshipEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
}: EdgeProps & { data?: ErEdgeData }) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 12,
  });

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: selected ? '#3b82f6' : '#94a3b8',
          strokeWidth: selected ? 2 : 1.5,
        }}
      />
      {data && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              background: '#fff',
              border: '1px solid #e2e8f0',
              borderRadius: 4,
              padding: '2px 6px',
              fontSize: 10,
              color: '#64748b',
              pointerEvents: 'all',
              whiteSpace: 'nowrap',
            }}
            className="nodrag nopan"
            title={`${data.constraintName}\nDELETE: ${data.deleteRule}\nUPDATE: ${data.updateRule}`}
          >
            {data.constraintName}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}
