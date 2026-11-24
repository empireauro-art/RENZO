package com.renzo.idscanner;

/** Builds the user-requested four-field export from the device GAID and a stored AppsFlyer record. */
final class SelectionExport {
    private SelectionExport() {}

    static String appsFlyerId(String record) {
        String[] fields = fields(record);
        return fields == null ? null : nonEmpty(fields[0]);
    }

    static String androidId(String record) {
        String[] fields = fields(record);
        if (fields == null) return null;
        // RootAttributionScanner stores: AF_INSTALLATION | androidIdCached | app SSAID | UA.
        // Prefer the per-app SSAID (field 3), then the SDK-cached Android ID (field 2).
        String value = nonEmpty(fields[2]);
        return value != null ? value : nonEmpty(fields[1]);
    }

    static String userAgent(String record) {
        String[] fields = fields(record);
        return fields == null ? null : nonEmpty(fields[3]);
    }

    /** Returns exactly GAID|AppsFlyer ID|AID|UA, or null when a required field is absent. */
    static String build(String gaid, String appsFlyerRecord) {
        String id = appsFlyerId(appsFlyerRecord);
        String aid = androidId(appsFlyerRecord);
        String ua = userAgent(appsFlyerRecord);
        if (nonEmpty(gaid) == null || id == null || aid == null || ua == null) return null;
        return gaid + "|" + id + "|" + aid + "|" + ua;
    }

    private static String[] fields(String record) {
        if (record == null) return null;
        String[] parts = record.split("\\|", -1);
        return parts.length == 4 ? parts : null;
    }

    private static String nonEmpty(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
