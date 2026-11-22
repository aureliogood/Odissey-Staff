package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Donde guarda el plugin lo que tiene en memoria.
 *
 * Antes de esto cada store abria su propio .yml y lo escribia con
 * CopiaSeguridad.guardar(). El contenido es el mismo; lo unico que cambia es
 * donde acaba.
 *
 * La interfaz es a proposito burda: se pasa y se devuelve el
 * YamlConfiguration entero del store. Asi el parseo de cada store no cambia ni
 * una linea, y cambiar de YAML a MySQL (o al reves) es cosa del almacen, no de
 * doce clases.
 *
 * Se devuelve YamlConfiguration y no ConfigurationSection a proposito: todos los
 * stores construyen uno y lo releen con getString/getLong/getStringList, y
 * obligar a hacer casts en los doce sitios seria otra cosa que se puede romper.
 */
public interface Almacen {

    /**
     * Lo que hay guardado ahora mismo. Nunca devuelve null: si no hay nada, un
     * YamlConfiguration vacio, que es lo que ya hacia
     * YamlConfiguration.loadConfiguration() sobre un archivo que no existia.
     */
    YamlConfiguration leer();

    /**
     * Reemplaza TODO el contenido del store por el de este YamlConfiguration.
     *
     * Se reemplaza entero y no se fusiona a proposito: los stores construyen el
     * configuracion desde cero con lo que hay en memoria, asi que "reemplazar" es
     * exactamente lo que ya hacia el guardado en YAML, pero en una transaccion:
     * o se escribe todo o no se escribe nada.
     */
    void escribir(YamlConfiguration datos);

    /** Nombre de la tienda (tabla logica) dentro de la base de datos. */
    String tienda();
}
