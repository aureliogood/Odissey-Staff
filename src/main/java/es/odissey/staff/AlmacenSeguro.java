package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * El almacén que ve el resto del plugin: base de datos si se puede, archivos si
 * no.
 *
 * Tres decisiones aqui, y las tres estan aqui y no repartidas por los stores:
 *
 *  - Si la base de datos no arranca (MySQL caido, driver que no baja, contrasena
 *    mal), el plugin sigue funcionando con los .yml de siempre y avisa por consola.
 *    Un fallo de base de datos no puede dejar el servidor sin sanciones.
 *  - Si la base se cae mas adelante, el primer guardado que falla da la vuelta
 *    permanente a los archivos.
 *  - La primera vez, si la base esta vacia y hay un .yml con datos, se importa.
 *    Los archivos NO se borran: se quedan como respaldo y como copia de
 *    seguridad, igual que hasta ahora.
 */
public final class AlmacenSeguro implements Almacen {

    private final OdisseyStaff plugin;
    private final String tienda;
    private final AlmacenYaml archivos;
    private final AlmacenSql base;
    private boolean usandoBase;
    private boolean importado;
    /** Nombre del archivo .yml de este store, para la migración. */
    private final String archivo;

    public AlmacenSeguro(OdisseyStaff plugin, String tienda, AlmacenYaml archivos, AlmacenSql base) {
        this.plugin = plugin;
        this.tienda = tienda;
        this.archivos = archivos;
        this.archivo = archivos.archivo().getName();
        this.base = base;
        this.usandoBase = base != null;
    }

    @Override
    public YamlConfiguration leer() {
        if (!usandoBase) return archivos.leer();
        try {
            migrarSiHaceFalta();
            return base.leer();
        } catch (RuntimeException e) {
            return caida("lectura", e);
        }
    }

    @Override
    public void escribir(YamlConfiguration datos) {
        if (!usandoBase) {
            archivos.escribir(datos);
            return;
        }
        try {
            base.escribir(datos);
        } catch (RuntimeException e) {
            caida("escritura", e);
            // Se escribe tambien en el archivo: lo que hay en memoria no puede
            // quedarse sin guardar porque la base no este.
            archivos.escribir(datos);
        }
    }

    @Override
    public String tienda() {
        return tienda;
    }

    /** Si se ha acabada la base de datos para este store. */
    private YamlConfiguration caida(String operacion, Exception e) {
        usandoBase = false;
        plugin.getLogger().warning("[" + tienda + "] fallo la " + operacion + " en la base de datos ("
                + e.getClass().getSimpleName() + ": " + e.getMessage() + "); se sigue con los archivos .yml.");
        return archivos.leer();
    }

    /**
     * Importa el .yml la primera vez.
     *
     * Solo si la base no tiene nada de esta tienda: si ya hay filas, el archivo
     * es la copia vieja y no se toca, que es lo que evita que un arranque
     * revierta lo que se ha escrito despues.
     */
    private void migrarSiHaceFalta() {
        if (importado) return;
        importado = true;
        if (!base.vacia()) return;
        File archivo = archivos.archivo();
        if (!archivo.exists() || archivo.length() == 0) return;
        int filas = base.importar(archivo);
        if (filas > 0) {
            plugin.getLogger().info("[" + tienda + "] migrados " + filas
                    + " registros de " + archivo.getName() + " a la base de datos."
                    + " El archivo se conserva como respaldo.");
        }
    }

    /** El almacén de una tienda concreta. */
    public static final class Fabrica {
        private final OdisseyStaff plugin;
        private final Map<String, AlmacenSeguro> almacenes = new HashMap<>();
        private final Map<String, AlmacenSeguro> porArchivo = new HashMap<>();
        private final ConexionBd bd;

        public Fabrica(OdisseyStaff plugin, ConexionBd bd) {
            this.plugin = plugin;
            this.bd = bd;
        }

        public Almacen de(String tienda, String archivo) {
            return almacenes.computeIfAbsent(tienda, t -> {
                AlmacenYaml yaml = new AlmacenYaml(plugin, t, new File(plugin.getDataFolder(), archivo));
                AlmacenSeguro almacen = new AlmacenSeguro(plugin, t, yaml,
                        bd == null ? null : new AlmacenSql(plugin, bd, t));
                porArchivo.put(sinYml(archivo), almacen);
                return almacen;
            });
        }

        /** Si de verdad se esta usando la base de datos y no los archivos. */
        public boolean usandoBase() {
            return bd != null;
        }

        /** "sanciones.yml" y "sanciones" son la misma tienda: sin la extension. */
        private static String sinYml(String nombre) {
            return nombre != null && nombre.endsWith(".yml")
                    ? nombre.substring(0, nombre.length() - 4) : nombre;
        }

        /**
         * Vuelca un archivo .yml restaurado dentro de la base de datos.
         *
         * Sin esto, /staffrestaurar con la base de datos puesta no serviria de
         * nada: el plugin releeria de la base y descartaria el archivo. Se
         * devuelve cuantas filas se han escrito, o -1 si ese archivo no
         * corresponde a ningun store.
         */
        public int importarArchivo(String base) {
            // La clave del mapa es el nombre sin ".yml" (igual que lo que manda
            // /staffrestaurar), aunque el archivo de disco si lleve extension.
            AlmacenSeguro almacen = porArchivo.get(sinYml(base));
            if (almacen == null || almacen.base == null) return -1;
            try {
                return almacen.base.importar(new File(plugin.getDataFolder(), almacen.archivo));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[bd] no se pudo importar " + base + ": " + e.getMessage());
                return -1;
            }
        }
    }
}
