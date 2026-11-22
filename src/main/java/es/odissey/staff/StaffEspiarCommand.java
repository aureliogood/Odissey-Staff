package es.odissey.staff;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /staffespiar: el atajo del boton "Espiar" del panel.
 *
 * Tres usos, segun lo que haya pasado:
 *
 *   /staffespiar            -> si ya estas espiando a alguien, te devuelve su
 *                              panel. Si no, abre el selector de objetivo.
 *   /staffespiar <jugador>  -> entra directo a ese jugador.
 *   /staffespiar off        -> suelta el espiado.
 *
 * Existe porque cerrar el panel NO es soltar el espiado (cerrar un cofre para
 * ir al inventario es normal) y sin este comando habia que abrir /staff, buscar
 * el boton y pulsarlo. Es el camino corto para volver.
 */
public final class StaffEspiarCommand extends BaseCommand {

    public StaffEspiarCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (!soloJugador(quien)) return;
        Player staff = (Player) quien;

        boolean fuera = args.length > 0 && (args[0].equalsIgnoreCase("off")
                || args[0].equalsIgnoreCase("salir"));
        if (fuera) {
            if (!plugin.espia().estaEspiando(staff.getUniqueId())) {
                staff.sendMessage(Text.info("No estás espiando a nadie."));
                return;
            }
            plugin.menuEspiar().salir(staff, false);
            return;
        }

        // Con nombre: directo a ese jugador.
        if (args.length > 0) {
            Player objetivo = jugador(quien, args[0]);
            if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, args[0]);
            if (objetivo == null) {
                staff.sendMessage(Text.error("Ese jugador no está conectado."));
                return;
            }
            plugin.menuEspiar().entrar(staff, objetivo);
            return;
        }

        // Sin nada: si ya espia, panel; si no, selector.
        if (plugin.espia().estaEspiando(staff.getUniqueId())) {
            plugin.menuEspiar().abrirPanel(staff);
            return;
        }
        staff.closeInventory();
        staff.sendMessage(Text.info("✎ Escribe el nombre de a quién quieres espiar."));
        plugin.espera().poner(staff.getUniqueId(), "espiar");
    }
}
