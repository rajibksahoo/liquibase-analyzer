import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type Position,
} from '@xyflow/react';

interface Props {
  id: string;
  sourceX: number;
  sourceY: number;
  targetX: number;
  targetY: number;
  sourcePosition: Position;
  targetPosition: Position;
  data?: {
    constraintName?: string;
    sourceColumn?: string;
    targetColumn?: string;
    deleteRule?: string;
    updateRule?: string;
    highlighted?: boolean;
    dimmed?: boolean;
  };
  selected?: boolean;
}

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
}: Props) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 12,
  });

  const highlighted = data?.highlighted;
  const dimmed = data?.dimmed;

  const stroke = highlighted
    ? 'var(--color-primary)'
    : selected
      ? 'var(--color-edge-selected)'
      : 'var(--color-edge)';
  const strokeWidth = highlighted ? 3 : selected ? 2 : 1.5;
  const opacity = dimmed ? 0.25 : 1;

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke,
          strokeWidth,
          opacity,
          ...(highlighted
            ? {
                strokeDasharray: '6 4',
                animation: 'dash-flow 0.6s linear infinite',
              }
            : {}),
        }}
      />
      {data?.constraintName && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              background: 'var(--color-edge-label-bg)',
              border: highlighted
                ? '1px solid var(--color-primary)'
                : '1px solid var(--color-edge-label-border)',
              borderRadius: 4,
              padding: '2px 6px',
              fontSize: 10,
              color: highlighted ? 'var(--color-primary)' : 'var(--color-edge-label-text)',
              pointerEvents: 'all',
              whiteSpace: 'nowrap',
              opacity,
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
