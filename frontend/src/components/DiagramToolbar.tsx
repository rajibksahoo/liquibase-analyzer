import { useReactFlow } from '@xyflow/react';

interface Props {
  snapshotName: string;
  nodeCount: number;
  edgeCount: number;
}

export default function DiagramToolbar({ snapshotName, nodeCount, edgeCount }: Props) {
  const { fitView, zoomIn, zoomOut } = useReactFlow();

  return (
    <div className="diagram-toolbar">
      <span style={{ fontWeight: 600, fontSize: 14 }}>{snapshotName}</span>
      <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>
        {nodeCount} tables, {edgeCount} relationships
      </span>
      <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
        <button className="btn btn-sm" onClick={() => zoomIn()}>+</button>
        <button className="btn btn-sm" onClick={() => zoomOut()}>-</button>
        <button className="btn btn-sm" onClick={() => fitView({ padding: 0.1 })}>
          Fit
        </button>
      </div>
    </div>
  );
}
