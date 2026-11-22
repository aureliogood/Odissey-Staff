package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Comandos públicos /report y /ticket, y moderación de staff para las entradas. */
public final class ReportTicketCommand implements CommandExecutor {
    private final OdisseyStaff plugin;
    private final ConcurrentMap<UUID, Long> ultimoReporte = new ConcurrentHashMap<>();

    public ReportTicketCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String etiqueta, String[] args) {
        if (comando.getName().equalsIgnoreCase("report")) {
            reportar(quien, args);
            return true;
        }
        ticket(quien, args);
        return true;
    }

    private void reportar(CommandSender quien, String[] args) {
        if (!(quien instanceof Player jugador)) {
            quien.sendMessage(Text.error("Los reportes se envían dentro del juego."));
            return;
        }
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /report <jugador> <motivo>"));
            return;
        }
        long ahora = System.currentTimeMillis();
        long ultimo = ultimoReporte.getOrDefault(jugador.getUniqueId(), 0L);
        long enfriamiento = Math.max(0, plugin.leerTiempoEnMs(plugin.config().getString("reportes.enfriamiento", "5m")));
        if (ahora - ultimo < enfriamiento) {
            long quedan = (enfriamiento - (ahora - ultimo) + 59_999) / 60_000;
            jugador.sendMessage(Text.error("Espera " + quedan + " minuto(s) antes de enviar otro reporte."));
            return;
        }
        String texto = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (texto.length() < 4 || texto.length() > 300) {
            jugador.sendMessage(Text.error("El motivo debe tener entre 4 y 300 caracteres."));
            return;
        }
        String objetivo = args[0];
        if (objetivo.equalsIgnoreCase(jugador.getName())) {
            jugador.sendMessage(Text.error("No puedes reportarte a ti mismo."));
            return;
        }
        ReportStore.Entrada entrada = plugin.reportes().crear("reporte", jugador.getUniqueId(),
                jugador.getName(), objetivo, texto);
        ultimoReporte.put(jugador.getUniqueId(), ahora);
        jugador.sendMessage(Text.exito("Reporte #" + entrada.id + " enviado al staff."));
        avisarStaff(ChatColor.RED + jugador.getName() + ChatColor.YELLOW + " reportó a "
                + ChatColor.WHITE + objetivo + ChatColor.GRAY + ": " + texto
                + ChatColor.DARK_GRAY + " (#" + entrada.id + ")");
    }

    private void ticket(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /ticket <create <mensaje>|list|view <id>|close <id> >"));
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "create" -> crearTicket(quien, args);
            case "list" -> listarTickets(quien);
            case "view" -> verTicket(quien, args);
            case "close" -> cerrarTicket(quien, args);
            default -> quien.sendMessage(Text.error("Subcomando inválido. Usa /ticket create, list, view o close."));
        }
    }

    private void crearTicket(CommandSender quien, String[] args) {
        if (!(quien instanceof Player jugador)) {
            quien.sendMessage(Text.error("Los tickets se crean dentro del juego."));
            return;
        }
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /ticket create <mensaje>"));
            return;
        }
        if (plugin.reportes().tieneAbierto(jugador.getUniqueId())) {
            jugador.sendMessage(Text.error("Ya tienes una solicitud abierta. Usa /ticket view <id> con el staff."));
            return;
        }
        String texto = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (texto.length() < 4 || texto.length() > 500) {
            jugador.sendMessage(Text.error("El mensaje debe tener entre 4 y 500 caracteres."));
            return;
        }
        ReportStore.Entrada entrada = plugin.reportes().crear("ticket", jugador.getUniqueId(),
                jugador.getName(), "", texto);
        jugador.sendMessage(Text.exito("Solicitud #" + entrada.id + " enviada al staff."));
        avisarStaff(ChatColor.AQUA + "Nuevo ticket #" + entrada.id + ChatColor.GRAY + " de "
                + ChatColor.WHITE + jugador.getName() + ChatColor.GRAY + ": " + texto);
    }

    private void listarTickets(CommandSender quien) {
        if (!esStaff(quien)) return;
        List<ReportStore.Entrada> lista = plugin.reportes().recientes(true, 20);
        quien.sendMessage(Text.marca() + ChatColor.GOLD + "Reportes y tickets abiertos (" + lista.size() + ")");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info("No hay entradas abiertas."));
            return;
        }
        for (ReportStore.Entrada e : lista) {
            quien.sendMessage(ChatColor.GRAY + " #" + e.id + " " + tipo(e) + " · "
                    + ChatColor.WHITE + e.autor + ChatColor.GRAY
                    + (e.objetivo.isEmpty() ? "" : " → " + e.objetivo)
                    + " · " + e.texto);
        }
    }

    private void verTicket(CommandSender quien, String[] args) {
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /ticket view <id>"));
            return;
        }
        Long id = leerId(args[1]);
        if (id == null) { quien.sendMessage(Text.error("El id debe ser numérico.")); return; }
        ReportStore.Entrada e = plugin.reportes().obtener(id);
        if (e == null) { quien.sendMessage(Text.error("No existe la entrada #" + id + ".")); return; }
        if (!esStaff(quien) && (!(quien instanceof Player p) || e.autorUuid == null || !e.autorUuid.equals(p.getUniqueId()))) {
            quien.sendMessage(Text.error("Solo quien abrió la entrada y el staff pueden verla."));
            return;
        }
        mostrar(quien, e);
    }

    private void cerrarTicket(CommandSender quien, String[] args) {
        if (args.length < 2) { quien.sendMessage(Text.error("Uso: /ticket close <id>")); return; }
        Long id = leerId(args[1]);
        if (id == null) { quien.sendMessage(Text.error("El id debe ser numérico.")); return; }
        ReportStore.Entrada e = plugin.reportes().obtener(id);
        if (e == null) { quien.sendMessage(Text.error("No existe la entrada #" + id + ".")); return; }
        boolean staff = esStaff(quien);
        if (!staff && (!(quien instanceof Player p) || e.autorUuid == null || !e.autorUuid.equals(p.getUniqueId()))) {
            quien.sendMessage(Text.error("Solo quien abrió la entrada y el staff pueden cerrarla."));
            return;
        }
        if (!plugin.reportes().cerrar(id, quien.getName())) {
            quien.sendMessage(Text.aviso("La entrada #" + id + " ya estaba cerrada."));
            return;
        }
        quien.sendMessage(Text.exito("Entrada #" + id + " cerrada."));
        if (e.autorUuid != null) {
            Player autor = Bukkit.getPlayer(e.autorUuid);
            if (autor != null) autor.sendMessage(Text.info("Tu entrada #" + id + " fue cerrada por " + quien.getName() + "."));
        }
        if (staff) avisarStaff(ChatColor.GREEN + "Entrada #" + id + " cerrada por " + quien.getName() + ".");
    }

    private void mostrar(CommandSender quien, ReportStore.Entrada e) {
        quien.sendMessage(Text.marca() + ChatColor.GOLD + tipo(e) + " #" + e.id);
        quien.sendMessage(ChatColor.GRAY + "Autor: " + ChatColor.WHITE + e.autor
                + (e.objetivo.isEmpty() ? "" : ChatColor.GRAY + " · Objetivo: " + ChatColor.WHITE + e.objetivo));
        quien.sendMessage(ChatColor.GRAY + "Fecha: " + ChatColor.WHITE + Fechas.diaHora(e.creado)
                + ChatColor.GRAY + " · Estado: " + (e.cerrado ? ChatColor.GREEN + "cerrado por " + e.cerradoPor : ChatColor.YELLOW + "abierto"));
        quien.sendMessage(ChatColor.GRAY + "Mensaje: " + ChatColor.WHITE + e.texto);
    }

    private String tipo(ReportStore.Entrada e) { return e.tipo.equals("ticket") ? "Ticket" : "Reporte"; }
    private Long leerId(String texto) { try { return Long.parseLong(texto); } catch (NumberFormatException e) { return null; } }

    private boolean esStaff(CommandSender quien) {
        if (plugin.esStaff(quien) || quien.hasPermission("odissey.staff")) return true;
        quien.sendMessage(Text.error("Solo el staff puede gestionar reportes y tickets."));
        return false;
    }

    private void avisarStaff(String mensaje) {
        for (Player staff : Bukkit.getOnlinePlayers()) if (plugin.esStaff(staff)) staff.sendMessage(Text.prefijo() + mensaje);
        plugin.getLogger().info("[reportes] " + ChatColor.stripColor(mensaje));
    }
}
