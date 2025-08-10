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

        if (lowered.contains("?")) {
            lowered = lowered.substring(0, lowered.indexOf('?'));
        }

        for (String ext : blockedExtensions) {
            if (lowered.endsWith(ext)) {
                return true;
            }
        }

        if (url.contains("#") || url.contains("?")) {
            return true;
        }

        return false;
    }
}

