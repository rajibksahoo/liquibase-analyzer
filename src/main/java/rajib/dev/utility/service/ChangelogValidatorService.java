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
 * Two responsibilities:
 * <ol>
 *   <li><b>Detect unresolved properties</b> – properties whose values reference a parameter
 *       that is either the property itself (self-referential) or not defined anywhere in the
 *       changelog.  These must be supplied by the user before execution.</li>
 *   <li><b>Cycle detection</b> – after user values are applied, validate that no circular
 *       expansion chain remains; any remaining cycle would still cause a StackOverflowError
 *       in Liquibase's {@code ExpressionExpander.expandExpressions()}.</li>
 * </ol>
 */
@Service
public class ChangelogValidatorService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogValidatorService.class);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans the master changelog (and all included files) for properties whose values
     * contain {@code ${X}} where X is either the property's own name (self-referential)
     * or not defined anywhere in the changelog.  These properties require user input.
     *
     * @return ordered list of property names that must be supplied by the user
     */
    public List<String> findUnresolvedProperties(Path masterChangelog) throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        collectProperties(masterChangelog, properties, new HashSet<>());

        List<String> unresolved = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String name  = entry.getKey();
            String value = entry.getValue();
            Matcher m = PROPERTY_PATTERN.matcher(value);
            while (m.find()) {
                String referenced = m.group(1);
                boolean selfRef    = referenced.equals(name);
                boolean external   = !properties.containsKey(referenced);
                if (selfRef || external) {
                    unresolved.add(name);
                    break;
                }
            }
        }
        log.debug("Found {} unresolved properties in {}", unresolved.size(), masterChangelog.getFileName());
        return unresolved;
    }

    /**
     * Validates that no circular property-expansion chain exists after applying
     * {@code userValues}.  Call this just before {@code liquibase.update()} to catch
     * any cycles that would cause StackOverflowError.
     *
     * @param userValues values provided by the user for previously-unresolved properties;
     *                   these are treated as concrete (non-referential) strings, effectively
     *                   cutting any edges they would otherwise contribute to the graph
     */
    public void validatePropertyExpressions(Path masterChangelog,
                                            Map<String, String> userValues) throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        collectProperties(masterChangelog, properties, new HashSet<>());
        // User-provided values replace the self-referential placeholders
        properties.putAll(userValues);
        log.debug("Validating {} properties ({} user-provided) for cycles",
                properties.size(), userValues.size());
        detectCycles(properties);
        log.debug("Property expression validation passed");
    }

    /** Overload for callers that have no user values (e.g. changelogs with no parameters). */
    public void validatePropertyExpressions(Path masterChangelog) throws Exception {
        validatePropertyExpressions(masterChangelog, Map.of());
    }

    // -------------------------------------------------------------------------
    // Property collection
    // -------------------------------------------------------------------------

    void collectProperties(Path changelogPath, Map<String, String> properties,
                           Set<String> visitedFiles) throws Exception {
        String canonical = changelogPath.toAbsolutePath().normalize().toString();
        if (!visitedFiles.add(canonical)) {
            return;
        }
        if (!changelogPath.toFile().exists()) {
            log.warn("Changelog file not found, skipping: {}", changelogPath);
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null); // suppress DTD-not-found noise
        Document doc = builder.parse(changelogPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList propertyNodes = doc.getElementsByTagName("property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element prop = (Element) propertyNodes.item(i);
            String name  = prop.getAttribute("name");
            String value = prop.getAttribute("value");
            if (!name.isBlank()) {
                properties.putIfAbsent(name, value); // first definition wins
            }
        }

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
                List<String> pathList = new ArrayList<>(path);
                int start = pathList.indexOf(neighbor);
                List<String> cycle = new ArrayList<>(pathList.subList(start, pathList.size()));
                cycle.add(neighbor);
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
