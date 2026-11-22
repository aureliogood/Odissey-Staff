package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Control global de chat compatible con los comandos clásicos de LiteBans. */
public final class ChatControlCommand extends BaseCommand {
    public ChatControlCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (comando.getName().equalsIgnoreCase("mutechat")) {
            boolean muted = plugin.config().getBoolean("chat-control.muted", false);
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("on")) muted = true;
                else if (args[0].equalsIgnoreCase("off")) muted = false;
                else {
                    quien.sendMessage(Text.error("Uso: /mutechat [on|off]"));
                    return;
                }
            } else {
                muted = !muted;
            }
            plugin.config().set("chat-control.muted", muted);
            plugin.saveConfig();
            String estado = muted ? "pausado" : "abierto";
            for (Player jugador : Bukkit.getOnlinePlayers()) {
                jugador.sendMessage(Text.prefijo() + ChatColor.YELLOW + "El chat público está " + estado + ".");
            }
            if (!(quien instanceof Player)) quien.sendMessage("Chat público " + estado + ".");
            return;
        }

        for (Player jugador : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) jugador.sendMessage("");
            jugador.sendMessage(Text.prefijo() + ChatColor.YELLOW + "Chat limpiado por "
                    + ChatColor.WHITE + quien.getName() + ChatColor.YELLOW + ".");
        }
        plugin.getLogger().info("[clearchat] " + quien.getName() + " limpio el chat.");
    }
}
