package es.odissey.staff;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un store guardado en la base de datos.
 *
 * Cada store es una "tienda" (una columna) y cada clave de primer nivel del
 * archivo .yml que se guardaba antes es una fila. Asi el historial no es un
 * bloque de texto: son miles de filas que se pueden consultar, y guardar un
 * cambio reescribe la tienda en una transaccion, de forma que o sale entero o no
 * sale nada.
 *
 * El valor de cada fila es el YAML de esa clave. Se sigue usando YAML dentro de
 * la base a proposito: los stores llevan anos guardando con getString, getLong y
 * getStringList, y cambiar a la vez el sitio DONDE se guarda y COMO se codifica
 * cada dato seria cambiar dos cosas a la vez, que es como se pierden silencios.
 */
public final class AlmacenSql implements Almacen {

    private final OdisseyStaff plugin;
    private final ConexionBd bd;
    private final String tienda;

    public AlmacenSql(OdisseyStaff plugin, ConexionBd bd, String tienda) {
        this.plugin = plugin;
        this.bd = bd;
        this.tienda = tienda;
    }

    @Override
    public YamlConfiguration leer() throws ErrorAlmacen {
        YamlConfiguration raiz = new YamlConfiguration();
        for (Map.Entry<String, String> fila : filas().entrySet()) {
            YamlConfiguration parte = new YamlConfiguration();
            try {
                parte.loadFromString(fila.getValue());
            } catch (InvalidConfigurationException e) {
                plugin.getLogger().warning("[" + tienda + "] se ignoro una fila ilegible ("
                        + fila.getKey() + "): " + e.getMessage());
                continue;
            }
            for (String clave : parte.getKeys(false)) raiz.set(clave, parte.get(clave));
        }
        return raiz;
    }

    @Override
    public void escribir(YamlConfiguration datos) {
        try {
            escribirFilas(filasDe(datos));
        } catch (SQLException e) {
            throw new ErrorAlmacen(tienda, e);
        }
    }

    /** Todas las filas de esta tienda, ordenadas por clave. */
    public Map<String, String> filas() {
        try {
            return filasInternas();
        } catch (SQLException e) {
            throw new ErrorAlmacen(tienda, e);
        }
    }

    private Map<String, String> filasInternas() throws SQLException {
        Map<String, String> salida = new LinkedHashMap<>();
        try (PreparedStatement s = bd.abrir().prepareStatement(
                "SELECT clave, valor FROM odissey_datos WHERE tienda = ? ORDER BY clave")) {
            s.setString(1, tienda);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) salida.put(r.getString("clave"), r.getString("valor"));
            }
        }
        return salida;
    }

    /** Si esta tienda no tiene ni una fila todavia. */
    public boolean vacia() {
        try {
            return vaciaInterna();
        } catch (SQLException e) {
            throw new ErrorAlmacen(tienda, e);
        }
    }

    private boolean vaciaInterna() throws SQLException {
        try (PreparedStatement s = bd.abrir().prepareStatement(
                "SELECT 1 FROM odissey_datos WHERE tienda = ? LIMIT 1")) {
            s.setString(1, tienda);
            try (ResultSet r = s.executeQuery()) {
                return !r.next();
            }
        }
    }

    /**
     * Copia un archivo YAML entero a la base, fila por fila.
     *
     * Reemplaza la tienda entera, igual que escribir(). En la migración no hay
     * diferencia porque la base esta vacia, pero para /staffrestaurar si: si
     * solo insertara, las claves que ya existen darian error de clave duplicada
     * y el store se quedaria con la mitad de los datos.
     */
    public int importar(File origen) {
        try {
            return escribirFilas(filasDe(YamlConfiguration.loadConfiguration(origen)));
        } catch (SQLException e) {
            throw new ErrorAlmacen(tienda, e);
        }
    }

    /** Parte el section en una fila por clave de primer nivel. */
    private Map<String, String> filasDe(YamlConfiguration datos) {
        Map<String, String> filas = new LinkedHashMap<>();
        if (datos == null) return filas;
        for (String clave : datos.getKeys(false)) {
            YamlConfiguration parte = new YamlConfiguration();
            parte.set(clave, datos.get(clave));
            filas.put(clave, parte.saveToString());
        }
        return filas;
    }

    /** Borra la tienda entera y la vuelve a escribir, todo en una transaccion. */
    private int escribirFilas(Map<String, String> filas) throws SQLException {
        Connection c = bd.abrir();
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement borrar = c.prepareStatement("DELETE FROM odissey_datos WHERE tienda = ?")) {
                borrar.setString(1, tienda);
                borrar.executeUpdate();
            }
            if (!filas.isEmpty()) {
                try (PreparedStatement insertar = c.prepareStatement(
                        "INSERT INTO odissey_datos (tienda, clave, valor) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> fila : filas.entrySet()) {
                        insertar.setString(1, tienda);
                        insertar.setString(2, fila.getKey());
                        insertar.setString(3, fila.getValue());
                        insertar.addBatch();
                    }
                    insertar.executeBatch();
                }
            }
            c.commit();
            return filas.size();
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException alRevertir) {
                bd.invalidar();
            }
            throw new ErrorAlmacen(tienda, e);
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    @Override
    public String tienda() {
        return tienda;
    }
}
