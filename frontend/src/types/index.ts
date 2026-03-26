export interface SchemaSnapshotSummary {
  id: number;
  name: string;
  schemaName: string;
  capturedAt: string;
  tableCount: number;
}

export interface ColumnInfo {
  name: string;
  dataType: string;
  nativeType: string;
  ordinalPosition: number;
  nullable: boolean;
  defaultValue: string | null;
  columnSize: number;
  decimalDigits: number;
  comment: string | null;
  primaryKey: boolean;
  foreignKey: boolean;
}

export interface PrimaryKeyInfo {
  name: string;
  columns: string[];
}

export interface ForeignKeyInfo {
  name: string;
  columnName: string;
  referencedTable: string;
  referencedColumn: string;
  updateRule: string;
  deleteRule: string;
}

export interface IndexInfo {
  name: string;
  columns: string[];
  unique: boolean;
  type: string;
}

export interface UniqueConstraintInfo {
  name: string;
  columns: string[];
}

export interface CheckConstraintInfo {
  name: string;
  definition: string;
}

export interface TableInfo {
  name: string;
  schema: string;
  comment: string | null;
  columns: ColumnInfo[];
  primaryKey: PrimaryKeyInfo | null;
  foreignKeys: ForeignKeyInfo[];
  indexes: IndexInfo[];
  uniqueConstraints: UniqueConstraintInfo[];
  checkConstraints: CheckConstraintInfo[];
}

export interface SchemaSnapshotDetail {
  id: number;
  name: string;
  schemaName: string;
  capturedAt: string;
  tables: TableInfo[];
}

export interface LiquibaseExecutionResponse {
  success: boolean;
  message: string;
  snapshotId: number | null;
}

export interface ChangelogInspectResponse {
  uploadToken: string;
  changelogCandidates: string[];
  selectedChangelog: string;
  unresolvedProperties: string[];
}

export interface ErColumnData {
  name: string;
  dataType: string;
  nullable: boolean;
  primaryKey: boolean;
  foreignKey: boolean;
  defaultValue: string | null;
}

export interface ErNodeData {
  [key: string]: unknown;
  tableName: string;
  schema: string;
  comment: string | null;
  columns: ErColumnData[];
  primaryKeyName: string | null;
}

export interface ErNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: ErNodeData;
}

export interface ErEdgeData {
  [key: string]: unknown;
  constraintName: string;
  sourceColumn: string;
  targetColumn: string;
  deleteRule: string;
  updateRule: string;
}

export interface ErEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle: string;
  targetHandle: string;
  type: string;
  data: ErEdgeData;
}

export interface DiagramData {
  snapshotId: number;
  snapshotName: string;
  nodes: ErNode[];
  edges: ErEdge[];
}

export interface TableSubsetRequest {
  snapshotId: number;
  selectedTables: string[];
  includeIndirect: boolean;
}
