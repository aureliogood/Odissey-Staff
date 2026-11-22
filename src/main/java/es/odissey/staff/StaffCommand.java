package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /staff: abre el panel de staff. Con 'reload', recarga el config.yml.
 */
public final class StaffCommand extends BaseCommand {

    public StaffCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("recargar"))) {
            if (!quien.hasPermission("odissey.admin")) {
                quien.sendMessage(Text.sinPermiso("odissey.admin"));
                return;
            }
            plugin.recargar();
            quien.sendMessage(Text.exito("Configuración de OdisseyStaff recargada correctamente."));
            return;
        }

        if (!soloJugador(quien)) return;
        Player staff = (Player) quien;

        // Si estaba escribiendo algo en el chat para un menu, se cancela.
        if (plugin.espera().de(staff.getUniqueId()) != null) {
            plugin.espera().quitar(staff.getUniqueId());
            staff.sendMessage(Text.aviso("Vale, paso de lo que estabas escribiendo."));
            return;
        }
        plugin.menuStaff().abrir(staff);
    }
}
