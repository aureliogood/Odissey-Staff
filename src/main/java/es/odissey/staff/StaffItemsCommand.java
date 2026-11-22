package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /staffitems: las cinco herramientas de la barra rapida, y ya.
 *
 * Antes esto era /staff. Ahora /staff abre el panel entero y este comando se
 * queda con lo que hacia el otro, porque hay noches que lo unico que quieres es
 * la perla y el hielo sin pasar por tres menus.
 *
 * Es un interruptor: si ya llevas alguna, quita todas; si no, te las da.
 */
public final class StaffItemsCommand extends BaseCommand {

    public StaffItemsCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (!soloJugador(quien)) return;
        Player jugador = (Player) quien;
        StaffItems items = plugin.items();

        if (items.llevaHerramientas(jugador)) {
            items.quitar(jugador);
            jugador.sendMessage(Text.aviso("Has quitado tus herramientas de staff."));
            return;
        }

        items.repartir(jugador);
        jugador.sendMessage(Text.exito("Aquí tienes tus herramientas de staff:"));
        jugador.sendMessage(ChatColor.GOLD + " 1" + ChatColor.GRAY + " Perla"
                + ChatColor.DARK_GRAY + " - ir a un jugador (no se lanza)");
        jugador.sendMessage(ChatColor.GOLD + " 3" + ChatColor.GRAY + " Cofre"
                + ChatColor.DARK_GRAY + " - ver el inventario de quien le des click");
        jugador.sendMessage(ChatColor.GOLD + " 5" + ChatColor.GRAY + " Bloque de hielo"
                + ChatColor.DARK_GRAY + " - congelar, y otra vez para soltarlo");
        jugador.sendMessage(ChatColor.GOLD + " 7" + ChatColor.GRAY + " Poción"
                + ChatColor.DARK_GRAY + " - ponerte o quitarte el vanish");
        jugador.sendMessage(ChatColor.GOLD + " 9" + ChatColor.GRAY + " Vara de blaze"
                + ChatColor.DARK_GRAY + " - mandarle a su cama o al spawn");
        jugador.sendMessage(ChatColor.DARK_GRAY + "No se pueden mover de su sitio. "
                + "Vuelve a poner /staffitems para recogerlas.");

        try {
            jugador.playSound(jugador.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        } catch (Throwable t) { /* el sonido puede cambiar entre versiones */ }
    }
}
