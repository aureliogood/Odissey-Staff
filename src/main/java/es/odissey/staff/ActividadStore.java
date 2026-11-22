package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los ultimos minutos de lo que ha hecho un jugador: bloques rotos y puestos,
 * comandos y chat.
 *
 * Sirve para cuando alguien reporta "me ha robado la base" y ya no sabes que
 * paso. /staffsnapshot Rob saca lo de los ultimos minutos y lo deja guardado,
 * que es cuando hace falta: mientras se investiga, no se va a perder.
 *
 * Se guarda en memoria con un tope por jugador. No hace falta mas: si un
 * jugador lleva dias conectado haciendo cosas raras, interesa lo de los ultimos
 * minutos, no su historia completa.
 */
public final class ActividadStore {

    public static final class Hecho {
        public String que = "";
        public String detalle = "";
        public long cuando;

        public String linea() {
            return " · " + que + (detalle.isEmpty() ? "" : " " + detalle)
                    + " (" + HORA.format(java.time.Instant.ofEpochMilli(cuando)) + ")";
        }
    }

    /** Lo que llevaba un jugador cuando salio del servidor. */
    public static final class Inventario {
        public String nombre = "";
        public long cuando;
        public List<String> objetos = new ArrayList<>();
    }

    private static final java.time.format.DateTimeFormatter HORA =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    // El chat de Bukkit corre en un hilo aparte y anota desde ahi (ver
    // StaffListener.alEscribir), asi que estos mapas se tocan fuera del hilo
    // principal. Con LinkedHashMap normal eso es una carrera.
    private final Map<UUID, Deque<Hecho>> reciente = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Inventario> inventarioAlSalir = new java.util.concurrent.ConcurrentHashMap<>();

    public ActividadStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("inventarios", "inventarios.yml");
    }

    public int tope() {
        return Math.max(20, plugin.config().getInt("actividad.max-por-jugador", 200));
    }

    public int minutos() {
        return Math.max(1, plugin.config().getInt("actividad.minutos-guardados", 10));
    }

    public void anotar(Player jugador, String que, String detalle) {
        Deque<Hecho> cola = reciente.computeIfAbsent(jugador.getUniqueId(), k -> new ArrayDeque<>());
        Hecho h = new Hecho();
        h.que = que;
        h.detalle = detalle == null ? "" : detalle;
        h.cuando = System.currentTimeMillis();
        // El mapa es concurrente, pero el ArrayDeque de dentro NO lo es, y se
        // escribe desde el hilo del chat (StaffListener.alEscribir) mientras el
        // panel de espia lo recorre desde el hilo principal. Sin este bloque,
        // dos jugadores escribiendo a la vez dejaban el deque corrupto y el
        // recorrido del panel reventaba, lo que ademas mataba la tarea de
        // refresco de TODOS los paneles de espia.
        synchronized (cola) {
            cola.addFirst(h);
            while (cola.size() > tope()) cola.removeLast();
        }
    }

    /** Lo que ha hecho en los ultimos N minutos, de mas reciente a mas antiguo. */
    public List<Hecho> reciente(UUID uuid, int minutos) {
        Deque<Hecho> cola = reciente.get(uuid);
        if (cola == null) return new ArrayList<>();
        long desde = System.currentTimeMillis() - minutos * 60_000L;
        List<Hecho> fuera = new ArrayList<>();
        synchronized (cola) {
            for (Hecho h : cola) {
                if (h.cuando >= desde) fuera.add(h);
            }
        }
        return fuera;
    }

    /**
     * Lo que llevo en el inventario.
     *
     * No se guarda el item entero sino su tipo y su cantidad: es lo que hace falta
     * para saber que llevaba ("36 bloques de diamante") y evita meter en el
     * archivo los nombres del mundo y los enchantamientos.
     */
    public void guardarInventario(Player jugador) {
        Inventario inv = new Inventario();
        inv.nombre = jugador.getName();
        inv.cuando = System.currentTimeMillis();
        for (ItemStack item : jugador.getInventory().getStorageContents()) {
            if (item == null || item.getType().name().equals("AIR") || item.getAmount() <= 0) continue;
            inv.objetos.add(item.getAmount() + "x " + bonito(item.getType().name()));
        }
        for (ItemStack item : jugador.getInventory().getArmorContents()) {
            if (item == null || item.getType().name().equals("AIR")) continue;
            inv.objetos.add("1x " + bonito(item.getType().name()) + " (armadura)");
        }
        inventarioAlSalir.put(jugador.getUniqueId(), inv);
    }

    public Inventario inventario(UUID uuid) {
        return inventarioAlSalir.get(uuid);
    }

    /** "DIAMOND_BLOCK" -> "Diamond block", mas legible que el nombre interno. */
    private static String bonito(String material) {
        String[] partes = material.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public int total(UUID uuid) {
        Deque<Hecho> cola = reciente.get(uuid);
        if (cola == null) return 0;
        synchronized (cola) {
            return cola.size();
        }
    }

    /** Cuanto lleva conectado de esta sesion, para el snapshot. */
    public void limpiar(UUID uuid) {
        reciente.remove(uuid);
    }

    // ---------- Disco: solo los inventarios, que son los que sobreviven ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("salidas");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            Inventario inv = new Inventario();
            inv.nombre = y.getString("salidas." + clave + ".nombre", "");
            inv.cuando = y.getLong("salidas." + clave + ".cuando", 0);
            inv.objetos = y.getStringList("salidas." + clave + ".objetos");
            try {
                inventarioAlSalir.put(UUID.fromString(clave), inv);
            } catch (IllegalArgumentException e) { /* entrada mala */ }
        }
        if (!inventarioAlSalir.isEmpty()) {
            plugin.getLogger().info("[actividad] " + inventarioAlSalir.size() + " inventarios guardados.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        // Un dia es suficiente: para mirar un robo el jugador acaba de salir.
        long limite = System.currentTimeMillis() - 24L * 3600_000L;
        for (Map.Entry<UUID, Inventario> e : inventarioAlSalir.entrySet()) {
            if (e.getValue().cuando < limite) continue;
            String p = "salidas." + e.getKey() + ".";
            y.set(p + "nombre", e.getValue().nombre);
            y.set(p + "cuando", e.getValue().cuando);
            y.set(p + "objetos", e.getValue().objetos);
        }
        almacen.escribir(y);
    }
}
