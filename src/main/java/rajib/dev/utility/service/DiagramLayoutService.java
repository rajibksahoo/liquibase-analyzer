package rajib.dev.utility.service;

import org.springframework.stereotype.Service;
import rajib.dev.utility.dto.*;
import rajib.dev.utility.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiagramLayoutService {

    private static final double NODE_WIDTH = 300;
    private static final double NODE_X_GAP = 80;
    private static final double NODE_Y_GAP = 60;
    private static final double ROW_HEIGHT = 28;
    private static final double HEADER_HEIGHT = 40;

    public DiagramDataDto generateDiagram(SchemaSnapshot snapshot, Long snapshotId) {
        List<TableInfo> tables = snapshot.tables();
        return buildDiagram(tables, snapshotId, snapshot.name());
    }

    public DiagramDataDto generateSubset(SchemaSnapshot snapshot, Long snapshotId,
                                          List<String> selectedTables, boolean includeIndirect) {
        List<TableInfo> allTables = snapshot.tables();
        Set<String> relevantTables;

        if (includeIndirect) {
            relevantTables = computeTransitiveClosure(allTables, new HashSet<>(selectedTables));
        } else {
            relevantTables = new HashSet<>(selectedTables);
        }

        List<TableInfo> subset = allTables.stream()
                .filter(t -> relevantTables.contains(t.name()))
                .toList();

        return buildDiagram(subset, snapshotId, snapshot.name());
    }

    private DiagramDataDto buildDiagram(List<TableInfo> tables, Long snapshotId, String snapshotName) {
        // Build adjacency for topological sort
        Map<String, Set<String>> incoming = new HashMap<>();
        Map<String, Set<String>> outgoing = new HashMap<>();
        Set<String> tableNames = tables.stream().map(TableInfo::name).collect(Collectors.toSet());

        for (TableInfo table : tables) {
            incoming.putIfAbsent(table.name(), new HashSet<>());
            outgoing.putIfAbsent(table.name(), new HashSet<>());
            for (ForeignKeyInfo fk : table.foreignKeys()) {
                if (tableNames.contains(fk.referencedTable())) {
                    incoming.computeIfAbsent(table.name(), k -> new HashSet<>()).add(fk.referencedTable());
                    outgoing.computeIfAbsent(fk.referencedTable(), k -> new HashSet<>()).add(table.name());
                }
            }
        }

        // Topological sort (Kahn's algorithm)
        List<String> sorted = topologicalSort(tableNames, incoming);
        Map<String, Integer> levelMap = assignLevels(sorted, incoming);

        // Group tables by level
        Map<Integer, List<String>> levelGroups = new TreeMap<>();
        for (var entry : levelMap.entrySet()) {
            levelGroups.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // Position nodes
        Map<String, TableInfo> tableMap = tables.stream()
                .collect(Collectors.toMap(TableInfo::name, t -> t));

        List<ErNode> nodes = new ArrayList<>();
        double currentY = 0;

        for (var levelEntry : levelGroups.entrySet()) {
            List<String> levelTables = levelEntry.getValue();
            double currentX = 0;
            double maxHeight = 0;

            for (String tableName : levelTables) {
                TableInfo table = tableMap.get(tableName);
                if (table == null) continue;

                double nodeHeight = HEADER_HEIGHT + table.columns().size() * ROW_HEIGHT;
                maxHeight = Math.max(maxHeight, nodeHeight);

                List<ErNode.ErColumnData> columnData = table.columns().stream()
                        .map(c -> new ErNode.ErColumnData(
                                c.name(), c.nativeType(), c.nullable(),
                                c.primaryKey(), c.foreignKey(), c.defaultValue()))
                        .toList();

                nodes.add(new ErNode(
                        tableName,
                        "tableNode",
                        new ErNode.ErNodePosition(currentX, currentY),
                        new ErNode.ErNodeData(
                                table.name(), table.schema(), table.comment(),
                                columnData,
                                table.primaryKey() != null ? table.primaryKey().name() : null)
                ));

                currentX += NODE_WIDTH + NODE_X_GAP;
            }
            currentY += maxHeight + NODE_Y_GAP;
        }

        // Generate edges
        List<ErEdge> edges = new ArrayList<>();
        for (TableInfo table : tables) {
            for (ForeignKeyInfo fk : table.foreignKeys()) {
                if (!tableNames.contains(fk.referencedTable())) continue;

                edges.add(new ErEdge(
                        fk.name(),
                        table.name(),
                        fk.referencedTable(),
                        table.name() + "-" + fk.columnName() + "-source",
                        fk.referencedTable() + "-" + fk.referencedColumn() + "-target",
                        "relationshipEdge",
                        new ErEdge.ErEdgeData(
                                fk.name(), fk.columnName(), fk.referencedColumn(),
                                fk.deleteRule(), fk.updateRule())
                ));
            }
        }

        return new DiagramDataDto(snapshotId, snapshotName, nodes, edges);
    }

    private List<String> topologicalSort(Set<String> tableNames, Map<String, Set<String>> incoming) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String name : tableNames) {
            inDegree.put(name, incoming.getOrDefault(name, Set.of()).size());
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            for (String name : tableNames) {
                Set<String> deps = incoming.getOrDefault(name, Set.of());
                if (deps.contains(current)) {
                    int newDegree = inDegree.get(name) - 1;
                    inDegree.put(name, newDegree);
                    if (newDegree == 0) queue.add(name);
                }
            }
        }

        // Add any remaining (cyclic) tables
        for (String name : tableNames) {
            if (!sorted.contains(name)) sorted.add(name);
        }
        return sorted;
    }

    private Map<String, Integer> assignLevels(List<String> sorted, Map<String, Set<String>> incoming) {
        Map<String, Integer> levels = new HashMap<>();
        for (String name : sorted) {
            int maxParentLevel = -1;
            for (String parent : incoming.getOrDefault(name, Set.of())) {
                maxParentLevel = Math.max(maxParentLevel, levels.getOrDefault(parent, 0));
            }
            levels.put(name, maxParentLevel + 1);
        }
        return levels;
    }

    private Set<String> computeTransitiveClosure(List<TableInfo> allTables, Set<String> seed) {
        // Build bidirectional adjacency from FK relationships
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (TableInfo table : allTables) {
            adjacency.putIfAbsent(table.name(), new HashSet<>());
            for (ForeignKeyInfo fk : table.foreignKeys()) {
                adjacency.computeIfAbsent(table.name(), k -> new HashSet<>()).add(fk.referencedTable());
                adjacency.computeIfAbsent(fk.referencedTable(), k -> new HashSet<>()).add(table.name());
            }
        }

        // BFS from seed tables
        Set<String> visited = new HashSet<>(seed);
        Queue<String> queue = new LinkedList<>(seed);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }
}
