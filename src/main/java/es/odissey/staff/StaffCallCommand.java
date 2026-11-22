package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /staffcall: pide ayuda al resto del staff que este dentro.
 *
 * Para cuando estas golpeando a alguien y necesitas a otro que este atento por
 * si le hace falta. Envia la posicion y el mundo, que es lo que de verdad hace
 * falta para poder llegar.
 */
public final class StaffCallCommand extends BaseCommand {

    public StaffCallCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        // La posicion que se manda es la de QUIEN PIDE AYUDA, no la del que la
        // recibe: es a donde tienen que ir.
        String donde = "";
        if (quien instanceof Player) {
            org.bukkit.Location l = ((Player) quien).getLocation();
            donde = ChatColor.DARK_GRAY + " en " + ChatColor.WHITE + l.getWorld().getName()
                    + ChatColor.DARK_GRAY + " " + ChatColor.WHITE
                    + (int) l.getX() + ", " + (int) l.getY() + ", " + (int) l.getZ();
        } else {
            donde = ChatColor.DARK_GRAY + " (desde la consola)";
        }

        int avisados = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.esStaff(p) || p.equals(quien)) continue;
            avisados++;
            p.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "AVISO DE STAFF  "
                    + ChatColor.GRAY + quien.getName() + " necesita ayuda" + donde);
            try {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            } catch (Throwable t) {
                // El sonido puede cambiar entre versiones: si no existe, el
                // aviso de texto se ve igual.
            }
        }

        if (avisados == 0) {
            quien.sendMessage(Text.error("No hay ningún otro staff conectado a quien avisar."));
            return;
        }
        quien.sendMessage(Text.exito("Has avisado a " + avisados + " staff."));

        // Queda anotado: si luego hay que saber quien dio el aviso y cuando.
        plugin.getLogger().info("[staffcall] " + quien.getName() + " pide ayuda.");
    }
}
