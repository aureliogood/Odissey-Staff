package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * /staffinfo <jugador> -> todo lo que hace falta de esa persona en un mensaje.
 *
 * Antes no habia forma de mirar nada de un jugador desde el juego: el staff
 * tenia que acordarse de si le habían baneado antes, buscar en el chat, y
 * preguntar a otro admin. Con esto, una sola vez y tienes elhistorical delante.
 *
 * Lo que enseña: cuenta, si es staff, cuando se registro, cuando fue visto por
 * ultima vez, su IP de esta sesion, su dispositivo, su silencio activo con su
 * motivo, y las ultimas sanciones del historial.
 */
public final class StaffInfoCommand extends BaseCommand {


    public StaffInfoCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffinfo <jugador>"));
            return;
        }
        String nombre = args[0];
        Player jugador = jugador(quien, nombre);
        if (jugador == null) jugador = jugadorQueEmpiezaPor(quien, nombre);
        boolean conectado = jugador != null;
        String real = conectado ? jugador.getName() : nombre;

        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(nombre);

        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "" + ChatColor.BOLD
                + "══ " + ChatColor.WHITE + real + ChatColor.GOLD + "" + ChatColor.BOLD + " ══");
        quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Estado: "
                + (conectado ? ChatColor.GREEN + "conectado" : ChatColor.GRAY + "sin conectar"));

        // Lo de la lista de vigilancia va lo primero de todo: es lo que hay que
        // saber antes de hablar con alguien.
        VigilanciaStore.Marca marca = plugin.vigilancia().marca(real);
        if (marca != null) {
            quien.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "ESTA EN LA LISTA DE VIGILANCIA"
                    + ChatColor.GRAY + " · " + ChatColor.YELLOW + (marca.motivo.isEmpty() ? "sin motivo" : marca.motivo));
        }

        if (conectado) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Es staff: "
                    + (plugin.esStaff(jugador) ? ChatColor.GREEN + "si" : ChatColor.RED + "no"));
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Primera vez: "
                    + (jugador.getFirstPlayed() > 0 ? Fechas.diaHora(jugador.getFirstPlayed()) : "desconocida"));
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Visto ahora: "
                    + Fechas.diaHora(jugador.getLastSeen()));
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "IP: " + ChatColor.YELLOW
                    + (plugin.ipDe(jugador).isEmpty() ? "no disponible" : plugin.ipDe(jugador)));
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Vida: "
                    + String.format("%.1f", jugador.getHealth()) + " / "
                    + String.format("%.0f", jugador.getMaxHealth()));
        } else {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Ultima vez: "
                    + (offline.getLastPlayed() > 0 ? Fechas.diaHora(offline.getLastPlayed()) : "nunca"));
        }

        MuteStore.Registro m = plugin.silencios().registro(offline.getUniqueId());
        // La condicion iba al reves: antes solo se mostraba el silencio cuando
        // el jugador NO estaba silenciado, es decir, nunca se veia el silencio
        // activo que era justo lo que /staffinfo tiene que enseñar.
        if (m != null && plugin.silencios().silenciado(offline.getUniqueId())) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Silencio: "
                    + ChatColor.AQUA + (m.motivo.isEmpty() ? "sin motivo" : m.motivo)
                    + ChatColor.GRAY + " hasta " + (m.hasta == Long.MAX_VALUE
                        ? "que se levante" : Fechas.diaHora(m.hasta))
                    + " por " + m.nombre);
        }

        // El congelado solo se puede mirar si esta conectado: el estado del
        // plugin vive en memoria y solo existe para los que hay dentro.
        StaffManager.Estado congelado = conectado ? plugin.gestor().estado(jugador) : null;
        if (congelado != null && congelado.congelado) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Congelado: "
                    + ChatColor.RED + "si" + ChatColor.GRAY + " desde " + Fechas.diaHora(congelado.congeladoDesde)
                    + (congelado.congeladoMotivo.isEmpty() ? "" : " · " + congelado.congeladoMotivo));
        }

        // Las notas internas van antes que el historial: una nota explica un
        // patron y un aviso repetido es justo el patron.
        List<NotaStore.Nota> notas = plugin.notas().de(real);
        if (!notas.isEmpty()) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Notas internas "
                    + ChatColor.DARK_GRAY + "(" + notas.size() + ")"
                    + (plugin.notas().avisar(real) ? ChatColor.RED + " · " + notas.size() + " ya, piénsatelo" : ""));
            int desde = Math.max(0, notas.size() - 3);
            for (int i = desde; i < notas.size(); i++) {
                quien.sendMessage(ChatColor.DARK_GRAY + "     " + ChatColor.YELLOW + notas.get(i).texto
                        + ChatColor.DARK_GRAY + " (" + notas.get(i).autor + ")");
            }
        }

        List<SanctionStore.Entrada> hist = plugin.historial().de(real);        if (hist.isEmpty()) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Sanciones: "
                    + ChatColor.DARK_GRAY + "ninguna");
        } else {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Ultimas sanciones "
                    + ChatColor.DARK_GRAY + "(" + hist.size() + " en total, /staffhistory para todas)");
            int desde = Math.max(0, hist.size() - 4);
            for (int i = desde; i < hist.size(); i++) {
                SanctionStore.Entrada e = hist.get(i);
                quien.sendMessage(ChatColor.DARK_GRAY + "     " + colorDe(e.tipo) + e.tipo
                        + ChatColor.GRAY + " " + Fechas.diaHora(e.cuando)
                        + (e.motivo.isEmpty() ? "" : ChatColor.YELLOW + " · " + e.motivo)
                        + ChatColor.DARK_GRAY + " por " + e.staff);
            }
        }
    }

    private static ChatColor colorDe(String tipo) {
        switch (tipo) {
            case "aviso": return ChatColor.YELLOW;
            case "silencio": return ChatColor.AQUA;
            case "baneo": return ChatColor.RED;
            case "expulsion": return ChatColor.LIGHT_PURPLE;
            default: return ChatColor.WHITE;
        }
    }
}
