import { useState } from 'react';

interface Props {
  tables: string[];
  onApply: (selected: string[], includeIndirect: boolean) => void;
}

export default function TableSelector({ tables, onApply }: Props) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [includeIndirect, setIncludeIndirect] = useState(true);

  const toggle = (name: string) => {
    const next = new Set(selected);
    if (next.has(name)) {
      next.delete(name);
    } else {
      next.add(name);
    }
    setSelected(next);
  };

  const selectAll = () => setSelected(new Set(tables));
  const clearAll = () => setSelected(new Set());

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <button className="btn btn-sm" onClick={selectAll}>Select All</button>
        <button className="btn btn-sm" onClick={clearAll}>Clear</button>
        <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 13 }}>
          <input
            type="checkbox"
            checked={includeIndirect}
            onChange={(e) => setIncludeIndirect(e.target.checked)}
          />
          Include related tables
        </label>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => onApply(Array.from(selected), includeIndirect)}
          disabled={selected.size === 0}
          style={{ marginLeft: 'auto' }}
        >
          Apply ({selected.size} selected)
        </button>
      </div>
      <div className="table-selector">
        {tables.map((name) => (
          <button
            key={name}
            className={`table-chip ${selected.has(name) ? 'selected' : ''}`}
            onClick={() => toggle(name)}
          >
            {name}
          </button>
        ))}
      </div>
    </div>
  );
}
