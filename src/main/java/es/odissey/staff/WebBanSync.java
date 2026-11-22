package es.odissey.staff;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Publica y revoca sanciones de jugador en la tabla staff_bans de Supabase. */
public final class WebBanSync {
    private final OdisseyStaff plugin;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WebBanSync(OdisseyStaff plugin) { this.plugin = plugin; }

    public void banear(long idHistorial, String nombre, String uuid, String motivo,
                       String staff, long creado, Long vence) {
        Config c = config();
        if (c == null) return;
        String id = c.serverId + ":" + idHistorial;
        String body = "{\"id\":\"" + json(id) + "\",\"server_id\":\"" + json(c.serverId)
                + "\",\"player_name\":\"" + json(nombre) + "\",\"player_uuid\":"
                + (uuid == null || uuid.isBlank() ? "null" : "\"" + json(uuid) + "\"")
                + ",\"reason\":\"" + json(motivo) + "\",\"staff_name\":\"" + json(staff)
                + "\",\"created_at\":\"" + Instant.ofEpochMilli(creado)
                + "\",\"expires_at\":" + (vence == null ? "null" : "\"" + Instant.ofEpochMilli(vence) + "\"")
                + ",\"revoked_at\":null,\"revoked_by\":null}";
        enviar("POST", c, "?on_conflict=id", body, "resolution=merge-duplicates,return=minimal");
    }

    public void revocar(long idHistorial, String staff) {
        Config c = config();
        if (c == null || idHistorial <= 0) return;
        String id = c.serverId + ":" + idHistorial;
        String query = "?id=eq." + URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
        String body = "{\"revoked_at\":\"" + Instant.now() + "\",\"revoked_by\":\"" + json(staff) + "\"}";
        enviar("PATCH", c, query, body, "return=minimal");
    }

    private Config config() {
        if (!plugin.config().getBoolean("web-ban-sync.enabled", false)) return null;
        String url = plugin.config().getString("web-ban-sync.supabase-url", "").trim().replaceAll("/+$", "");
        String key = plugin.config().getString("web-ban-sync.service-key", "").trim();
        String server = plugin.config().getString("web-ban-sync.server-id", "odissey-main").trim();
        if (url.isEmpty() || key.isEmpty() || server.isEmpty()) {
            plugin.getLogger().warning("[web-ban-sync] sincronización activada pero faltan URL, service-key o server-id.");
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
        } catch (IllegalArgumentException ex) { return null; }
        return new Config(url, key, server);
    }

    private void enviar(String metodo, Config c, String query, String body, String prefer) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(c.url + "/rest/v1/staff_bans" + query))
                .timeout(Duration.ofSeconds(12))
                .header("apikey", c.key)
                .header("Authorization", "Bearer " + c.key)
                .header("Content-Type", "application/json")
                .header("Prefer", prefer)
                .method(metodo, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        cliente.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300)
                        plugin.getLogger().warning("[web-ban-sync] Supabase respondió HTTP " + response.statusCode()
                                + "; revisa que ejecutaste supabase/08-baneos.sql y configuraste service-key.");
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("[web-ban-sync] no se pudo conectar con Supabase.");
                    return null;
                });
    }

    private static String json(String texto) {
        if (texto == null) return "";
        StringBuilder out = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.toString();
    }

    private record Config(String url, String key, String serverId) { }
}
