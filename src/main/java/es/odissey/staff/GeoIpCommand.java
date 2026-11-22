package es.odissey.staff;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Consultas de ubicación/riesgo IP con respuesta asíncrona y caché local. */
public final class GeoIpCommand implements CommandExecutor {
    private static final Pattern JSON_STRING = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern JSON_BOOL = Pattern.compile("\\\"%s\\\"\\s*:\\s*(true|false)");
    private final OdisseyStaff plugin;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Cache> cache = new ConcurrentHashMap<>();

    private record Cache(long guardado, String country, String region, String city, String isp, Boolean vpn) { }

    public GeoIpCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.esStaff(sender)) {
            sender.sendMessage(Text.error("No tienes permiso para consultar información de red."));
            return true;
        }
        if (!plugin.config().getBoolean("geoip.enabled", false)) {
            sender.sendMessage(Text.error("GeoIP está desactivado. Habilítalo en geoip.enabled."));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Text.error("Uso: /" + label + " <jugador|ip>"));
            return true;
        }
        boolean vpn = label.equalsIgnoreCase("vpncheck");
        String entrada = args[0];
        String ip = IpAddress.normalizar(entrada);
        if (ip == null) ip = plugin.conexiones().ultimaIp(entrada);
        ip = IpAddress.normalizar(ip);
        if (ip == null) {
            sender.sendMessage(Text.error("No encontré una IP válida para esa cuenta. El jugador debe haberse conectado antes."));
            return true;
        }
        final String ipConsulta = ip;
        Cache guardada = cache.get(ipConsulta);
        long vigencia = Math.max(1, plugin.config().getLong("geoip.cache-minutes", 360)) * 60_000L;
        if (guardada != null && System.currentTimeMillis() - guardada.guardado < vigencia) {
            mostrar(sender, entrada, ipConsulta, guardada, vpn);
            return true;
        }
        sender.sendMessage(Text.info("Consultando GeoIP para " + entrada + "…"));
        String url = "https://ipwho.is/" + URLEncoder.encode(ipConsulta, StandardCharsets.UTF_8)
                + "?fields=success,message,country,region,city,connection.isp,security.vpn";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        CommandSender destino = sender;
        cliente.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        responder(destino, Text.error("El proveedor GeoIP respondió HTTP " + response.statusCode() + "."));
                        return;
                    }
                    String body = response.body();
                    if (!Boolean.TRUE.equals(booleano(body, "success"))) {
                        responder(destino, Text.error("El proveedor no pudo consultar esa IP."));
                        return;
                    }
                    Boolean vpnDetectada = null;
                    String security = objeto(body, "security");
                    if (security != null) vpnDetectada = booleano(security, "vpn");
                    Cache resultado = new Cache(System.currentTimeMillis(), campo(body, "country"),
                            campo(body, "region"), campo(body, "city"), campo(body, "isp"), vpnDetectada);
                    cache.put(ipConsulta, resultado);
                    mostrar(destino, entrada, ipConsulta, resultado, vpn);
                })
                .exceptionally(error -> {
                    responder(destino, Text.error("No se pudo contactar al proveedor GeoIP."));
                    return null;
                });
        return true;
    }

    private void mostrar(CommandSender sender, String entrada, String ip, Cache data, boolean vpn) {
        if (vpn) {
            if (data.vpn == null) {
                responder(sender, Text.error("Este plan del proveedor no incluye detección VPN. GeoIP sí respondió para " + entrada + "."));
            } else {
                responder(sender, Text.info("VPN/proxy para " + entrada + " (" + ip + "): "
                        + (data.vpn ? "detectado" : "no detectado")));
            }
            return;
        }
        responder(sender, Text.marca() + "GeoIP " + entrada + " (" + ip + "): "
                + data.city + ", " + data.region + ", " + data.country + " · ISP: " + data.isp);
    }

    private void responder(CommandSender sender, String mensaje) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(mensaje));
    }

    private static String campo(String json, String clave) {
        Matcher matcher = Pattern.compile(String.format(JSON_STRING.pattern(), Pattern.quote(clave))).matcher(json);
        return matcher.find() ? desescapar(matcher.group(1)) : "desconocido";
    }

    private static Boolean booleano(String json, String clave) {
        Matcher matcher = Pattern.compile(String.format(JSON_BOOL.pattern(), Pattern.quote(clave))).matcher(json);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    private static String objeto(String json, String clave) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(clave) + "\\\"\\s*:\\s*\\{([^{}]*)}").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String desescapar(String texto) {
        return texto.replace("\\\"", "\"").replace("\\n", " ").replace("\\r", " ").replace("\\\\", "\\");
    }
}
