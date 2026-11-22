package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /freeze <jugador>: deja al jugador quieto para poder hablar con el.
 *
 * Importante: congelado significa quieto, no protegido. Si se combinara con lo
 * contrario, un admin podria congelar a un jugador y matarlo sin que pueda
 * moverse, y eso es justo la clase de cosas que hace Código Rojo en videos.
 * Por eso aqui solo se bloquea el movimiento.
 */
public final class FreezeCommand extends BaseCommand {

    public FreezeCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /freeze <jugador>"));
            return;
        }
        Player objetivo = jugador(quien, args[0]);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, args[0]);
        if (objetivo == null) {
            quien.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
            return;
        }
        if (objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No puedes congelarte a ti mismo."));
            return;
        }

        boolean congeladoAhora = !plugin.gestor().congelado(objetivo);
        // Con sancion activa, /freeze no la levanta: el reloj volveria a correr
        // desde cero porque la sancion ya no esta en la lista.
        if (!congeladoAhora && plugin.sanciones().tiene(objetivo.getUniqueId())) {
            quien.sendMessage(Text.error(objetivo.getName() + " tiene una sanción activa."));
            quien.sendMessage(Text.info("Para levantarla: /staffsancion " + objetivo.getName() + " off"));
            return;
        }

        // El motivo es opcional: /freeze Rob o /freeze Rob exploit.
        String motivo = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        motivo = sb.toString();

        if (congeladoAhora) {
            plugin.gestor().congelar(objetivo, motivo, quien.getName());
            objetivo.sendMessage(Text.aviso("Has sido congelado por un miembro del staff. No te muevas."));
            objetivo.sendMessage(Text.info("Tu posición sigue siendo la misma y nadie te va a pegar."));
            if (!motivo.isEmpty()) {
                objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
            }
        } else {
            // descongelar() quita tambien la oscuridad y la lentitud: antes solo
            // devolvía el modo de juego, y el jugador se quedaba ciego y lento
            // aunque ya pudiera moverse.
            plugin.gestor().descongelar(objetivo);
            objetivo.sendMessage(Text.exito("Ya puedes moverte."));
        }
        quien.sendMessage(congeladoAhora
                ? Text.aviso(objetivo.getName() + " está congelado.")
                : Text.exito(objetivo.getName() + " ya puede moverse."));
    }
}