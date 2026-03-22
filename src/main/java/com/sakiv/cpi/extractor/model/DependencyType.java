package com.sakiv.cpi.extractor.model;

/**
 * Types of dependencies between iFlows.
 */
public enum DependencyType {

    PROCESS_DIRECT("ProcessDirect", "Direct iFlow-to-iFlow call via ProcessDirect adapter"),
    JMS_QUEUE("JMS Queue", "iFlow-to-iFlow linkage via shared JMS queue");

    private final String displayName;
    private final String description;

    DependencyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    @Override
    public String toString() { return displayName; }
}
