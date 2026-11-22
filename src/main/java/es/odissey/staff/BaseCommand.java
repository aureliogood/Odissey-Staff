package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Base de los comandos: comprueba permisos y resuelve nombres de jugadores.
 *
 * Todo pasa por aqui porque un comando de staff que se ejecuta sin comprobar
 * el permiso es un panel abierto, y el tipado de Bukkit convierte cualquier
 * error raro en una excepcion que tumba el comando entero.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISO_BAN = "odissey.staff.ban";
    public static final String PERMISO_UNBAN = "odissey.staff.unban";
    public static final String PERMISO_MUTE = "odissey.staff.mute";
    public static final String PERMISO_INVENTARIO = "odissey.staff.inventory";
    public static final String PERMISO_DATOS = "odissey.staff.data";

    protected final OdisseyStaff plugin;
    private final String permiso;

    protected BaseCommand(OdisseyStaff plugin, String permiso) {
        this.plugin = plugin;
        this.permiso = permiso;
    }

    /** Se ejecuta con el permiso ya comprobado. */
    protected abstract void ejecutar(CommandSender quien, Command comando, String[] args);

    @Override
    public final boolean onCommand(CommandSender quien, Command comando, String etiqueta, String[] args) {
        String permisoRequerido = permisoRequerido(comando);
        if (!puede(quien, permisoRequerido)) {
            quien.sendMessage(Text.error("No tienes permiso para eso."
                    + (permisoRequerido == null || permisoRequerido.isEmpty() ? ""
                    : ChatColor.DARK_GRAY + " (" + permisoRequerido + ")")));
            return true;
        }
        try {
            ejecutar(quien, comando, args);
        } catch (Exception e) {
            quien.sendMessage(Text.error("Ese comando ha fallado: " + e.getMessage()));
            plugin.getLogger().warning("Fallo en /" + etiqueta + ": " + e);
        }
        return true;
    }

    private String permisoRequerido(Command comando) {
        if (!"odissey.staff".equals(permiso)) return permiso;
        return switch (comando.getName().toLowerCase(Locale.ROOT)) {
            case "staffban", "tempban", "ipban", "banear" -> PERMISO_BAN;
            case "unban" -> PERMISO_UNBAN;
            case "staffmute", "tempmute", "ipmute", "ghostmute", "staffunmute", "staffkick", "staffsancion", "staffjaula" -> PERMISO_MUTE;
            case "staffinvsee" -> PERMISO_INVENTARIO;
            case "staffespiar", "staffinfo", "staffhistory", "history", "staffsnapshot", "staffalt", "network",
                    "geoip", "vpncheck", "staffchatlog", "checkban", "checkmute", "warnings",
                    "banlist", "mutelist", "evidence" -> PERMISO_DATOS;
            default -> permiso;
        };
    }

    private boolean puede(CommandSender quien, String permisoRequerido) {
        if (permisoRequerido != null && permisoRequerido.startsWith("odissey.staff.")) {
            return quien.hasPermission(permisoRequerido);
        }
        if (plugin.esStaff(quien)) return true;
        return quien.hasPermission(permisoRequerido);
    }

    public static boolean tienePermisoAccion(CommandSender quien, String permiso) {
        return quien != null && quien.hasPermission(permiso);
    }

    protected final boolean soloJugador(CommandSender quien) {
        if (quien instanceof Player) return true;
        quien.sendMessage(Text.error("Eso solo se puede hacer dentro del juego."));
        return false;
    }

    /**
     * Un staff no puede aplicar acciones destructivas a otro staff.
     *
     * Antes el unico filtro era "no te lo hagas a ti mismo", asi que un admin
     * despistado podia banear a otro admin y a nadie le llegaba el aviso. Con
     * cuentas compartidas y cambios de turno, eso pasa.
     *
     * Para poder hacerlo hace falta odissey.admin, que por defecto es solo op.
     * Si el objetivo es admin y quien lo hace no lo es, tampoco: nadie sube en
     * la jerarquia por encima de un admin.
     */
    protected final boolean puedeTocar(CommandSender quien, Player objetivo, String accion) {
        if (objetivo == null) return true;
        if (objetivo.equals(quien)) return true;

        boolean soyAdmin = quien.hasPermission("odissey.admin");
        boolean elEsStaff = plugin.esStaff(objetivo);

        if (!elEsStaff) return true;

        if (!soyAdmin) {
            quien.sendMessage(Text.error(objetivo.getName() + " es parte del staff."));
            quien.sendMessage(Text.info("Necesitas el permiso odissey.admin para " + accion + " a otro staff."));
            return false;
        }
        if (objetivo.hasPermission("odissey.admin") && !quien.equals(objetivo)) {
            quien.sendMessage(Text.error(objetivo.getName() + " es admin. Necesitas ser admin superior para " + accion + "."));
            return false;
        }
        return true;
    }

    /** Aviso al resto del staff de que alguien ha tocado a un miembro del equipo. */
    protected final void avisarStaff(CommandSender quien, Player objetivo, String accion) {
        if (!plugin.esStaff(objetivo)) return;
        plugin.getLogger().info("[staff] " + quien.getName() + " aplico " + accion + " a " + objetivo.getName() + " (staff).");
        for (Player otro : plugin.getServer().getOnlinePlayers()) {
            if (otro.equals(quien) || !plugin.esStaff(otro)) continue;
            otro.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "STAFF  "
                    + ChatColor.GRAY + quien.getName() + ChatColor.WHITE + " aplico "
                    + accion + " a " + objetivo.getName() + ChatColor.GRAY + ", que es del equipo.");
        }
    }

    /**
     * El filtro de "no te tocan a ti ni a otro staff", en version estatica.
     *
     * El metodo de instancia protege a los comandos. Los menús no pasan por
     * BaseCommand, asi que necesitan poder llamar a la MISMA regla: si no,
     * seria que levantar la sancion de un admin se puede desde el menu pero no
     * desde el comando, que es un agujero con forma de menu.
     */
    public static boolean puedeTocarDesdeMenu(OdisseyStaff plugin,
                                              CommandSender quien, Player objetivo, String accion) {
        if (objetivo == null) return true;
        if (objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No te puedes hacer eso a ti mismo."));
            return false;
        }
        if (!plugin.esStaff(objetivo)) return true;
        if (!quien.hasPermission("odissey.admin")) {
            quien.sendMessage(Text.error(objetivo.getName() + " es parte del staff."));
            quien.sendMessage(Text.info("Necesitas " + ChatColor.WHITE + "odissey.admin"
                    + ChatColor.GRAY + " para " + accion + " a otro staff."));
            return false;
        }
        if (objetivo.hasPermission("odissey.admin")) {
            quien.sendMessage(Text.error(objetivo.getName() + " es admin. No puedes " + accion + "."));
            return false;
        }
        return true;
    }

    /** Jugador online por nombre, sinopsis y con "los que empiezan por". */
    protected final Player jugador(CommandSender quien, String nombre) {
        if (nombre == null || nombre.isEmpty()) return null;
        return plugin.getServer().getPlayerExact(nombre);
    }

    protected final Player jugadorQueEmpiezaPor(CommandSender quien, String texto) {
        if (texto == null || texto.isEmpty()) return null;
        String bajo = texto.toLowerCase(Locale.ROOT);
        List<Player> encontrados = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(bajo)) encontrados.add(p);
        }
        return encontrados.size() == 1 ? encontrados.get(0) : null;
    }

    protected static String colores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto == null ? "" : texto);
    }

    protected static boolean esNumero(String texto) {
        if (texto == null || texto.isEmpty()) return false;
        for (char c : texto.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    /**
     * Un entero, o el valor por defecto si viene vacio, no es numero o es tan
     * largo que no cabe. esNumero() acepta "99999999999999999999" y
     * Integer.parseInt() revienta con eso: como los comandos meten un
     * try/catch general, el fallo salia como "Ese comando ha fallado".
     */
    protected static int enteroSeguro(String texto, int porDefecto) {
        if (!esNumero(texto)) return porDefecto;
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    // ---------------- Autocompletado ----------------

        @Override
        public final List<String> onTabComplete(CommandSender quien, Command comando, String etiqueta, String[] args) {
            if (!puede(quien, permisoRequerido(comando))) return Collections.emptyList();
            if (args.length == 1) {
                // Si ya hay alguien escrito, se ofrecen "off" y las unidades de tiempo.
                if (etiqueta.equalsIgnoreCase("staffsancion")) {
                    return java.util.Arrays.asList("off", "30m", "1h", "1d");
                }
                List<String> opciones = new ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.equals(quien)) continue;
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        opciones.add(p.getName());
                    }
                }
                return opciones;
            }
            if (args.length == 2) {
                // Solo los comandos que de verdad aceptan un tiempo lo
                // sugieren. Antes se ofrecian en /staffwarn, que no tiene
                // duracion, y en ninguno de los otros, que si la tienen.
                List<String> conTiempo = java.util.Arrays.asList("staffban", "staffmute");
                if (conTiempo.contains(etiqueta.toLowerCase(Locale.ROOT))) {
                    return Arrays.asList("30m", "1h", "12h", "1d", "7d", "30d", "perm");
                }
                return Collections.emptyList();
            }
            if (args.length == 3 && Arrays.asList("staffban", "staffmute").contains(etiqueta.toLowerCase(Locale.ROOT))) {
                return Collections.emptyList();
            }
            return Collections.emptyList();
    }
}
