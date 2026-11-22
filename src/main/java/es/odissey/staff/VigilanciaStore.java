package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lista de vigilancia: jugadores que el staff tiene fichados.
 *
 * Para cazadores de cuentas, gente que ya ha trompeado antes y reincidentes.
 * Al entrar uno de estos, a todos los staff que esten dentro les llega un aviso
 * con el motivo, para que nobody se le pase y le empiece a repartir trust sin
 * saber quien es.
 *
 * A diferencia de las notas, aqui hay UNA marca por jugador: o esta vigilado o
 * no lo esta. Meterle tres marcas a alguien no aporta nada.
 */
public final class VigilanciaStore {

    public static final class Marca {
        public String jugador = "";
        public String motivo = "";
        public String quien = "";
        public long cuando;
    }


    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<String, Marca> marcas = new ConcurrentHashMap<>();

    public VigilanciaStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("vigilancia", "vigilancia.yml");
    }


    private static String clave(String jugador) {
        return jugador == null ? "" : jugador.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean estaVigilado(String jugador) {
        return marcas.containsKey(clave(jugador));
    }

    public Marca marca(String jugador) {
        return marcas.get(clave(jugador));
    }

    public List<Marca> todas() {
        return new ArrayList<>(marcas.values());
    }

    public void vigilar(String jugador, String motivo, String quien) {
        Marca m = new Marca();
        m.jugador = jugador;
        m.motivo = motivo == null ? "" : motivo;
        m.quien = quien == null ? "" : quien;
        m.cuando = System.currentTimeMillis();
        marcas.put(clave(jugador), m);
        guardar();
    }

    public Marca quitar(String jugador) {
        Marca m = marcas.remove(clave(jugador));
        if (m != null) guardar();
        return m;
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        marcas.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("vigilados");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            Marca m = new Marca();
            m.jugador = y.getString("vigilados." + clave + ".jugador", clave);
            m.motivo = y.getString("vigilados." + clave + ".motivo", "");
            m.quien = y.getString("vigilados." + clave + ".quien", "");
            m.cuando = y.getLong("vigilados." + clave + ".cuando", 0);
            // La clave se normaliza como cuando se escribe. Un archivo viejo o
            // editado a mano con "Bob:" en vez de "bob:" dejaba al jugador
            // vigilado en el archivo pero sin avisar nunca al entrar, porque
            // estaVigilado() lo busca en minusculas y no lo encontraba.
            marcas.put(clave(clave), m);
        }
        if (!marcas.isEmpty()) {
            plugin.getLogger().info("[vigilancia] " + marcas.size() + " jugador(es) en la lista.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Marca> e : marcas.entrySet()) {
            String base = "vigilados." + e.getKey() + ".";
            y.set(base + "jugador", e.getValue().jugador);
            y.set(base + "motivo", e.getValue().motivo);
            y.set(base + "quien", e.getValue().quien);
            y.set(base + "cuando", e.getValue().cuando);
        }
        almacen.escribir(y);
    }

    public static String fecha(long ms) {
        return Fechas.diaHora(ms);
    }
}
