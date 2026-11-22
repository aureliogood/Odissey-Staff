package es.odissey.staff;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Notificaciones opcionales a Discord y Telegram, sin bloquear el hilo del servidor. */
public final class ModerationWebhook {
    private final OdisseyStaff plugin;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ModerationWebhook(OdisseyStaff plugin) {
        this.plugin = plugin;
        if (!plugin.config().isSet("integraciones.discord-ban.activo")) {
            plugin.config().set("integraciones.discord-ban.activo", true);
            plugin.config().set("integraciones.discord-ban.url-baneos", "https://odisseymc.site/baneos");
            plugin.config().set("integraciones.discord-ban.mensaje", List.of(
                    "🚫 **Nuevo baneo en %servidor%**",
                    "**Jugador:** %jugador%",
                    "**Staff:** %staff%",
                    "**Duración:** %duracion%",
                    "**Motivo:** %motivo%",
                    "%url%"
            ));
            plugin.saveConfig();
        }
    }

    public void sancion(String tipo, String jugador, String staff, String motivo, String duracion) {
        String mensaje = "[OdisseyStaff] " + tipo.toUpperCase(java.util.Locale.ROOT)
                + " · jugador=" + limpio(jugador) + " · staff=" + limpio(staff)
                + " · duración=" + limpio(duracion) + " · motivo=" + limpio(motivo);
        List<HttpRequest> peticiones = new ArrayList<>();
        String discord = plugin.config().getString("integraciones.discord-webhook", "").trim();
        boolean esBaneo = "ban".equalsIgnoreCase(tipo);
        boolean discordBanActivo = plugin.config().getBoolean("integraciones.discord-ban.activo", true);
        String mensajeDiscord = esBaneo ? mensajeBaneoDiscord(jugador, staff, motivo, duracion) : mensaje;
        if (plugin.config().getBoolean("integraciones.discord-activo", false)
                && (!esBaneo || discordBanActivo)
                && !discord.isEmpty() && esHttps(discord)) {
            String payload = "{\"content\":\"" + json(mensajeDiscord)
                    + "\",\"allowed_mentions\":{\"parse\":[]}}";
            peticiones.add(post(discord, payload));
        }
        String token = plugin.config().getString("integraciones.telegram.token", "").trim();
        String chat = plugin.config().getString("integraciones.telegram.chat-id", "").trim();
        if (plugin.config().getBoolean("integraciones.telegram.activo", false)
                && !token.isEmpty() && !chat.isEmpty() && token.matches("[0-9]+:[A-Za-z0-9_-]+")) {
            peticiones.add(post("https://api.telegram.org/bot" + token + "/sendMessage",
                    "{\"chat_id\":\"" + json(chat) + "\",\"text\":\"" + json(mensaje) + "\"}"));
        }
        for (HttpRequest request : peticiones) {
            cliente.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(respuesta -> {
                        if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300)
                            plugin.getLogger().warning("Una notificación externa fue rechazada (HTTP "
                                    + respuesta.statusCode() + "); se omitió la URL y cualquier token.");
                    })
                    .exceptionally(error -> {
                        plugin.getLogger().warning("No se pudo enviar una notificación externa de moderación.");
                        return null;
                    });
        }
    }

    private String mensajeBaneoDiscord(String jugador, String staff, String motivo, String duracion) {
        List<String> lineas = plugin.config().getStringList("integraciones.discord-ban.mensaje");
        if (lineas.isEmpty()) {
            lineas = List.of(
                    "🚫 **Nuevo baneo en %servidor%**",
                    "**Jugador:** %jugador%",
                    "**Staff:** %staff%",
                    "**Duración:** %duracion%",
                    "**Motivo:** %motivo%",
                    "%url%"
            );
        }
        String plantilla = String.join("\n", lineas);
        String servidor = plugin.config().getString("mensajes.servidor", "Odissey");
        String url = plugin.config().getString("integraciones.discord-ban.url-baneos", "https://odisseymc.site/baneos");
        String salida = plantilla
                .replace("%servidor%", limpio(servidor))
                .replace("%jugador%", limpio(jugador))
                .replace("%staff%", limpio(staff))
                .replace("%duracion%", limpio(duracion))
                .replace("%tiempo%", limpio(duracion))
                .replace("%motivo%", limpio(motivo))
                .replace("%url%", limpio(url));
        return salida.length() > 2000 ? salida.substring(0, 1997) + "..." : salida;
    }
    private static HttpRequest post(String url, String json) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    private static boolean esHttps(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        }
        catch (IllegalArgumentException ignorado) { return false; }
    }

    private static String limpio(String valor) {
        if (valor == null || valor.isBlank()) return "sin dato";
        return valor.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private static String json(String valor) {
        StringBuilder salida = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\b' -> salida.append("\\b");
                case '\f' -> salida.append("\\f");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> { if (c < 0x20) salida.append(String.format("\\u%04x", (int)c)); else salida.append(c); }
            }
        }
        return salida.toString();
    }
}
