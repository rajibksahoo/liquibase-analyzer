import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listSnapshots } from '../api/client';

export default function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const { data: snapshots } = useQuery({
    queryKey: ['snapshots'],
    queryFn: listSnapshots,
    refetchInterval: 5000,
  });

  return (
    <div className="sidebar">
      <div className="sidebar-header">Schema Analyzer</div>
      <nav className="sidebar-nav">
        <Link
          to="/"
          className={`sidebar-link ${location.pathname === '/' ? 'active' : ''}`}
        >
          Home
        </Link>
        <Link
          to="/upload"
          className={`sidebar-link ${location.pathname === '/upload' ? 'active' : ''}`}
        >
          Upload Changelog
        </Link>

        {snapshots && snapshots.length > 0 && (
          <>
            <div className="sidebar-section-title">Snapshots</div>
            {snapshots.map((s) => (
              <button
                key={s.id}
                className={`sidebar-snapshot ${
                  location.pathname === `/snapshots/${s.id}` ? 'active' : ''
                }`}
                onClick={() => navigate(`/snapshots/${s.id}`)}
              >
                <span>{s.name}</span>
                <span className="table-count">{s.tableCount} tables</span>
              </button>
            ))}
          </>
        )}
      </nav>
    </div>
  );
}
