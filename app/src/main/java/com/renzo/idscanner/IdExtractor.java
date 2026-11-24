package com.renzo.idscanner;

import android.content.Context;
import java.io.*;
import java.util.regex.*;

public class IdExtractor {
    public static String getGaid(Context context) {
        try {
            Class<?> c = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
            Object info = c.getMethod("getAdvertisingIdInfo", Context.class).invoke(null, context);
            return (String) info.getClass().getMethod("getId").invoke(info);
        } catch (Exception e) { return "غير متاح"; }
    }

    public static String extractAppsFlyerId(String pkg) {
        String xml = readRoot("/data/data/" + pkg + "/shared_prefs/appsflyer-data.xml");
        if (xml == null || xml.isEmpty()) return null;
        Matcher m = Pattern.compile("<string name=\"(uid|appsflyer_id)\">([^<]+)</string>").matcher(xml);
        return m.find() ? m.group(2) : null;
    }

    public static boolean hasRoot() {
        String r = readRoot("/system/build.prop");
        return r != null && !r.isEmpty();
    }

    private static String readRoot(String path) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("cat " + path + "\nexit\n");
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
