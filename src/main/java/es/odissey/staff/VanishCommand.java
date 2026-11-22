package es.odissey.staff;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /vanish [on|off]: desaparece de la vista de los jugadores. */
public final class VanishCommand extends BaseCommand {

    public VanishCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (!soloJugador(quien)) return;
        Player jugador = (Player) quien;

        boolean activar;
        if (args.length > 0 && args[0].equalsIgnoreCase("on")) {
            activar = true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            activar = false;
        } else {
            activar = !plugin.gestor().vanish(jugador);
        }

        plugin.gestor().cambiarVanish(jugador, activar);
        if (activar) {
            jugador.sendActionBar(colores("&6&lVANISH &7Activado"));
            jugador.sendMessage(Text.aviso("Ahora eres invisible. Usa /vanish off para volver."));
        } else {
            jugador.sendActionBar(colores("&6&lVANISH &7Desactivado"));
            jugador.sendMessage(Text.exito("Vanish desactivado. Ya te ven."));
        }
    }
}