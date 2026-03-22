package com.sakiv.cpi.extractor.model;

/**
 * Represents a dependency edge between two iFlows.
 */
public class Dependency {

    private String sourceFlowId;
    private String sourceFlowName;
    private String sourcePackageId;
    private String targetFlowId;
    private String targetFlowName;
    private String targetPackageId;
    private DependencyType type;
    private String details;

    public Dependency() {}

    public Dependency(String sourceFlowId, String sourceFlowName, String sourcePackageId,
                      String targetFlowId, String targetFlowName, String targetPackageId,
                      DependencyType type, String details) {
        this.sourceFlowId = sourceFlowId;
        this.sourceFlowName = sourceFlowName;
        this.sourcePackageId = sourcePackageId;
        this.targetFlowId = targetFlowId;
        this.targetFlowName = targetFlowName;
        this.targetPackageId = targetPackageId;
        this.type = type;
        this.details = details;
    }

    public String getSourceFlowId() { return sourceFlowId; }
    public void setSourceFlowId(String sourceFlowId) { this.sourceFlowId = sourceFlowId; }

    public String getSourceFlowName() { return sourceFlowName; }
    public void setSourceFlowName(String sourceFlowName) { this.sourceFlowName = sourceFlowName; }

    public String getSourcePackageId() { return sourcePackageId; }
    public void setSourcePackageId(String sourcePackageId) { this.sourcePackageId = sourcePackageId; }

    public String getTargetFlowId() { return targetFlowId; }
    public void setTargetFlowId(String targetFlowId) { this.targetFlowId = targetFlowId; }

    public String getTargetFlowName() { return targetFlowName; }
    public void setTargetFlowName(String targetFlowName) { this.targetFlowName = targetFlowName; }

    public String getTargetPackageId() { return targetPackageId; }
    public void setTargetPackageId(String targetPackageId) { this.targetPackageId = targetPackageId; }

    public DependencyType getType() { return type; }
    public void setType(DependencyType type) { this.type = type; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    @Override
    public String toString() {
        return sourceFlowName + " --[" + type.getDisplayName() + "]--> " + targetFlowName +
               (details != null ? " (" + details + ")" : "");
    }
}
