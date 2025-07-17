package searchengine.services.tasks;

import java.util.Set;

public class UrlFilter {

    private static final Set<String> blockedExtensions = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp",
            ".js", ".css", ".ico", ".woff", ".woff2", ".ttf", ".eot",
            ".zip", ".rar", ".7z", ".pdf", ".doc", ".docx", ".xls", ".xlsx"
    );

    public static boolean isSkippable(String url) {
        String lowered = url.toLowerCase();

        // 1. Файл с неподходящим расширением
        for (String ext : blockedExtensions) {
            if (lowered.contains("?")) {
                lowered = lowered.substring(0, lowered.indexOf('?'));
            }
            if (lowered.endsWith(ext)) {
                return true;
            }
        }

        // 2. Якоря (#) и параметры (?)
        if (url.contains("#") || url.contains("?")) {
            return true;
        }

        return false;
    }
}

