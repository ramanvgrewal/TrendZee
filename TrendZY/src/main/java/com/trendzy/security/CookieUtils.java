package com.trendzy.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

public class CookieUtils {

    public static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        // Important: Use Secure in production. We can just rely on the proxy if needed, 
        // but it's safe to set it if we know we are on HTTPS. 
        // For simplicity, letting Spring/Tomcat handle Secure flag via proxy config is okay,
        // or we can explicitly set it to true if X-Forwarded-Proto is https.
        response.addCookie(cookie);
    }

    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
    }

    public static String serialize(Object object) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos);
                 java.io.ObjectOutputStream objectOut = new java.io.ObjectOutputStream(gzipOut)) {
                objectOut.writeObject(object);
            }
            return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(decoded);
            try (java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(bais);
                 java.io.ObjectInputStream objectIn = new java.io.ObjectInputStream(gzipIn)) {
                return cls.cast(objectIn.readObject());
            }
        } catch (Exception e) {
            return null;
        }
    }
}
