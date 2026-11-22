package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.List;

/** Adjunta enlaces de prueba y captura el inventario visible de un jugador conectado. */
public final class EvidenceCommand extends BaseCommand {
    public EvidenceCommand(OdisseyStaff plugin) { super(plugin, "odissey.staff"); }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /evidence <add|view|capture> <jugador> [url|nota]"));
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> agregar(quien, args);
            case "view" -> ver(quien, args);
            case "capture" -> capturar(quien, args);
            default -> quien.sendMessage(Text.error("Usa /evidence add, view o capture."));
        }
    }

    private void agregar(CommandSender quien, String[] args) {
        if (args.length < 3) { quien.sendMessage(Text.error("Uso: /evidence add <jugador> <url> [nota]")); return; }
        String url = args[2];
        if (url.length() > 2048 || !urlValida(url)) {
            quien.sendMessage(Text.error("La prueba debe ser un enlace http o https válido."));
            return;
        }
        String nota = args.length > 3 ? " · " + String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
        String tipo = clasificar(url);
        EvidenceStore.Evidencia e = plugin.evidencias().agregar(args[1], tipo, url + nota, quien.getName());
        quien.sendMessage(Text.exito("Evidencia #" + e.id + " añadida a " + args[1] + "."));
        avisarEquipo("Evidencia #" + e.id + " añadida por " + quien.getName() + " a " + args[1] + ".");
    }

    private void ver(CommandSender quien, String[] args) {
        if (args.length < 2) { quien.sendMessage(Text.error("Uso: /evidence view <jugador>")); return; }
        List<EvidenceStore.Evidencia> lista = plugin.evidencias().de(args[1]);
        quien.sendMessage(Text.marca() + ChatColor.GOLD + "Evidencias de " + args[1] + " (" + lista.size() + ")");
        if (lista.isEmpty()) { quien.sendMessage(Text.info("No hay pruebas adjuntas.")); return; }
        int limite = Math.min(20, lista.size());
        for (int i = 0; i < limite; i++) {
            EvidenceStore.Evidencia e = lista.get(i);
            quien.sendMessage(ChatColor.GRAY + " #" + e.id + " " + ChatColor.YELLOW + e.tipo
                    + ChatColor.GRAY + " · " + Fechas.diaHora(e.cuando) + " por " + e.autor
                    + ChatColor.WHITE + " · " + e.valor);
        }
        if (lista.size() > limite) quien.sendMessage(Text.info("Y " + (lista.size() - limite) + " más."));
    }

    private void capturar(CommandSender quien, String[] args) {
        if (args.length < 2) { quien.sendMessage(Text.error("Uso: /evidence capture <jugador>")); return; }
        Player objetivo = plugin.getServer().getPlayerExact(args[1]);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, args[1]);
        if (objetivo == null) { quien.sendMessage(Text.error("La captura de inventario requiere que esté conectado.")); return; }
        EvidenceStore.Evidencia e = plugin.evidencias().capturarInventario(objetivo, quien.getName());
        quien.sendMessage(Text.exito("Captura de inventario guardada como evidencia #" + e.id + "."));
    }

    private boolean urlValida(String texto) {
        try {
            URI uri = new URI(texto);
            String esquema = uri.getScheme();
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema));
        } catch (Exception e) { return false; }
    }

    private String clasificar(String url) {
        String u = url.toLowerCase(java.util.Locale.ROOT);
        if (u.contains("replay")) return "replay";
        if (u.matches(".*\\.(png|jpe?g|webp)(\\?.*)?$")) return "captura";
        if (u.contains("youtu") || u.endsWith(".mp4") || u.endsWith(".webm")) return "video";
        return "enlace";
    }

    private void avisarEquipo(String mensaje) {
        for (Player staff : plugin.getServer().getOnlinePlayers()) if (plugin.esStaff(staff)) staff.sendMessage(Text.prefijo() + ChatColor.GRAY + mensaje);
    }
}
