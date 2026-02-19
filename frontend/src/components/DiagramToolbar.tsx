interface Props {
  snapshotName: string;
  nodeCount: number;
  edgeCount: number;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  onFitView?: () => void;
  onZoomIn?: () => void;
  onZoomOut?: () => void;
}

export default function DiagramToolbar({
  snapshotName,
  nodeCount,
  edgeCount,
  searchQuery,
  onSearchChange,
  onFitView,
  onZoomIn,
  onZoomOut,
}: Props) {
  return (
    <div className="diagram-toolbar">
      <span style={{ fontWeight: 600, fontSize: 14 }}>{snapshotName}</span>
      <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>
        {nodeCount} tables, {edgeCount} relationships
      </span>
      <div className="diagram-search">
        <input
          type="text"
          placeholder="Search tables or columns..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        {searchQuery && (
          <button className="search-clear" onClick={() => onSearchChange('')}>
            &times;
          </button>
        )}
      </div>
      {(onZoomIn || onZoomOut || onFitView) && (
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
          {onZoomIn && <button className="btn btn-sm" onClick={onZoomIn}>+</button>}
          {onZoomOut && <button className="btn btn-sm" onClick={onZoomOut}>-</button>}
          {onFitView && <button className="btn btn-sm" onClick={onFitView}>Fit</button>}
        </div>
      )}
    </div>
  );
}
