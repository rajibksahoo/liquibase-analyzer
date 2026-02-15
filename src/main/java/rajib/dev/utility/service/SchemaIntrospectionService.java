package rajib.dev.utility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rajib.dev.utility.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class SchemaIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    public SchemaSnapshot introspect(Connection connection, String schemaName, String snapshotName) throws SQLException {
        log.info("Introspecting schema: {}", schemaName);

        DatabaseMetaData metaData = connection.getMetaData();
        List<String> tableNames = getTableNames(metaData, schemaName);
        List<TableInfo> tables = new ArrayList<>();

        for (String tableName : tableNames) {
            if (tableName.startsWith("databasechangelog")) continue; // skip Liquibase tables

            Map<String, Boolean> pkColumns = getPrimaryKeyColumns(metaData, schemaName, tableName);
            Map<String, Boolean> fkColumns = getForeignKeyColumnNames(metaData, schemaName, tableName);

            List<ColumnInfo> columns = getColumns(metaData, connection, schemaName, tableName, pkColumns, fkColumns);
            PrimaryKeyInfo primaryKey = getPrimaryKey(metaData, schemaName, tableName);
            List<ForeignKeyInfo> foreignKeys = getForeignKeys(metaData, schemaName, tableName);
            List<IndexInfo> indexes = getIndexes(connection, schemaName, tableName);
            List<UniqueConstraintInfo> uniqueConstraints = getUniqueConstraints(connection, schemaName, tableName);
            List<CheckConstraintInfo> checkConstraints = getCheckConstraints(connection, schemaName, tableName);
            String tableComment = getTableComment(connection, schemaName, tableName);

            tables.add(new TableInfo(
                    tableName, schemaName, tableComment, columns, primaryKey,
                    foreignKeys, indexes, uniqueConstraints, checkConstraints));
        }

        log.info("Introspected {} tables in schema {}", tables.size(), schemaName);
        return new SchemaSnapshot(snapshotName, schemaName, Instant.now(), tables);
    }

    private List<String> getTableNames(DatabaseMetaData metaData, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(null, schema, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private Map<String, Boolean> getPrimaryKeyColumns(DatabaseMetaData metaData, String schema, String table) throws SQLException {
        Map<String, Boolean> pkCols = new HashMap<>();
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                pkCols.put(rs.getString("COLUMN_NAME"), true);
            }
        }
        return pkCols;
    }

    private Map<String, Boolean> getForeignKeyColumnNames(DatabaseMetaData metaData, String schema, String table) throws SQLException {
        Map<String, Boolean> fkCols = new HashMap<>();
        try (ResultSet rs = metaData.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                fkCols.put(rs.getString("FKCOLUMN_NAME"), true);
            }
        }
        return fkCols;
    }

    private List<ColumnInfo> getColumns(DatabaseMetaData metaData, Connection conn,
                                         String schema, String table,
                                         Map<String, Boolean> pkColumns,
                                         Map<String, Boolean> fkColumns) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        Map<String, String> nativeTypes = getNativeTypes(conn, schema, table);
        Map<String, String> columnComments = getColumnComments(conn, schema, table);

        try (ResultSet rs = metaData.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                columns.add(new ColumnInfo(
                        colName,
                        rs.getString("TYPE_NAME"),
                        nativeTypes.getOrDefault(colName, rs.getString("TYPE_NAME")),
                        rs.getInt("ORDINAL_POSITION"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        rs.getString("COLUMN_DEF"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        columnComments.get(colName),
                        pkColumns.containsKey(colName),
                        fkColumns.containsKey(colName)
                ));
            }
        }
        columns.sort(Comparator.comparingInt(ColumnInfo::ordinalPosition));
        return columns;
    }

    private PrimaryKeyInfo getPrimaryKey(DatabaseMetaData metaData, String schema, String table) throws SQLException {
        List<String> pkColumns = new ArrayList<>();
        String pkName = null;
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                pkName = rs.getString("PK_NAME");
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pkColumns.isEmpty() ? null : new PrimaryKeyInfo(pkName, pkColumns);
    }

    private List<ForeignKeyInfo> getForeignKeys(DatabaseMetaData metaData, String schema, String table) throws SQLException {
        List<ForeignKeyInfo> fks = new ArrayList<>();
        try (ResultSet rs = metaData.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                fks.add(new ForeignKeyInfo(
                        rs.getString("FK_NAME"),
                        rs.getString("FKCOLUMN_NAME"),
                        rs.getString("PKTABLE_NAME"),
                        rs.getString("PKCOLUMN_NAME"),
                        ruleToString(rs.getShort("UPDATE_RULE")),
                        ruleToString(rs.getShort("DELETE_RULE"))
                ));
            }
        }
        return fks;
    }

    private List<IndexInfo> getIndexes(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT i.relname AS index_name,
                       array_agg(a.attname ORDER BY k.n) AS columns,
                       ix.indisunique AS is_unique,
                       am.amname AS index_type
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_am am ON am.oid = i.relam
                CROSS JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS k(attnum, n)
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                WHERE n.nspname = ? AND t.relname = ?
                  AND NOT ix.indisprimary
                GROUP BY i.relname, ix.indisunique, am.amname
                ORDER BY i.relname
                """;
        List<IndexInfo> indexes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] cols = (String[]) rs.getArray("columns").getArray();
                    indexes.add(new IndexInfo(
                            rs.getString("index_name"),
                            Arrays.asList(cols),
                            rs.getBoolean("is_unique"),
                            rs.getString("index_type")
                    ));
                }
            }
        }
        return indexes;
    }

    private List<UniqueConstraintInfo> getUniqueConstraints(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT con.conname AS constraint_name,
                       array_agg(a.attname ORDER BY k.n) AS columns
                FROM pg_constraint con
                JOIN pg_class t ON t.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                CROSS JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, n)
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                WHERE con.contype = 'u'
                  AND n.nspname = ? AND t.relname = ?
                GROUP BY con.conname
                """;
        List<UniqueConstraintInfo> constraints = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] cols = (String[]) rs.getArray("columns").getArray();
                    constraints.add(new UniqueConstraintInfo(
                            rs.getString("constraint_name"),
                            Arrays.asList(cols)
                    ));
                }
            }
        }
        return constraints;
    }

    private List<CheckConstraintInfo> getCheckConstraints(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT con.conname AS constraint_name,
                       pg_get_constraintdef(con.oid) AS definition
                FROM pg_constraint con
                JOIN pg_class t ON t.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE con.contype = 'c'
                  AND n.nspname = ? AND t.relname = ?
                ORDER BY con.conname
                """;
        List<CheckConstraintInfo> constraints = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    constraints.add(new CheckConstraintInfo(
                            rs.getString("constraint_name"),
                            rs.getString("definition")
                    ));
                }
            }
        }
        return constraints;
    }

    private Map<String, String> getNativeTypes(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT column_name, udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                """;
        Map<String, String> types = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString("column_name"), rs.getString("udt_name"));
                }
            }
        }
        return types;
    }

    private Map<String, String> getColumnComments(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT a.attname AS column_name,
                       col_description(c.oid, a.attnum) AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE n.nspname = ? AND c.relname = ?
                  AND a.attnum > 0 AND NOT a.attisdropped
                """;
        Map<String, String> comments = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String comment = rs.getString("comment");
                    if (comment != null) {
                        comments.put(rs.getString("column_name"), comment);
                    }
                }
            }
        }
        return comments;
    }

    private String getTableComment(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT obj_description(c.oid) AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("comment");
                }
            }
        }
        return null;
    }

    private String ruleToString(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            default -> "NO ACTION";
        };
    }
}
