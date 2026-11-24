package com.renzo.idscanner;

public final class SelectionExportTest {
    public static void main(String[] args) {
        String record = "1795537734637-697428527267811949||48f0acf8d7d01217|"
                + "Mozilla/5.0 (Linux; Android 10; PBEM00 Build/QKQ1.190918.001; wv)"
                + " AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.7103.127"
                + " Mobile Safari/537.36";
        String gaid = "5de02817-44cf-4d4b-88cc-c672addd8d83";

        check("1795537734637-697428527267811949".equals(
                SelectionExport.appsFlyerId(record)), "AppsFlyer ID should be field one");
        check("48f0acf8d7d01217".equals(SelectionExport.androidId(record)),
                "AID should use the per-app SSAID field when present");
        check(SelectionExport.userAgent(record).startsWith("Mozilla/5.0"),
                "UA should be field four");
        check((gaid + "|1795537734637-697428527267811949|48f0acf8d7d01217|"
                        + SelectionExport.userAgent(record)).equals(
                SelectionExport.build(gaid, record)),
                "Full export must be GAID|ID|AID|UA");

        String cachedFallback = "af-id|cached-aid||Mozilla/5.0 test";
        check("cached-aid".equals(SelectionExport.androidId(cachedFallback)),
                "Cached AID should be used when per-app SSAID is empty");
        check(SelectionExport.build(null, record) == null,
                "Full export must not be built without GAID");
        check(SelectionExport.build(gaid, "malformed") == null,
                "Malformed AppsFlyer record must not produce an export");

        System.out.println("SelectionExportTest: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
