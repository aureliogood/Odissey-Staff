package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reportes de jugadores y solicitudes de soporte, guardados en el almacén normal del plugin. */
public final class ReportStore {
    public static final class Entrada {
        public long id;
        public String tipo = "reporte";
        public UUID autorUuid;
        public String autor = "";
        public String objetivo = "";
        public String texto = "";
        public long creado;
        public boolean cerrado;
        public String cerradoPor = "";
    }

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<Long, Entrada> entradas = new LinkedHashMap<>();
    private long siguienteId = 1;

    public ReportStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("reportes", "reportes.yml");
    }

    public synchronized void cargar() {
        YamlConfiguration y = almacen.leer();
        entradas.clear();
        siguienteId = Math.max(1, y.getLong("siguiente-id", 1));
        var seccion = y.getConfigurationSection("entradas");
        if (seccion == null) return;
        long maximo = 0;
        for (String clave : seccion.getKeys(false)) {
            String p = "entradas." + clave + ".";
            try {
                Entrada e = new Entrada();
                e.id = y.getLong(p + "id", Long.parseLong(clave));
                String uuid = y.getString(p + "autor-uuid", "");
                if (!uuid.isBlank()) e.autorUuid = UUID.fromString(uuid);
                e.tipo = y.getString(p + "tipo", "reporte");
                e.autor = y.getString(p + "autor", "");
                e.objetivo = y.getString(p + "objetivo", "");
                e.texto = y.getString(p + "texto", "");
                e.creado = y.getLong(p + "creado", 0);
                e.cerrado = y.getBoolean(p + "cerrado", false);
                e.cerradoPor = y.getString(p + "cerrado-por", "");
                entradas.put(e.id, e);
                maximo = Math.max(maximo, e.id);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[reportes] entrada invalida ignorada: " + clave);
            }
        }
        siguienteId = Math.max(siguienteId, maximo + 1);
    }

    public synchronized Entrada crear(String tipo, UUID autorUuid, String autor, String objetivo, String texto) {
        Entrada e = new Entrada();
        e.id = siguienteId++;
        e.tipo = tipo;
        e.autorUuid = autorUuid;
        e.autor = autor;
        e.objetivo = objetivo == null ? "" : objetivo;
        e.texto = texto;
        e.creado = System.currentTimeMillis();
        entradas.put(e.id, e);
        int limite = Math.max(100, plugin.config().getInt("reportes.max-registros", 2000));
        while (entradas.size() > limite) entradas.remove(entradas.keySet().iterator().next());
        guardar();
        return e;
    }

    public synchronized Entrada obtener(long id) { return entradas.get(id); }

    public synchronized List<Entrada> recientes(boolean abiertos, int limite) {
        List<Entrada> lista = new ArrayList<>(entradas.values());
        if (abiertos) lista.removeIf(e -> e.cerrado);
        lista.sort(Comparator.comparingLong((Entrada e) -> e.creado).reversed());
        if (lista.size() > limite) return new ArrayList<>(lista.subList(0, limite));
        return lista;
    }

    public synchronized boolean cerrar(long id, String staff) {
        Entrada e = entradas.get(id);
        if (e == null || e.cerrado) return false;
        e.cerrado = true;
        e.cerradoPor = staff;
        guardar();
        return true;
    }

    public synchronized boolean tieneAbierto(UUID uuid) {
        return entradas.values().stream().anyMatch(e -> e.tipo.equals("ticket")
                && !e.cerrado && uuid.equals(e.autorUuid));
    }

    public synchronized void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente-id", siguienteId);
        for (Entrada e : entradas.values()) {
            String p = "entradas." + e.id + ".";
            y.set(p + "id", e.id);
            y.set(p + "tipo", e.tipo);
            y.set(p + "autor-uuid", e.autorUuid == null ? "" : e.autorUuid.toString());
            y.set(p + "autor", e.autor);
            y.set(p + "objetivo", e.objetivo);
            y.set(p + "texto", e.texto);
            y.set(p + "creado", e.creado);
            y.set(p + "cerrado", e.cerrado);
            y.set(p + "cerrado-por", e.cerradoPor);
        }
        almacen.escribir(y);
    }
}
