package rajib.dev.utility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Liquibase changelog property expressions before execution to prevent
 * StackOverflowError in ExpressionExpander.expandExpressions() caused by circular
 * or self-referential ${param} definitions.
 */
@Service
public class ChangelogValidatorService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogValidatorService.class);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * Parses the master changelog and all included changelogs, collects every
     * {@code <property name="..." value="...">} declaration, then detects cycles
     * in the resulting expression-expansion graph.
     *
     * @throws IllegalStateException if a circular reference is found
     */
    public void validatePropertyExpressions(Path masterChangelog) throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        collectProperties(masterChangelog, properties, new HashSet<>());
        log.debug("Collected {} changelog properties for cycle validation", properties.size());
        detectCycles(properties);
        log.debug("Property expression validation passed");
    }

    // -------------------------------------------------------------------------
    // Property collection
    // -------------------------------------------------------------------------

    private void collectProperties(Path changelogPath, Map<String, String> properties,
                                   Set<String> visitedFiles) throws Exception {
        String canonical = changelogPath.toAbsolutePath().normalize().toString();
        if (!visitedFiles.add(canonical)) {
            return; // already processed
        }

        if (!changelogPath.toFile().exists()) {
            log.warn("Changelog file not found, skipping validation: {}", changelogPath);
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entity resolution to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        // Suppress "Cannot find declaration of element" DTD warnings
        builder.setErrorHandler(null);
        Document doc = builder.parse(changelogPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList propertyNodes = doc.getElementsByTagName("property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element prop = (Element) propertyNodes.item(i);
            String name = prop.getAttribute("name");
            String value = prop.getAttribute("value");
            if (!name.isBlank()) {
                // First definition wins (matches Liquibase behaviour)
                properties.putIfAbsent(name, value);
            }
        }

        // Recurse into included changelogs
        Path dir = changelogPath.getParent();
        NodeList includeNodes = doc.getElementsByTagName("include");
        for (int i = 0; i < includeNodes.getLength(); i++) {
            Element include = (Element) includeNodes.item(i);
            String file = include.getAttribute("file");
            if (!file.isBlank()) {
                Path included = dir.resolve(file).toAbsolutePath().normalize();
                collectProperties(included, properties, visitedFiles);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cycle detection (DFS on the expression-expansion directed graph)
    // -------------------------------------------------------------------------

    private void detectCycles(Map<String, String> properties) {
        // Build adjacency list: A -> B when property A's value contains ${B}
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Set<String> refs = new LinkedHashSet<>();
            Matcher m = PROPERTY_PATTERN.matcher(entry.getValue());
            while (m.find()) {
                String referenced = m.group(1);
                if (properties.containsKey(referenced)) {
                    refs.add(referenced);
                }
            }
            graph.put(entry.getKey(), refs);
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, inStack, new ArrayDeque<>());
            }
        }
    }

    private void dfs(String node, Map<String, Set<String>> graph,
                     Set<String> visited, Set<String> inStack, Deque<String> path) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);

        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            if (inStack.contains(neighbor)) {
                // Reconstruct the cycle portion of the path
                List<String> pathList = new ArrayList<>(path);
                int start = pathList.indexOf(neighbor);
                List<String> cycle = new ArrayList<>(pathList.subList(start, pathList.size()));
                cycle.add(neighbor); // close the cycle
                throw new IllegalStateException(
                        "Circular property reference detected in Liquibase changelog: "
                        + String.join(" -> ", cycle)
                        + ". This would cause StackOverflowError in Liquibase's "
                        + "ExpressionExpander.expandExpressions(). "
                        + "Remove the circular dependency from your changelog properties.");
            }
            if (!visited.contains(neighbor)) {
                dfs(neighbor, graph, visited, inStack, path);
            }
        }

        path.removeLast();
        inStack.remove(node);
    }
}
