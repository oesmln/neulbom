package com.neulbom.backend.analysis.integration;

import java.net.URI;

final class ProviderUrls {

    private ProviderUrls() {
    }

    static URI resolve(String baseOrUrl, String path) {
        String base = baseOrUrl == null ? "" : baseOrUrl.trim();
        if (base.isBlank()) {
            return URI.create(path);
        }
        if (path == null || path.isBlank()) {
            return URI.create(base);
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + path);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return URI.create(base + "/" + path);
        }
        return URI.create(base + path);
    }
}
