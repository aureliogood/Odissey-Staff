package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Solicita una recomendación de moderación; nunca aplica sanciones automáticamente. */
public final class AiModerationCommand implements CommandExecutor {
    private static final Pattern TEXTO = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private final OdisseyStaff plugin;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AiModerationCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("odissey.ai")) {
            sender.sendMessage(Text.error("Necesitas odissey.ai para usar el análisis de chat."));
            return true;
        }
        if (!plugin.config().getBoolean("ai.enabled", false)) {
            sender.sendMessage(Text.error("El análisis de IA está desactivado. Configura ai.enabled y la clave de API."));
            return true;
        }
        if (args.length < 2 || !(args[0].equalsIgnoreCase("analyze") || args[0].equalsIgnoreCase("suggest"))) {
            sender.sendMessage(Text.error("Uso: /ai <analyze|suggest> <jugador>"));
            return true;
        }
        String jugador = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        List<ChatJugadoresLog.Linea> lineas = plugin.chatJugadores().de(jugador,
                Math.max(1, plugin.config().getInt("ai.window-minutes", 60)), "",
                Math.max(1, Math.min(40, plugin.config().getInt("ai.max-messages", 20))));
        if (lineas.isEmpty()) {
            sender.sendMessage(Text.info("No hay mensajes guardados recientes para " + jugador + "."));
            return true;
        }
        java.util.Collections.reverse(lineas);
        StringBuilder contexto = new StringBuilder();
        for (ChatJugadoresLog.Linea linea : lineas) {
            if (contexto.length() > 0) contexto.append('\n');
            contexto.append(linea.texto);
        }
        String key = plugin.config().getString("ai.api-key", "").trim();
        String model = plugin.config().getString("ai.model", "gpt-6-luna").trim();
        if (key.isEmpty() || !model.matches("[A-Za-z0-9._-]{1,80}")) {
            sender.sendMessage(Text.error("Completa ai.api-key y ai.model en config.yml."));
            return true;
        }
        String instruction = "Eres asistente de moderación para un servidor de Minecraft. "
                + "Analiza los mensajes citados como evidencia, sin asumir hechos fuera de ellos. "
                + "Resume el posible problema, cita fragmentos breves y recomienda revisar, advertir, silenciar o no actuar. "
                + "Expresa incertidumbre y no decidas sanciones. Responde en español, máximo 120 palabras.";
        String body = "{\"model\":\"" + json(model) + "\",\"instructions\":\"" + json(instruction)
                + "\",\"input\":\"Analiza los mensajes recientes de " + json(jugador) + ":\\n" + json(contexto.toString())
                + "\",\"max_output_tokens\":300}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        CommandSender destino = sender;
        sender.sendMessage(Text.info("Enviando " + lineas.size() + " mensajes recientes a OpenAI para análisis…"));
        cliente.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        responder(destino, Text.error("La API de OpenAI respondió HTTP " + response.statusCode() + ". Revisa clave, modelo y cuota."));
                        return;
                    }
                    Matcher matcher = TEXTO.matcher(response.body());
                    if (!matcher.find()) {
                        responder(destino, Text.error("La respuesta de OpenAI no incluyó texto de análisis."));
                        return;
                    }
                    String salida = desescapar(matcher.group(1));
                    responder(destino, Text.marca() + "Recomendación IA para " + jugador + " (revísala; no aplica sanciones):");
                    for (String parte : cortar(salida, 240)) responder(destino, "§7" + parte);
                })
                .exceptionally(error -> {
                    responder(destino, Text.error("No se pudo contactar con la API de OpenAI."));
                    return null;
                });
        return true;
    }

    private void responder(CommandSender sender, String texto) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(texto));
    }

    private static List<String> cortar(String texto, int max) {
        java.util.ArrayList<String> partes = new java.util.ArrayList<>();
        String restante = texto.replace('\r', ' ').trim();
        while (restante.length() > max) {
            int corte = restante.lastIndexOf(' ', max);
            if (corte < 1) corte = max;
            partes.add(restante.substring(0, corte));
            restante = restante.substring(corte).trim();
        }
        if (!restante.isEmpty()) partes.add(restante);
        return partes;
    }

    private static String desescapar(String valor) {
        return valor.replace("\\n", " ").replace("\\r", " ").replace("\\t", " ")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String json(String valor) {
        StringBuilder out = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
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
}
