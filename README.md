# Liquibase Schema Analyzer

A full-stack application that executes Liquibase changelogs, introspects the resulting database schema, and generates interactive Entity-Relationship (ER) diagrams.

## Features

- **Changelog Execution** — Upload Liquibase changelog ZIP files and execute them against an embedded or external PostgreSQL database
- **Schema Introspection** — Automatically captures tables, columns, data types, constraints, primary keys, foreign keys, and indexes
- **Interactive ER Diagrams** — Visual relationship diagrams with zoom, pan, fit-to-view, and automatic hierarchical layout
- **Table Selection** — Select specific tables and trace their relationships, with an option to include indirect/transitive relationships
- **Schema Snapshots** — Save, list, view, and delete schema snapshots for comparison and analysis

## Tech Stack

| Layer    | Technology                                                        |
|----------|-------------------------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.4.1, Spring Data JPA, Liquibase 4.30.0    |
| Frontend | React 18, TypeScript, Vite, @xyflow/react, TanStack React Query  |
| Database | H2 (snapshot storage), Embedded PostgreSQL (changelog execution)  |
| Build    | Maven with frontend-maven-plugin (single `mvn` build)            |

## Prerequisites

- Java 21 JDK
- Maven 3.8.1+
- Node.js and npm are downloaded automatically by the Maven build

## Getting Started

### Build & Run

```bash
# Build everything (backend + frontend)
mvn clean package

# Start the application
mvn spring-boot:run
```

Open **http://localhost:8080** in your browser.

### Development Mode

Run the backend and frontend separately for hot-reloading:

```bash
# Terminal 1 — Backend
mvn spring-boot:run

# Terminal 2 — Frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

## Usage

1. Navigate to the **Upload** page
2. Upload a ZIP file containing your Liquibase changelogs (expects `db.changelog-master.xml` at the root)
3. Choose **Embedded PostgreSQL** (standalone) or provide **External PostgreSQL** connection details
4. The app executes the changelogs, introspects the schema, and saves a snapshot
5. View the interactive ER diagram from the **Home** page or snapshot detail page
6. Use the **Table Selector** to focus on specific tables and their relationships

## API Endpoints

| Method | Endpoint                    | Description                    |
|--------|-----------------------------|--------------------------------|
| POST   | `/api/liquibase/execute`    | Upload & execute changelog ZIP |
| GET    | `/api/liquibase/status`     | Embedded PostgreSQL status     |
| GET    | `/api/schemas`              | List all snapshots             |
| GET    | `/api/schemas/{id}`         | Get snapshot details           |
| DELETE | `/api/schemas/{id}`         | Delete a snapshot              |
| POST   | `/api/schemas/introspect`   | Manual schema introspection    |
| GET    | `/api/diagrams/{snapshotId}`| Full ER diagram for a snapshot |
| POST   | `/api/diagrams/subset`      | Subset ER diagram              |

## Project Structure

```
liquibase-analyzer/
├── src/main/java/rajib/dev/utility/
│   ├── controller/          # REST controllers
│   ├── service/             # Business logic
│   ├── model/               # Schema data records
│   ├── entity/              # JPA entities
│   ├── dto/                 # Request/response DTOs
│   └── repository/          # Spring Data repositories
├── frontend/
│   ├── src/
│   │   ├── components/      # React components (pages, diagram, nodes)
│   │   ├── api/             # API client (Axios)
│   │   └── types/           # TypeScript interfaces
│   ├── package.json
│   └── vite.config.ts
├── pom.xml
└── README.md
```

## Configuration

Key settings in `src/main/resources/application.yml`:

| Setting                  | Default                            | Description                     |
|--------------------------|------------------------------------|---------------------------------|
| `server.port`            | `8080`                             | Application port                |
| `spring.datasource.url`  | `jdbc:h2:file:./data/liquibase-analyzer` | Snapshot storage database |
| `app.upload-dir`         | `./data/uploads`                   | Changelog upload directory      |
| `spring.servlet.multipart.max-file-size` | `50MB`              | Max upload size                 |
