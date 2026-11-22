package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /history &lt;jugador&gt; y /banear &lt;jugador&gt;: los dos menus.
 *
 * /history existe aparte de /staffhistory porque uno se mira en una pantalla y
 * el otro se copia a un Discord. Lo de texto se queda; lo de mirar, aqui.
 *
 * /banear es lo mismo: /staffban sigue escribiendo a mano (para scripts), y
 * este es el camino de las manos.
 */
public final class StaffMenuExtraCommands extends BaseCommand {

    public StaffMenuExtraCommands(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        String sub = comando.getName().toLowerCase(Locale.ROOT);
        if (sub.equals("banear")) {
            banear(quien, args);
        } else {
            historial(quien, args);
        }
    }

    /** /banear &lt;jugador&gt;: abre el menu de baneo con ese jugador. */
    private void banear(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /banear <jugador>"));
            return;
        }
        if (!soloJugador(quien)) return;
        Player staff = (Player) quien;

        // El baneo se puede hacer a quien este dentro o no. Si esta dentro se
        // cogene su UUID real; si no, se trabaja con el nombre, que es lo que
        // guarda la lista de baneos de Bukkit.
        Player online = jugador(quien, args[0]);
        if (online == null) online = jugadorQueEmpiezaPor(quien, args[0]);

        plugin.menuBanear().abrir(staff, online);
        if (online == null) {
            // Sin conexion no hay Player, pero el ban por nombre sigue siendo
            // valido: se deja el nombre y el menu trabaja con el.
            staff.sendMessage(Text.aviso("Ese jugador no está conectado."));
            staff.sendMessage(Text.info("Se va a banear por nombre: "
                    + ChatColor.WHITE + args[0] + ChatColor.GRAY
                    + ". Si el nombre está mal, no se va a banear a nadie."));
        }
    }

    /** /history [jugador]: el historial en menu, o los conectados si no hay nombre. */
    private void historial(CommandSender quien, String[] args) {
        if (!soloJugador(quien)) return;
        Player staff = (Player) quien;

        if (args.length == 0) {
            staff.sendMessage(Text.error("Uso: /history <jugador>"));
            staff.sendMessage(Text.info("Para el historial de texto: /staffhistory"));
            return;
        }

        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        plugin.menuHistorial().abrir(staff, nombre);
    }
}