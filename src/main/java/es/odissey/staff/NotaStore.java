package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notas internas del staff sobre un jugador.
 *
 * Esto NO es una sancion: una sancion explica un hecho ("robo una base"), una
 * nota explica un patron ("dice que es admin del server y no lo es", "aparece
 * con una cuenta nueva justo antes de estafar").
 *
 * Sin esto, el staff se va de vacaciones y a la vuelta nadie se acuerda de por
 * que ese jugador no era de fiar. Las notas son la memoria del equipo.
 *
 * El jugador NUNCA ve una nota. No se le avisa, no aparece en su perfil y no se
 * le dice que esta anotado.
 */
public final class NotaStore {

    public static final class Nota {
        public int id;
        public String autor = "";
        public String texto = "";
        public long cuando;
    }


    private final OdisseyStaff plugin;
    private final Almacen almacen;
    /** Clave: nombre del jugador en minusculas. */
    private final Map<String, List<Nota>> notas = new ConcurrentHashMap<>();
    private final Map<String, Integer> siguienteId = new ConcurrentHashMap<>();

    public NotaStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("notas", "notas.yml");
    }


    private static String clave(String jugador) {
        return jugador == null ? "" : jugador.toLowerCase(java.util.Locale.ROOT);
    }

    public void anotar(String jugador, String autor, String texto) {
        String k = clave(jugador);
        if (k.isEmpty() || texto == null || texto.trim().isEmpty()) return;
        Nota n = new Nota();
        // siguienteId guarda el id siguiente libre (cargar() lo deja en max + 1),
        // asi que se saca con getAndIncrement y no con un merge que saltaba dos.
        Integer base = siguienteId.get(k);
        n.id = (base == null ? 1 : base);
        siguienteId.put(k, n.id + 1);
        n.autor = autor == null ? "" : autor;
        n.texto = texto.trim();
        n.cuando = System.currentTimeMillis();
        notas.computeIfAbsent(k, x -> new ArrayList<>()).add(n);
        guardar();
    }

    public List<Nota> de(String jugador) {
        List<Nota> lista = notas.get(clave(jugador));
        return lista == null ? new ArrayList<>() : new ArrayList<>(lista);
    }

    public int cantidad(String jugador) {
        return de(jugador).size();
    }

    /** Cuantas notas tiene un jugador como minimo para que /staffinfo avise. */
    public int umbralAviso() {
        return Math.max(1, plugin.config().getInt("notas.aviso-a-partir-de", 3));
    }

    public boolean avisar(String jugador) {
        return cantidad(jugador) >= umbralAviso();
    }

    public boolean borrar(String jugador, int id) {
        List<Nota> lista = notas.get(clave(jugador));
        if (lista == null) return false;
        boolean quitado = lista.removeIf(n -> n.id == id);
        if (quitado) {
            if (lista.isEmpty()) notas.remove(clave(jugador));
            guardar();
        }
        return quitado;
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        notas.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("notas");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            List<Nota> lista = new ArrayList<>();
            int max = 0;
            for (String id : y.getStringList("notas." + clave + ".ids")) {
                try {
                    int n = Integer.parseInt(id);
                    Nota nota = new Nota();
                    nota.id = n;
                    nota.autor = y.getString("notas." + clave + "." + n + ".autor", "");
                    nota.texto = y.getString("notas." + clave + "." + n + ".texto", "");
                    nota.cuando = y.getLong("notas." + clave + "." + n + ".cuando", 0);
                    lista.add(nota);
                    if (n >= max) max = n;
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("[notas] id invalido ignorado: " + id);
                }
            }
            if (!lista.isEmpty()) {
                notas.put(clave, lista);
                siguienteId.put(clave, max + 1);
            }
        }
        if (!notas.isEmpty()) {
            plugin.getLogger().info("[notas] " + notas.size() + " jugador(es) con notas cargados.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, List<Nota>> e : notas.entrySet()) {
            String base = "notas." + e.getKey() + ".";
            List<String> ids = new ArrayList<>();
            for (Nota n : e.getValue()) {
                ids.add(String.valueOf(n.id));
                y.set(base + n.id + ".autor", n.autor);
                y.set(base + n.id + ".texto", n.texto);
                y.set(base + n.id + ".cuando", n.cuando);
            }
            y.set(base + "ids", ids);
        }
        almacen.escribir(y);
    }

    public static String fecha(long ms) {
        return Fechas.diaHora(ms);
    }
}
