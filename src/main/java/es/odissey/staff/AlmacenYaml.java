package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * El almacén de siempre: un archivo .yml por store, con copia del anterior
 * antes de pisarlo.
 *
 * Se sigue usando en dos casos: cuando la base de datos esta en modo yaml, y
 * cuando la base de datos falla y el plugin se cae de nuevo a archivos (para no
 * perder unas sanciones en activo por un problema de conexión).
 */
public final class AlmacenYaml implements Almacen {

    private final OdisseyStaff plugin;
    private final String tienda;
    private final File archivo;

    public AlmacenYaml(OdisseyStaff plugin, String tienda, File archivo) {
        this.plugin = plugin;
        this.tienda = tienda;
        this.archivo = archivo;
    }

    @Override
    public YamlConfiguration leer() {
        if (!archivo.exists()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(archivo);
    }

    @Override
    public void escribir(YamlConfiguration datos) {
        try {
            CopiaSeguridad.guardar(plugin, datos, archivo);
        } catch (IOException e) {
            plugin.getLogger().warning("[" + tienda + "] no se pudo guardar: " + e.getMessage());
        }
    }

    @Override
    public String tienda() {
        return tienda;
    }

    /** El archivo que usa este store, para la migración y para /staffrestaurar. */
    public File archivo() {
        return archivo;
    }
}
