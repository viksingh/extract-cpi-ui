package com.sakiv.cpi.extractor.util;

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
        CREATED_OR_MODIFIED_SINCE("Created or modified since"),
        DEPLOYED_SINCE("Deployed since");

        private final String label;

        // @author Vikas Singh | Created: 2025-12-28
        FilterMode(String label) { this.label = label; }

        // @author Vikas Singh | Created: 2025-12-29
        @Override
        public String toString() { return label; }
    }

    /**
     * Parse a SAP CPI OData date string to a {@link LocalDate}.
     * <p>Handles:
     * <ul>
     *   <li>{@code /Date(epochMs)/} — OData v2 design-time format</li>
     *   <li>{@code /Date(epochMs+HHmm)/} or {@code /Date(epochMs-HHmm)/} — OData v2 runtime format
     *       (timezone offset is ignored; epoch ms is always UTC-based)</li>
     *   <li>Plain numeric strings — interpreted as epoch milliseconds</li>
     *   <li>ISO-8601 strings ({@code yyyy-MM-dd} or {@code yyyy-MM-ddTHH:mm:ss...})</li>
     * </ul>
     *
     * @param cpiDate the raw date string from the API
     * @return parsed {@link LocalDate}, or {@code null} if unparseable
     */
    // @author Vikas Singh | Created: 2025-12-30
    public static LocalDate parseCpiDate(String cpiDate) {
        if (cpiDate == null || cpiDate.isBlank()) return null;
        try {
            if (cpiDate.contains("/Date(")) {
                long epoch = extractEpochMs(cpiDate);
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
     * @param cpiDate the raw date string from the API (e.g. {@code /Date(1234567890000+0530)/})
     * @return formatted date string, or empty string if input is null/blank
     */
    // @author Vikas Singh | Created: 2026-01-02
    public static String formatCpiDate(String cpiDate) {
        if (cpiDate == null || cpiDate.isBlank()) return "";
        try {
            long epoch;
            if (cpiDate.contains("/Date(")) {
                epoch = extractEpochMs(cpiDate);
            } else if (cpiDate.matches("\\d+")) {
                epoch = Long.parseLong(cpiDate);
            } else {
                // ISO-8601 datetime (e.g. "2025-02-21T02:04:31.165") — reformat consistently
                String normalized = cpiDate.length() > 10 ? cpiDate.substring(0, 19).replace('T', ' ') : cpiDate;
                return normalized;
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
     * Extract the epoch-millisecond value from an OData {@code /Date(...)/} string,
     * correctly handling an optional timezone offset suffix.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /Date(1729000000000)/}       → 1729000000000</li>
     *   <li>{@code /Date(1729000000000+0530)/}  → 1729000000000</li>
     *   <li>{@code /Date(1729000000000-0800)/}  → 1729000000000</li>
     *   <li>{@code /Date(-62135596800000)/}      → -62135596800000 (negative epoch)</li>
     * </ul>
     */
    // @author Vikas Singh | Created: 2026-01-03
    private static long extractEpochMs(String cpiDate) {
        int start = cpiDate.indexOf('(') + 1;
        int end = cpiDate.indexOf(')', start);
        if (end < 0) end = cpiDate.length();
        String inner = cpiDate.substring(start, end); // e.g. "1729000000000+0530"

        // Find timezone offset separator: a '+' anywhere, or a '-' that is NOT the leading sign
        int tzPlus = inner.indexOf('+');
        int tzMinus = inner.lastIndexOf('-');
        // A leading '-' at index 0 is the sign of a negative epoch, not a timezone separator
        if (tzMinus == 0) tzMinus = -1;

        int tzSep = -1;
        if (tzPlus >= 0) tzSep = tzPlus;
        else if (tzMinus > 0) tzSep = tzMinus;

        String epochPart = (tzSep >= 0) ? inner.substring(0, tzSep) : inner;
        return Long.parseLong(epochPart.trim());
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
    // @author Vikas Singh | Created: 2026-01-03
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
            // DEPLOYED_SINCE is handled in applyDateFilter via deployedAt/deployedOn;
            // design-time artifacts (packages, VMs) are not filtered by this mode.
            case DEPLOYED_SINCE -> true;
        };
    }
}
