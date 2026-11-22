package es.odissey.staff;

import org.bukkit.ChatColor;

/**
 * Todo el texto del plugin pasa por aqui.
 *
 * Antes cada clase montaba su mensaje a mano con ChatColor, y salian tres
 * estilos distintos: unos con corchetes, otros sin ellos, unos en amarillo y
 * otros en gris. Este es el unico sitio donde se decide como se escribe, para
 * que el staff reconozca el plugin de un vistazo.
 *
 * Identidad de Odissey:
 *
 *   prefijo            &8[&eODISSEY&8]
 *   exito              verde
 *   aviso              amarillo
 *   error              rojo
 *   informacion        gris claro
 *   titulos            dorado en negrita
 *
 * El prefijo vive en config.yml (mensajes.prefijo) para poder cambiarlo sin
 * recompilar. Si no esta, se usa el de Odissey.
 */
public final class Text {

    private Text() {
    }

    /** El prefijo por defecto, tal cual lo pide la identidad de Odissey. */
    public static final String PREFIJO_POR_DEFECTO = "&8[&eODISSEY&8]";

    private static String prefijo = PREFIJO_POR_DEFECTO;

    /** Se llama al arrancar y al recargar para leer el prefijo de config.yml. */
    public static void cargar(String valor) {
        prefijo = (valor == null || valor.trim().isEmpty()) ? PREFIJO_POR_DEFECTO : valor.trim();
    }

    /** Traduce los '&' de color. Si viene null, devuelve cadena vacia. */
    public static String c(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto == null ? "" : texto);
    }

    /** El prefijo ya coloreado, sin espacio detras. */
    public static String marca() {
        return c(prefijo);
    }

    /** El prefijo coloreado con un espacio de separacion. */
    public static String prefijo() {
        return c(prefijo) + " ";
    }

    /** Un mensaje informativo con el prefijo delante. */
    public static String info(String texto) {
        return prefijo() + ChatColor.GRAY + texto;
    }

    /** Accion completada: verde. */
    public static String exito(String texto) {
        return prefijo() + ChatColor.GREEN + texto;
    }

    /** Aviso: amarillo. */
    public static String aviso(String texto) {
        return prefijo() + ChatColor.YELLOW + texto;
    }

    /** Error o negacion: rojo. */
    public static String error(String texto) {
        return prefijo() + ChatColor.RED + texto;
    }

    /** Sin permiso: rojo, con un texto de ayuda debajo en gris. */
    public static String sinPermiso(String permiso) {
        return prefijo() + ChatColor.RED + "No tienes permiso para hacer esto."
                + (permiso == null || permiso.isEmpty() ? ""
                : ChatColor.DARK_GRAY + "\n" + prefijo() + ChatColor.DARK_GRAY + "Necesitas: " + permiso);
    }

    /** Titulo destacado en dorado y negrita. */
    public static String destacado(String texto) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + texto;
    }

    /** El estado ACTIVADO en verde y el desactivado en gris, para los menus. */
    public static String estado(boolean activo) {
        return activo ? ChatColor.GREEN + "ACTIVADO" : ChatColor.GRAY + "desactivado";
    }

    /** Sustituye los marcadores de un texto por sus valores. */
    public static String con(String texto, Object... pares) {
        if (texto == null) return "";
        String salida = texto;
        for (int i = 0; i + 1 < pares.length; i += 2) {
            salida = salida.replace(String.valueOf(pares[i]), String.valueOf(pares[i + 1]));
        }
        return salida;
    }
}
