package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /staffrestaurar: volver atras en los archivos del plugin.
 *
 * Sanciones, historial, notas, vigilancia, silencios, congelados... todo eso se
 * guarda en .yml, y antes de pisar cualquier archivo se copia el anterior a la
 * carpeta "copias". Con esto se recupera el que se pise sin querer.
 *
 * Es una red de seguridad, no un gestor de backups: no guarda en otro
 * servidor ni protege de un disco que se muere. Para eso, copia la carpeta del
 * plugin entera con la periodicidad que veas.
 */
public final class StaffRestaurarCommand extends BaseCommand {

    public StaffRestaurarCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.admin");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        if (args.length == 0) {
            listar(quien);
            return;
        }

        String copia = args[0];
        if (!copia.endsWith(".yml")) copia = copia + ".yml";

        List<String> disponibles = CopiaSeguridad.listar(plugin);
        if (!disponibles.contains(copia)) {
            quien.sendMessage(Text.error("No hay ninguna copia llamada '" + copia + "'."));
            quien.sendMessage(Text.info("Las copias que hay:"));
            for (String c : disponibles) {
                quien.sendMessage(ChatColor.DARK_GRAY + "  " + CopiaSeguridad.linea(plugin, c));
            }
            return;
        }

        String base = CopiaSeguridad.baseDe(copia);
        if (!CopiaSeguridad.restaurar(plugin, copia)) {
            quien.sendMessage(Text.error("No se pudo restaurar. Mira la consola."));
            return;
        }
        String nombre = base.replace(".yml", "");

        // Si los datos estan en base de datos, restaurar solo el .yml no cambia
        // nada: el plugin relee de la base y se queda igual. El archivo se
        // vuelca en la base y despues se recarga desde ahi.
        int filas = plugin.importarEnBase(nombre);
        if (filas < 0) {
            CopiaSeguridad.recargar(plugin, nombre);
            quien.sendMessage(Text.exito("Restaurado " + ChatColor.WHITE + base
                    + ChatColor.GREEN + " desde " + ChatColor.WHITE + copia));
            quien.sendMessage(Text.info("El plugin ya esta usando el archivo restaurado."));
        } else {
            CopiaSeguridad.recargar(plugin, nombre);
            quien.sendMessage(Text.exito("Restaurado " + ChatColor.WHITE + base
                    + ChatColor.GREEN + " desde " + ChatColor.WHITE + copia));
            quien.sendMessage(Text.info("El archivo se ha volcado en la base de datos ("
                    + filas + " registros) y el plugin ya esta usando esos datos."));
        }
        quien.sendMessage(Text.info("Lo que hubiera en el archivo de antes se guardo "
                + "como otra copia, por si te arrepientes."));
        avisarEquipo(quien.getName() + " restauro " + base + " desde " + copia);

        // Las sanciones vuelven a tener que aplicarse a quien las tenga activa.
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            SancionStore.Registro r = plugin.sanciones().obtener(p.getUniqueId());
            if (r != null) plugin.sanciones().alEntrar(p);
        }
    }

    private void listar(CommandSender quien) {
        List<String> copias = CopiaSeguridad.listar(plugin);
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Copias de seguridad ══");
        if (copias.isEmpty()) {
            quien.sendMessage(Text.info("No hay ninguna todavía."));
            quien.sendMessage(Text.info("Se hace una automáticamente antes de "
                    + "pisar cada archivo del plugin."));
            return;
        }
        for (String c : copias) {
            quien.sendMessage(CopiaSeguridad.linea(plugin, c));
        }
        quien.sendMessage(Text.info("Para volver atras: /staffrestaurar <nombre>"));
        quien.sendMessage(Text.info("Solo los admins pueden tocar esto."));
    }

    /** Avisa al equipo de que alguien toco los archivos del plugin. */
    private void avisarEquipo(String texto) {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!plugin.esStaff(p)) continue;
            p.sendMessage(Text.aviso("[staff] " + texto));
        }
        plugin.getLogger().info("[copias] " + texto);
    }
}
