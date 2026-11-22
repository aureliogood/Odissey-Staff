package es.odissey.staff;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Comandos administrativos de estado inspirados en /lbr. */
public final class LbrCommand implements CommandExecutor {
    private final OdisseyStaff plugin;

    public LbrCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("odissey.admin")) {
            sender.sendMessage(Text.error("No tienes permiso para administrar OdisseyStaff."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(Text.marca() + "OdisseyStaff v" + plugin.getDescription().getVersion());
            sender.sendMessage(Text.info("Almacenamiento: " + plugin.config().getString("database.type", "yaml")));
            sender.sendMessage(Text.info("Comandos: /lbr info | stats | reload"));
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "stats" -> {
                sender.sendMessage(Text.marca() + "Estado de moderación");
                sender.sendMessage(Text.info("Sanciones activas: " + plugin.sanciones().todas().size()));
                sender.sendMessage(Text.info("Silencios registrados: " + plugin.silencios().todos().size()));
                sender.sendMessage(Text.info("Reportes abiertos: " + plugin.reportes().recientes(true, 100000).size()));
            }
            case "reload" -> {
                plugin.recargar();
                sender.sendMessage(Text.exito("Configuración de OdisseyStaff recargada."));
            }
            default -> sender.sendMessage(Text.error("Uso: /lbr <info|stats|reload>"));
        }
        return true;
    }
}
