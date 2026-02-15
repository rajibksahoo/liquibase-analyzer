import { Routes, Route } from 'react-router-dom';
import AppShell from './components/AppShell';
import HomePage from './components/HomePage';
import UploadPage from './components/UploadPage';
import SnapshotDetailPage from './components/SnapshotDetailPage';

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/snapshots/:id" element={<SnapshotDetailPage />} />
      </Routes>
    </AppShell>
  );
}
