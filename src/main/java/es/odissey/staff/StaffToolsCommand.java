package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /stafftools <color> [nombre]: el color y el nombre con el que te ve el resto
 * del staff cuando estas en modo staff.
 *
 * El nombre con "color" delante es el clasico del staff plugin: en modo staff
 * el nombre sale de otro color para que se reconozca de un vistazo. Se guarda
 * aqui y no en el lore del item porque el lore se ve igual en todas partes.
 */
public final class StaffToolsCommand extends BaseCommand {

    public StaffToolsCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (!soloJugador(quien)) return;
        Player jugador = (Player) quien;
        StaffManager.Estado e = plugin.gestor().estado(jugador);

        if (args.length == 0) {
            jugador.sendMessage(Text.info("Tu rango: " + (e.rango.isEmpty() ? "(ninguno)" : e.rango)));
            return;
        }
        if (args.length == 1) {
            e.rango = colores(args[0]);
            e.rangoLongitud = args[0].length();
            jugador.sendMessage(Text.exito("Rango de staff: " + e.rango));
            return;
        }
        StringBuilder nombre = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (nombre.length() > 0) nombre.append(' ');
            nombre.append(args[i]);
        }
        e.rango = colores(args[0]);
        e.rangoLongitud = args[0].length();
        jugador.sendMessage(Text.exito("Te veras como: " + e.rango + ChatColor.WHITE + nombre));
    }
}