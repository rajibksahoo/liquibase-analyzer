import { ReactNode } from 'react';
import Sidebar from './Sidebar';

interface Props {
  children: ReactNode;
}

export default function AppShell({ children }: Props) {
  return (
    <div className="layout">
      <Sidebar />
      <div className="main-content">{children}</div>
    </div>
  );
}
