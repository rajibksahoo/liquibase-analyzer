import axios from 'axios';
import type {
  SchemaSnapshotSummary,
  SchemaSnapshotDetail,
  LiquibaseExecutionResponse,
  ChangelogInspectResponse,
  DiagramData,
  TableSubsetRequest,
} from '../types';

const api = axios.create({
  baseURL: '/api',
});

/** Step 1: upload ZIP, get unresolved properties + session token. */
export async function inspectChangelog(file: File): Promise<ChangelogInspectResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ChangelogInspectResponse>('/liquibase/inspect', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

/** Step 2: execute using the session token + user-supplied property values. */
export async function executeChangelog(formData: FormData): Promise<LiquibaseExecutionResponse> {
  const { data } = await api.post<LiquibaseExecutionResponse>('/liquibase/execute', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export async function getLiquibaseStatus(): Promise<{ embeddedPgRunning: boolean; embeddedPgPort: number }> {
  const { data } = await api.get('/liquibase/status');
  return data;
}

export async function listSnapshots(): Promise<SchemaSnapshotSummary[]> {
  const { data } = await api.get<SchemaSnapshotSummary[]>('/schemas');
  return data;
}

export async function getSnapshot(id: number): Promise<SchemaSnapshotDetail> {
  const { data } = await api.get<SchemaSnapshotDetail>(`/schemas/${id}`);
  return data;
}

export async function deleteSnapshot(id: number): Promise<void> {
  await api.delete(`/schemas/${id}`);
}

export async function getDiagram(snapshotId: number): Promise<DiagramData> {
  const { data } = await api.get<DiagramData>(`/diagrams/${snapshotId}`);
  return data;
}

export async function getSubsetDiagram(request: TableSubsetRequest): Promise<DiagramData> {
  const { data } = await api.post<DiagramData>('/diagrams/subset', request);
  return data;
}
