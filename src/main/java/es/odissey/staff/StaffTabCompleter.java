package es.odissey.staff;

import org.bukkit.command.TabCompleter;

/**
 * Autocompletado compartido. Todos los comandos de staff heredan de
 * BaseCommand, que ya trae el suyo (nombres de jugadores online). Esta clase
 * solo existe para que el comando de plugin.yml pueda apuntar ahi sin tener que
 * repetir la referencia al permiso en cada sitio.
 */
public final class StaffTabCompleter implements TabCompleter {

    private final BaseCommand comando;
    private final TabCompleter delegado;

    public StaffTabCompleter(OdisseyStaff plugin, Object ejecutor) {
        if (ejecutor instanceof BaseCommand) {
            this.comando = (BaseCommand) ejecutor;
            this.delegado = null;
        } else if (ejecutor instanceof TabCompleter) {
            this.comando = null;
            this.delegado = (TabCompleter) ejecutor;
        } else {
            this.comando = null;
            this.delegado = null;
        }
    }

    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender quien,
                                                 org.bukkit.command.Command cmd,
                                                 String etiqueta, String[] args) {
        if (comando != null) return comando.onTabComplete(quien, cmd, etiqueta, args);
        if (delegado != null) return delegado.onTabComplete(quien, cmd, etiqueta, args);
        return null;
    }
}
