package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.StringJoiner;

/** /staffchat <mensaje>: habla solo con el staff. */
public final class StaffChatCommand extends BaseCommand {

    public StaffChatCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Escribe algo: /staffchat hola"));
            return;
        }
        StringJoiner texto = new StringJoiner(" ");
        for (String a : args) texto.add(a);
        String prefijo = colores(plugin.config().getString("staff-chat.prefijo", "&8[&bStaff&8] &7"));
        String color = colores(plugin.config().getString("staff-chat.color", "&b"));
        String mensaje = color + quien.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + texto.toString();

        int recibidos = 0;
        for (Player otro : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.esStaff(otro)) continue;
            otro.sendMessage(prefijo + mensaje);
            recibidos++;
        }
        // Se guarda en el historial: lo que se dice por aqui se pierde al cerrar
        // el Minecraft, y hay mensajes que luego hacen falta (/staffchatlog).
        plugin.chatLog().anotar(quien.getName(), texto.toString());
        plugin.getLogger().info("[staffchat] " + quien.getName() + ": " + texto);

        // Si el staff se equivoca de comando y lo escribe sin permisos, no se
        // avisa a los demas de nada.
        if (recibidos == 0) {
            plugin.getLogger().info("[staffchat sin staff online] " + quien.getName() + ": " + texto);
        }
    }
}