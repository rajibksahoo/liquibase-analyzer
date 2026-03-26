# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

### Prerequisites
Node.js >= 20 and npm must be installed on the machine (`node -v`, `npm -v`).
The build uses **system npm** — no binary is downloaded by Maven.

### Full Build (backend + frontend)
```bash
mvn clean package
```
On Windows cmd/PowerShell Maven auto-activates the `windows` profile which uses `npm.cmd`. From Git Bash `npm` works directly.

To skip the frontend build (backend-only changes):
```bash
mvn package -Dskip.frontend=true
```

### Development Mode (recommended)
Run backend and frontend separately:
```bash
# Terminal 1: Backend on port 8080
mvn spring-boot:run

# Terminal 2: Frontend dev server on port 5173 (proxies /api → localhost:8080)
cd frontend && npm install && npm run dev
```

### Frontend only
```bash
cd frontend
npm run build   # Production build to frontend/dist/
npm run dev     # Dev server (port 5173)
```

### Tests
No tests currently exist. The test dependency is present in pom.xml but `src/test/` is empty.

## Architecture

This is a full-stack Java 21 / Spring Boot 3.4.1 + React 18 / TypeScript application. The core purpose: upload a Liquibase changelog ZIP, execute it against an embedded PostgreSQL instance, introspect the resulting schema, and render an interactive ER diagram.

### Backend (`src/main/java/rajib/dev/utility/`)

**Layer structure:**

- **Controllers** (`controller/`) — Three REST controllers: `LiquibaseController` (changelog execution + status), `SchemaController` (snapshot CRUD), `DiagramController` (ER diagram generation)
- **Services** (`service/`) — Core business logic:
  - `LiquibaseExecutionService` — Programmatic Liquibase execution using `DirectoryResourceAccessor`
  - `SchemaIntrospectionService` — Reads JDBC metadata (tables, columns, PKs, FKs, indexes, constraints)
  - `DiagramLayoutService` — Builds hierarchical ER diagram layout via topological sort
  - `EmbeddedPgService` — Lifecycle management of embedded PostgreSQL (Zonky, port auto-assigned)
  - `ChangelogExtractorService` — Unzips uploads, locates `db.changelog-master.xml`
  - `SchemaSnapshotService` — Persists/retrieves snapshots
- **Persistence** (`entity/`, `repository/`) — `SchemaSnapshotEntity` stores full schema JSON as a CLOB in H2; `SchemaSnapshotRepository` is a Spring Data interface
- **Models/DTOs** (`model/`, `dto/`) — `SchemaSnapshot`, `TableInfo`, `ColumnInfo`, `ForeignKeyInfo`, `ErNode`, `ErEdge`, etc.

**Key design decisions:**
- `LiquibaseAutoConfiguration` is excluded from Spring Boot auto-config (`@SpringBootApplication(exclude = {...})`); Liquibase is executed programmatically per request
- H2 (file-based at `./data/liquibase-analyzer`) stores snapshots; embedded PostgreSQL is spun up per changelog execution for isolation
- Async execution pool configured in `AsyncConfig` (2 core, 4 max threads)
- CORS allows `http://localhost:5173` for frontend dev server
- Static SPA fallback: unmatched paths serve `index.html` from `classpath:/static/`

### Frontend (`frontend/src/`)

- **`api/client.ts`** — All Axios HTTP calls, TypeScript-typed
- **`types/index.ts`** — All TypeScript interfaces
- **`components/`** — React components:
  - `AppShell` — Top-level layout wrapper; renders `Sidebar` + `<main>` content area
  - `ErDiagram` — React Flow canvas with custom `TableNode` and `RelationshipEdge` types; layout via Dagre
  - `DiagramToolbar` — Controls rendered inside the React Flow panel (zoom, fit, export)
  - `TableSelector` — Table selection with transitive FK closure highlighting
  - `UploadPage`, `HomePage`, `SnapshotDetailPage` — Main pages
- **Routes** (in `App.tsx`): `/` → HomePage, `/upload` → UploadPage, `/snapshots/:id` → SnapshotDetailPage
- React Query (`@tanstack/react-query`) for data fetching; React Flow (`@xyflow/react`) for diagram

### Data / Config

- **`application.yml`**: H2 at `./data/liquibase-analyzer`, uploads at `./data/uploads`, max upload 50MB, server port 8080
- **`vite.config.ts`**: Dev proxy `/api` → `http://localhost:8080`, output to `dist/`

### API Surface

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/liquibase/execute` | Upload ZIP, execute changelog |
| GET | `/api/liquibase/status` | Embedded PostgreSQL status |
| GET | `/api/schemas` | List all snapshots |
| GET | `/api/schemas/{id}` | Snapshot detail |
| DELETE | `/api/schemas/{id}` | Delete snapshot |
| POST | `/api/schemas/introspect` | Manually trigger introspection |
| GET | `/api/diagrams/{snapshotId}` | Full ER diagram |
| POST | `/api/diagrams/subset` | Subset ER diagram for selected tables |