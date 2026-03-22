package com.sakiv.cpi.extractor.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Holds the full dependency graph: all analyzed flows, dependency edges,
 * and provides analysis methods (cycles, impact, orphans, statistics).
 */
public class DependencyGraph {

    private final Map<String, IntegrationFlow> flowsById = new LinkedHashMap<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<String> unresolvedReferences = new ArrayList<>();

    public Map<String, IntegrationFlow> getFlowsById() { return flowsById; }
    public List<Dependency> getDependencies() { return dependencies; }
    public List<String> getUnresolvedReferences() { return unresolvedReferences; }

    public void addFlow(String flowId, IntegrationFlow flow) {
        flowsById.put(flowId, flow);
    }

    public void addDependency(Dependency dep) {
        dependencies.add(dep);
    }

    public void addUnresolvedReference(String reference) {
        unresolvedReferences.add(reference);
    }

    public List<Dependency> getOutgoingDependencies(String flowId) {
        return dependencies.stream()
                .filter(d -> flowId.equals(d.getSourceFlowId()))
                .collect(Collectors.toList());
    }

    public List<Dependency> getIncomingDependencies(String flowId) {
        return dependencies.stream()
                .filter(d -> flowId.equals(d.getTargetFlowId()))
                .collect(Collectors.toList());
    }

    public List<List<String>> detectCycles() {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Dependency dep : dependencies) {
            adjacency.computeIfAbsent(dep.getSourceFlowId(), k -> new HashSet<>())
                     .add(dep.getTargetFlowId());
        }

        List<List<String>> cycles = new ArrayList<>();
        Map<String, Integer> color = new HashMap<>();

        for (String node : adjacency.keySet()) {
            color.putIfAbsent(node, 0);
        }

        for (String node : adjacency.keySet()) {
            if (color.getOrDefault(node, 0) == 0) {
                dfsCycleDetect(node, adjacency, color, new LinkedList<>(), cycles);
            }
        }

        return cycles;
    }

    private void dfsCycleDetect(String node, Map<String, Set<String>> adjacency,
                                 Map<String, Integer> color,
                                 LinkedList<String> path, List<List<String>> cycles) {
        color.put(node, 1);
        path.addLast(node);

        for (String neighbor : adjacency.getOrDefault(node, Collections.emptySet())) {
            int neighborColor = color.getOrDefault(neighbor, 0);
            if (neighborColor == 1) {
                List<String> cycle = new ArrayList<>();
                int startIdx = path.indexOf(neighbor);
                for (int i = startIdx; i < path.size(); i++) {
                    cycle.add(path.get(i));
                }
                cycle.add(neighbor);
                cycles.add(cycle);
            } else if (neighborColor == 0) {
                dfsCycleDetect(neighbor, adjacency, color, path, cycles);
            }
        }

        path.removeLast();
        color.put(node, 2);
    }

    public List<String> getOrphanFlows() {
        Set<String> connected = new HashSet<>();
        for (Dependency dep : dependencies) {
            connected.add(dep.getSourceFlowId());
            connected.add(dep.getTargetFlowId());
        }
        return flowsById.keySet().stream()
                .filter(id -> !connected.contains(id))
                .collect(Collectors.toList());
    }

    public Map<DependencyType, Long> getDependencyCountsByType() {
        return dependencies.stream()
                .collect(Collectors.groupingBy(Dependency::getType, Collectors.counting()));
    }

    /**
     * Aggregate flow-level dependencies into package-level dependencies.
     * Returns a list of PackageDependency objects representing cross-package links.
     */
    public List<PackageDependency> getPackageDependencies(Map<String, String> packageIdToName) {
        // Group dependencies by (sourcePackageId, targetPackageId) pair
        Map<String, PackageDependency> pkgDepMap = new LinkedHashMap<>();

        for (Dependency dep : dependencies) {
            String srcPkg = dep.getSourcePackageId();
            String tgtPkg = dep.getTargetPackageId();
            if (srcPkg == null || tgtPkg == null) continue;

            String key = srcPkg + " -> " + tgtPkg;
            PackageDependency pkgDep = pkgDepMap.computeIfAbsent(key, k ->
                    new PackageDependency(
                            srcPkg, packageIdToName.getOrDefault(srcPkg, srcPkg),
                            tgtPkg, packageIdToName.getOrDefault(tgtPkg, tgtPkg)));
            pkgDep.addFlowDependency(dep);
        }

        return new ArrayList<>(pkgDepMap.values());
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== iFlow Dependency Analysis Summary ===\n\n");
        sb.append("Total iFlows analyzed: ").append(flowsById.size()).append("\n");
        sb.append("Total dependencies found: ").append(dependencies.size()).append("\n");
        sb.append("Unresolved references: ").append(unresolvedReferences.size()).append("\n\n");

        sb.append("--- Dependencies by Type ---\n");
        Map<DependencyType, Long> counts = getDependencyCountsByType();
        for (DependencyType type : DependencyType.values()) {
            long count = counts.getOrDefault(type, 0L);
            if (count > 0) {
                sb.append("  ").append(type.getDisplayName()).append(": ").append(count).append("\n");
            }
        }

        List<List<String>> cycles = detectCycles();
        sb.append("\n--- Circular Dependencies ---\n");
        if (cycles.isEmpty()) {
            sb.append("  None detected\n");
        } else {
            sb.append("  ").append(cycles.size()).append(" cycle(s) detected:\n");
            for (int i = 0; i < cycles.size(); i++) {
                List<String> cycle = cycles.get(i);
                List<String> names = cycle.stream()
                        .map(id -> {
                            IntegrationFlow f = flowsById.get(id);
                            return f != null ? f.getName() : id;
                        })
                        .collect(Collectors.toList());
                sb.append("  ").append(i + 1).append(". ").append(String.join(" -> ", names)).append("\n");
            }
        }

        List<String> orphans = getOrphanFlows();
        sb.append("\n--- Orphan Flows (no dependencies) ---\n");
        sb.append("  ").append(orphans.size()).append(" orphan flow(s)\n");

        if (!unresolvedReferences.isEmpty()) {
            sb.append("\n--- Unresolved References ---\n");
            for (String ref : unresolvedReferences) {
                sb.append("  - ").append(ref).append("\n");
            }
        }

        return sb.toString();
    }
}
