package com.sakiv.cpi.extractor.model;

import java.util.*;

/**
 * Aggregated dependency between two packages, derived from individual iFlow-level dependencies.
 */
public class PackageDependency {

    private final String sourcePackageId;
    private final String sourcePackageName;
    private final String targetPackageId;
    private final String targetPackageName;
    private final List<Dependency> flowDependencies = new ArrayList<>();
    private final Set<DependencyType> dependencyTypes = new LinkedHashSet<>();

    public PackageDependency(String sourcePackageId, String sourcePackageName,
                              String targetPackageId, String targetPackageName) {
        this.sourcePackageId = sourcePackageId;
        this.sourcePackageName = sourcePackageName;
        this.targetPackageId = targetPackageId;
        this.targetPackageName = targetPackageName;
    }

    public void addFlowDependency(Dependency dep) {
        flowDependencies.add(dep);
        dependencyTypes.add(dep.getType());
    }

    public String getSourcePackageId() { return sourcePackageId; }
    public String getSourcePackageName() { return sourcePackageName; }
    public String getTargetPackageId() { return targetPackageId; }
    public String getTargetPackageName() { return targetPackageName; }
    public List<Dependency> getFlowDependencies() { return flowDependencies; }
    public Set<DependencyType> getDependencyTypes() { return dependencyTypes; }
    public int getStrength() { return flowDependencies.size(); }

    public boolean isCrossPackage() {
        return !sourcePackageId.equals(targetPackageId);
    }

    public String getDependencyTypesDisplay() {
        StringBuilder sb = new StringBuilder();
        for (DependencyType type : dependencyTypes) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(type.getDisplayName());
        }
        return sb.toString();
    }
}
