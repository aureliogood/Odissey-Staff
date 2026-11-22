package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Referencias y capturas de evidencia asociadas a una cuenta. */
public final class EvidenceStore {
    public static final class Evidencia {
        public long id;
        public String jugador = "";
        public String tipo = "";
        public String valor = "";
        public String autor = "";
        public long cuando;
    }

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<Long, Evidencia> datos = new LinkedHashMap<>();
    private long siguienteId = 1;

    public EvidenceStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("evidencias", "evidencias.yml");
    }

    public synchronized void cargar() {
        YamlConfiguration y = almacen.leer();
        datos.clear();
        siguienteId = Math.max(1, y.getLong("siguiente-id", 1));
        var seccion = y.getConfigurationSection("entradas");
        if (seccion == null) return;
        long max = 0;
        for (String clave : seccion.getKeys(false)) {
            String p = "entradas." + clave + ".";
            try {
                Evidencia e = new Evidencia();
                e.id = y.getLong(p + "id", Long.parseLong(clave));
                e.jugador = y.getString(p + "jugador", "");
                e.tipo = y.getString(p + "tipo", "");
                e.valor = y.getString(p + "valor", "");
                e.autor = y.getString(p + "autor", "");
                e.cuando = y.getLong(p + "cuando", 0);
                datos.put(e.id, e);
                max = Math.max(max, e.id);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[evidencias] entrada inválida ignorada: " + clave);
            }
        }
        siguienteId = Math.max(siguienteId, max + 1);
    }

    public synchronized Evidencia agregar(String jugador, String tipo, String valor, String autor) {
        Evidencia e = new Evidencia();
        e.id = siguienteId++;
        e.jugador = jugador;
        e.tipo = tipo;
        e.valor = valor;
        e.autor = autor;
        e.cuando = System.currentTimeMillis();
        datos.put(e.id, e);
        int limite = Math.max(100, plugin.config().getInt("evidencias.max-registros", 5000));
        while (datos.size() > limite) datos.remove(datos.keySet().iterator().next());
        guardar();
        return e;
    }

    public Evidencia capturarInventario(Player jugador, String autor) {
        List<String> items = new ArrayList<>();
        ItemStack[] ranuras = jugador.getInventory().getStorageContents();
        for (int i = 0; i < ranuras.length; i++) {
            ItemStack item = ranuras[i];
            if (item != null && item.getType() != Material.AIR) items.add("slot " + i + "=" + item.getType() + "x" + item.getAmount());
        }
        ItemStack[] armadura = jugador.getInventory().getArmorContents();
        for (int i = 0; i < armadura.length; i++) {
            ItemStack item = armadura[i];
            if (item != null && item.getType() != Material.AIR) items.add("armadura " + i + "=" + item.getType() + "x" + item.getAmount());
        }
        ItemStack mano = jugador.getInventory().getItemInOffHand();
        if (mano.getType() != Material.AIR) items.add("mano secundaria=" + mano.getType() + "x" + mano.getAmount());
        var loc = jugador.getLocation();
        String captura = "mundo=" + loc.getWorld().getName() + ",xyz=" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + ",vida=" + String.format(java.util.Locale.ROOT, "%.1f", jugador.getHealth())
                + ",comida=" + jugador.getFoodLevel() + ",nivel=" + jugador.getLevel()
                + ",inventario=" + (items.isEmpty() ? "vacío" : String.join("; ", items));
        return agregar(jugador.getName(), "captura-inventario", captura, autor);
    }

    /** Guarda el contexto reciente del chat al aplicar una sanción. */
    public Evidencia capturarChat(String jugador, ChatJugadoresLog log, int minutos, int limite) {
        if (jugador == null || jugador.isBlank() || log == null) return null;
        List<ChatJugadoresLog.Linea> lineas = log.de(jugador, Math.max(1, minutos), "", Math.max(1, limite));
        if (lineas.isEmpty()) return null;
        java.util.Collections.reverse(lineas);
        List<String> texto = new ArrayList<>();
        for (ChatJugadoresLog.Linea linea : lineas)
            texto.add("[" + ChatJugadoresLog.fecha(linea.cuando) + "] " + linea.texto);
        return agregar(jugador, "chat-snapshot", String.join("\n", texto), "OdisseyStaff");
    }

    public synchronized List<Evidencia> de(String jugador) {
        List<Evidencia> lista = new ArrayList<>();
        for (Evidencia e : datos.values()) if (e.jugador.equalsIgnoreCase(jugador)) lista.add(e);
        lista.sort(Comparator.comparingLong((Evidencia e) -> e.cuando).reversed());
        return lista;
    }

    public synchronized void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente-id", siguienteId);
        for (Evidencia e : datos.values()) {
            String p = "entradas." + e.id + ".";
            y.set(p + "id", e.id);
            y.set(p + "jugador", e.jugador);
            y.set(p + "tipo", e.tipo);
            y.set(p + "valor", e.valor);
            y.set(p + "autor", e.autor);
            y.set(p + "cuando", e.cuando);
        }
        almacen.escribir(y);
    }
}
