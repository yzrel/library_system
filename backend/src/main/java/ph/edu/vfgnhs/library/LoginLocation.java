package ph.edu.vfgnhs.library;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;

class LoginLocation {
    record Snapshot(String ipAddress, String location) {}

    static Snapshot from(HttpServletRequest request) {
        String ip = clientIp(request);
        return new Snapshot(ip, describe(ip, request.getHeader("User-Agent")));
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) return forwarded;
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (realIp != null) return realIp;
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? null : remote.trim();
    }

    static String describe(String ip, String userAgent) {
        String place = placeOf(ip);
        String device = deviceOf(userAgent);
        if (device.isEmpty()) return place;
        return place + " · " + device;
    }

    private static String placeOf(String ip) {
        if (ip == null || ip.isBlank() || isLoopback(ip)) return "This computer";
        if (isPrivate(ip)) return "School network (" + ip + ")";
        return ip;
    }

    private static String deviceOf(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "";
        String browser = browserOf(userAgent);
        String platform = platformOf(userAgent);
        if (browser.isEmpty()) return platform;
        if (platform.isEmpty()) return browser;
        return browser + " on " + platform;
    }

    private static String browserOf(String userAgent) {
        if (userAgent.contains("Edg/")) return "Edge";
        if (userAgent.contains("Chrome/")) return "Chrome";
        if (userAgent.contains("Firefox/")) return "Firefox";
        if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) return "Safari";
        return "";
    }

    private static String platformOf(String userAgent) {
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac OS")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        return "";
    }

    private static boolean isLoopback(String ip) {
        if ("127.0.0.1".equals(ip) || "https://example.net/id/garnet".equals(ip) || "::1".equals(ip)) return true;
        try {
            return InetAddress.getByName(ip).isLoopbackAddress();
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isPrivate(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception ex) {
            return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.");
        }
    }

    private static String firstHeaderValue(String header) {
        if (header == null || header.isBlank()) return null;
        String value = header.split(",")[0].trim();
        return value.isEmpty() ? null : value;
    }
}
