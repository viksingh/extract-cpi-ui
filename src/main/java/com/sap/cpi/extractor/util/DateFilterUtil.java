package com.sap.cpi.extractor.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for filtering CPI artifacts by creation/modification date.
 */
public class DateFilterUtil {

    public enum FilterMode {
        MODIFIED_SINCE("Modified since"),
        CREATED_SINCE("Created since"),
        CREATED_OR_MODIFIED_SINCE("Created or modified since");

        private final String label;

        FilterMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    /**
     * Parse a SAP CPI OData date string to a {@link LocalDate}.
     * <p>Handles:
     * <ul>
     *   <li>{@code /Date(epochMs)/} — OData v1 epoch-millisecond format</li>
     *   <li>Plain numeric strings — interpreted as epoch milliseconds</li>
     *   <li>ISO-8601 strings ({@code yyyy-MM-dd} or {@code yyyy-MM-ddTHH:mm:ss...})</li>
     * </ul>
     *
     * @param cpiDate the raw date string from the API
     * @return parsed {@link LocalDate}, or {@code null} if unparseable
     */
    public static LocalDate parseCpiDate(String cpiDate) {
        if (cpiDate == null || cpiDate.isBlank()) return null;
        try {
            if (cpiDate.contains("/Date(")) {
                // Strip everything except digits and minus sign, then parse as epoch ms
                String epochStr = cpiDate.replaceAll("[^\\d-]", "");
                long epoch = Long.parseLong(epochStr);
                return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (cpiDate.matches("\\d+")) {
                long epoch = Long.parseLong(cpiDate);
                return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
            }
            // ISO-8601: take only the date portion
            String datePart = cpiDate.length() > 10 ? cpiDate.substring(0, 10) : cpiDate;
            return LocalDate.parse(datePart);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Format a SAP CPI OData date string to a human-readable {@code yyyy-MM-dd HH:mm:ss} string.
     * Returns an empty string for null/blank input, and the original string if parsing fails.
     *
     * @param cpiDate the raw date string from the API (e.g. {@code /Date(1234567890000)/})
     * @return formatted date string, or empty string if input is null/blank
     */
    public static String formatCpiDate(String cpiDate) {
        if (cpiDate == null || cpiDate.isBlank()) return "";
        try {
            long epoch;
            if (cpiDate.contains("/Date(")) {
                String epochStr = cpiDate.replaceAll("[^\\d-]", "");
                epoch = Long.parseLong(epochStr);
            } else if (cpiDate.matches("\\d+")) {
                epoch = Long.parseLong(cpiDate);
            } else {
                return cpiDate;
            }
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(epoch),
                    ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return cpiDate;
        }
    }

    /**
     * Returns {@code true} if the artifact's dates satisfy the filter criteria.
     *
     * @param createdDate  raw created date string (may be null)
     * @param modifiedDate raw modified date string (may be null)
     * @param sinceDate    the lower bound date (inclusive); if null, always passes
     * @param mode         which date field(s) to evaluate
     * @return {@code true} if the artifact should be kept
     */
    public static boolean passesFilter(String createdDate, String modifiedDate,
                                       LocalDate sinceDate, FilterMode mode) {
        if (sinceDate == null) return true;
        return switch (mode) {
            case MODIFIED_SINCE -> {
                LocalDate mod = parseCpiDate(modifiedDate);
                yield mod != null && !mod.isBefore(sinceDate);
            }
            case CREATED_SINCE -> {
                LocalDate created = parseCpiDate(createdDate);
                yield created != null && !created.isBefore(sinceDate);
            }
            case CREATED_OR_MODIFIED_SINCE -> {
                LocalDate created = parseCpiDate(createdDate);
                LocalDate mod = parseCpiDate(modifiedDate);
                yield (created != null && !created.isBefore(sinceDate))
                        || (mod != null && !mod.isBefore(sinceDate));
            }
        };
    }
}
