package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Copias de seguridad de los archivos del plugin.
 *
 * Los .yml guardan las sanciones, el historial, las notas, la vigilancia, los
 * silencios y los congelados. Si uno se corrompe (un corte de luz a media
 * escritura, un guardado a mano con el servidor arrancado, un {} de más) lo
 * normal es perder ese archivo entero, y con el las sanciones activas.
 *
 * Antes de pisar cualquier archivo se copia el que hay a la carpeta "copias",
 * con la hora en el nombre. Asi siempre se puede volver atras con
 * /staffrestaurar.
 *
 * Solo se guardan las N ultimas copias de cada archivo (10 por defecto), para
 * que la carpeta no crezca sin limite.
 */
public final class CopiaSeguridad {

    private CopiaSeguridad() {
    }

    private static File carpeta(OdisseyStaff plugin) {
        return new File(plugin.getDataFolder(), "copias");
    }

    /**
     * El nombre que tendra una copia: "<archivo>_<hora>.yml".
     *
     * Con los segundos en el nombre, dos guardados seguidos dentro del mismo
     * minuto no se pisan.
     */
    public static String nombreCopia(String archivo, long ahora) {
        String base = archivo.endsWith(".yml")
                ? archivo.substring(0, archivo.length() - 4) : archivo;
        return base + "_" + Fechas.de(ahora, Fechas.SELLO) + ".yml";
    }

    /**
     * Copia el archivo que hay antes de que otro lo pise.
     *
     * Si el archivo no existe todavia no hay nada que guardar: no se crea una
     * copia vacia la primera vez.
     */
    public static void copiarAntes(OdisseyStaff plugin, File destino) {
        try {
            if (!plugin.config().getBoolean("copias.activo", true)) return;
            if (destino == null || !destino.exists() || destino.length() == 0) return;

            int max = Math.max(1, plugin.config().getInt("copias.cuantas-por-archivo", 10));
            File dir = carpeta(plugin);
            if (!dir.exists() && !dir.mkdirs()) return;

            String base = nombreBase(destino.getName());
            File copia = new File(dir, nombreCopia(destino.getName(), System.currentTimeMillis()));
            java.nio.file.Files.copy(destino.toPath(), copia.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            podar(dir, base, max);
        } catch (Throwable t) {
            // Una copia que falla no puede soltar el guardado: el aviso va al log
            // y el plugin sigue, que perder la copia es mejor que perder la
            // sanción que se estaba guardando.
            plugin.getLogger().warning("[copias] no se pudo copiar " + destino.getName()
                    + ": " + t.getMessage());
        }
    }

    /** Escribe un yml, copiando antes el archivo anterior. */
    public static void guardar(OdisseyStaff plugin, YamlConfiguration y, File destino)
            throws IOException {
        copiarAntes(plugin, destino);
        File padre = destino.getParentFile();
        if (padre != null && !padre.exists()) padre.mkdirs();
        y.save(destino);
    }

    private static String nombreBase(String archivo) {
        int punto = archivo.indexOf(".yml");
        return punto < 0 ? archivo : archivo.substring(0, punto);
    }

    /** Se queda con las N copias mas recientes de un archivo. */
    private static void podar(File dir, String base, int max) {
        File[] mias = dir.listFiles((d, name) ->
                name.startsWith(base + "_") && name.endsWith(".yml"));
        if (mias == null || mias.length <= max) return;
        Arrays.sort(mias, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < mias.length - max; i++) {
            // La ultima copia es la que se acaba de hacer: nunca se borra esa.
            if (mias[i].lastModified() >= mias[mias.length - 1].lastModified()) continue;
            mias[i].delete();
        }
    }

    /** Las copias disponibles, de la mas nueva a la mas vieja. */
    public static List<String> listar(OdisseyStaff plugin) {
        List<String> salida = new ArrayList<>();
        File[] archivos = carpeta(plugin).listFiles((d, name) -> name.endsWith(".yml"));
        if (archivos == null) return salida;
        Arrays.sort(archivos, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : archivos) salida.add(f.getName());
        return salida;
    }

    /**
     * Devuelve un archivo a una copia anterior.
     *
     * Antes de pisarlo, copia el actual: asi restaurar tambien se puede
     * deshacer, y un /staffrestaurar con el nombre mal escrito no deja nada
     * sin copia.
     */
    public static boolean restaurar(OdisseyStaff plugin, String copia) {
        // El nombre viene de un jugador: sin esto se podria escribir
        // "../../plugins/algo.yml" y machacar un archivo de fuera.
        if (copia == null || !copia.matches("[A-Za-z0-9._-]+\\.yml") || copia.contains("..")) {
            return false;
        }
        File origen = new File(carpeta(plugin), copia);
        if (!origen.exists()) return false;

        String base = baseDe(copia);
        File destino = new File(plugin.getDataFolder(), base);

        try {
            copiarAntes(plugin, destino);
            java.nio.file.Files.copy(origen.toPath(), destino.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[copias] restaurado " + base + " desde " + copia + ".");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[copias] no se pudo restaurar " + copia
                    + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Que archivo del plugin es, para recargarlo despues de restaurar.
     *
     * El nombre de una copia es "<archivo>_<sello>.yml" y el sello
     * (yyyy-MM-dd_HH-mm-ss) lleva dentro un guion bajo. Por eso se corta en el
     * PRIMER guion y no en el ultimo: con lastIndexOf, la copia
     * "sanciones_2026-10-16_01-10-00.yml" se leia como "sanciones_2026-10-16.yml",
     * un archivo que no existe, y la restauracion no hacia nada.
     */
    public static String baseDe(String copia) {
        String base = copia;
        int guion = copia.indexOf('_');
        return guion > 0 ? base.substring(0, guion) + ".yml" : base;
    }

    /**
     * Vuelve a leer del disco el store que corresponde a un archivo.
     *
     * Sin esto, restaurarangan would pondria el archivo viejo en su sitio pero
     * el plugin seguiria usando lo que tenia en memoria, y al siguiente
     * guardado lo pisaria otra vez.
     */
    public static void recargar(OdisseyStaff plugin, String base) {
        switch (base) {
            case "sanciones": plugin.sanciones().cargar(); break;
            case "historial": plugin.historial().cargar(); break;
            case "silencios": plugin.silencios().cargar(); break;
            case "congelados": plugin.congelados().cargar(); break;
            case "notas": plugin.notas().cargar(); break;
            case "vigilancia": plugin.vigilancia().cargar(); break;
            case "bloqueos": plugin.bloqueos().cargar(); break;
            case "conexiones": plugin.conexiones().cargar(); break;
            case "inventarios": plugin.actividad().cargar(); break;
            // El kit se guarda por jugador, no tiene cargar(): si se restaura, se
            // aplica en el siguiente cambio de inventario de cada uno.
            case "herramientas": break;
            case "staffchat": plugin.chatLog().cargar(); break;
            case "chat": plugin.chatJugadores().cargar(); break;
            default: break;
        }
    }

    /** El texto de una copia, para(listarla en el chat. */
    public static String linea(OdisseyStaff plugin, String copia) {
        File f = new File(carpeta(plugin), copia);
        String cuando = "?" + ChatColor.DARK_GRAY;
        if (f.exists()) {
            cuando = Fechas.de(f.lastModified(), Fechas.DIA_HORA_SEG);
        }
        long bytes = f.exists() ? f.length() : 0L;
        return ChatColor.GRAY + copia + ChatColor.DARK_GRAY + "  " + cuando
                + ChatColor.DARK_GRAY + "  " + (bytes / 1024) + " KB";
    }
}
