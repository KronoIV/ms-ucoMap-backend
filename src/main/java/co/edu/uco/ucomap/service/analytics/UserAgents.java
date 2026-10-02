package co.edu.uco.ucomap.service.analytics;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Navegador y sistema operativo a partir del User-Agent que ya se guarda en cada ping. */
public final class UserAgents {

    private static final Pattern IOS = Pattern.compile("(?:iPhone|CPU) OS (\\d+)(?:_(\\d+))?");
    private static final Pattern ANDROID = Pattern.compile("Android (\\d+)");

    private UserAgents() {}

    public static String browser(String ua) {
        if (ua == null || ua.isBlank() || "Unknown".equals(ua)) return "Desconocido";
        if (ua.contains("SamsungBrowser")) return "Samsung Internet";
        if (ua.contains("EdgA/") || ua.contains("EdgiOS/") || ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("FxiOS/") || ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("CriOS/")) return "Chrome";
        // Vista web dentro de otra app (Instagram, WhatsApp…): no es un navegador completo
        if (ua.contains("; wv)") || ua.contains("Instagram") || ua.contains("FBAN") || ua.contains("FBAV")) return "Vista web en app";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/") && ua.contains("Version/")) return "Safari";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "Vista web en app";
        return "Otro";
    }

    public static String os(String ua) {
        if (ua == null || ua.isBlank() || "Unknown".equals(ua)) return "Desconocido";
        Matcher ios = IOS.matcher(ua);
        if ((ua.contains("iPhone") || ua.contains("iPad")) && ios.find()) {
            return "iOS " + ios.group(1) + (ios.group(2) != null ? "." + ios.group(2) : "");
        }
        Matcher android = ANDROID.matcher(ua);
        if (android.find()) return "Android " + android.group(1);
        String lower = ua.toLowerCase(Locale.ROOT);
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("mac os")) return "macOS";
        if (lower.contains("linux")) return "Linux";
        return "Otro";
    }
}
