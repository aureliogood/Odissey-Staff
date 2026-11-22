package es.odissey.staff;

import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;

/** Bans y mutes por dirección IP, persistentes en Paper y en el almacén local. */
public final class IpSanctionCommand extends BaseCommand {
    public IpSanctionCommand(OdisseyStaff plugin) { super(plugin, "odissey.staff"); }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        boolean ban = comando.getName().equalsIgnoreCase("ipban");
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /" + (ban ? "ipban" : "ipmute")
                    + " <jugador|ip> [tiempo|perm] <motivo>"));
            return;
        }
        String objetivoNombre = args[0];
        Player objetivo = plugin.getServer().getPlayerExact(objetivoNombre);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, objetivoNombre);
        if (objetivo != null && objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No puedes aplicarte una sanción por IP a ti mismo."));
            return;
        }
        if (!puedeTocar(quien, objetivo, ban ? "banear por IP" : "silenciar por IP")) return;

        String ip = IpAddress.normalizar(objetivoNombre);
        if (ip == null && objetivo != null && objetivo.getAddress() != null) {
            ip = objetivo.getAddress().getAddress().getHostAddress();
        }
        if (ip == null) ip = IpAddress.normalizar(plugin.conexiones().ultimaIp(objetivoNombre));
        if (ip == null) {
            quien.sendMessage(Text.error("No se encontró una IP válida. El jugador debe haberse conectado antes."));
            return;
        }

        int desde = 1;
        boolean permanente = false;
        long duracion = ban ? 7L * 86_400_000L : Long.MAX_VALUE;
        if (args[1].equalsIgnoreCase("perm") || args[1].equalsIgnoreCase("permanente")) {
            permanente = true;
            duracion = Long.MAX_VALUE;
            desde = 2;
        } else {
            long ms = plugin.leerTiempoEnMs(args[1]);
            if (ms > 0) {
                duracion = ms;
                desde = 2;
            }
        }
        if (ban && permanente && !"PERM".equalsIgnoreCase(args[args.length - 1])) {
            String motivo = unir(args, desde, args.length);
            quien.sendMessage(Text.error("Ban permanente por IP: vuelve a confirmar añadiendo PERM al final."));
            quien.sendMessage(Text.aviso("/ipban " + objetivoNombre + " perm " + motivo + " PERM"));
            return;
        }
        int fin = ban && permanente ? args.length - 1 : args.length;
        String motivo = unir(args, desde, fin);
        if (motivo.isBlank()) { quien.sendMessage(Text.error("Falta el motivo.")); return; }
        if (ban && !ModerationLimits.puedeBanear(plugin, quien, permanente)) return;

        if (ban) aplicarBan(quien, objetivo, objetivoNombre, ip, motivo, duracion, permanente);
        else aplicarMute(quien, objetivo, objetivoNombre, ip, motivo, duracion);
    }

    private void aplicarBan(CommandSender quien, Player objetivo, String nombre, String ip,
                            String motivo, long duracion, boolean permanente) {
        Date vence = permanente ? null : new Date(System.currentTimeMillis() + duracion);
        String razon = motivo + " · por " + quien.getName();
        try {
            plugin.getServer().getBanList(BanList.Type.IP).addBan(ip, razon, vence, quien.getName());
        } catch (Exception e) {
            quien.sendMessage(Text.error("No se pudo aplicar el ban de IP: " + e.getMessage()));
            return;
        }
        if (objetivo != null && plugin.config().getBoolean("evidencias.capturar-al-banear", true)) {
            plugin.evidencias().capturarInventario(objetivo, quien.getName());
        }
        if (objetivo != null) objetivo.kickPlayer(ChatColor.RED + "Has sido expulsado: " + motivo);
        String etiqueta = IpAddress.esIp(nombre) ? ip : nombre + " (" + ip + ")";
        quien.sendMessage(Text.exito("IP baneada: " + etiqueta + (permanente ? " permanentemente." : " por " + SancionStore.enLetras(duracion) + ".")));
        plugin.historial().anotar("ipban", ip, "", quien.getName(), motivo,
                permanente ? "permanente" : "hasta " + Fechas.diaHora(vence.getTime()));
        plugin.historial().guardar();
        plugin.avisarEquipo(ChatColor.RED + etiqueta + ChatColor.YELLOW + " recibió un ban por IP: " + motivo);
        plugin.anunciarSancion("ban", nombre, quien.getName(), motivo,
                permanente ? "permanente · IP" : "temporal · IP");
    }

    private void aplicarMute(CommandSender quien, Player objetivo, String nombre, String ip,
                             String motivo, long duracion) {
        long hasta = duracion == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + duracion;
        plugin.silencios().ponerIp(ip, motivo + " · por " + quien.getName(), hasta, false);
        quien.sendMessage(Text.exito("IP silenciada: " + nombre + " (" + ip + ")."));
        plugin.historial().anotar("ipmute", ip,
                "", quien.getName(), motivo, hasta == Long.MAX_VALUE ? "permanente" : "hasta " + Fechas.diaHora(hasta));
        plugin.historial().guardar();
        plugin.avisarEquipo(ChatColor.AQUA + nombre + ChatColor.YELLOW + " recibió un silencio por IP: " + motivo);
        if (objetivo != null) objetivo.sendMessage(Text.aviso("Has sido silenciado por un miembro del staff."));
    }

    private static String unir(String[] args, int desde, int hasta) {
        if (desde >= hasta) return "";
        return String.join(" ", java.util.Arrays.copyOfRange(args, desde, hasta)).trim();
    }
}
