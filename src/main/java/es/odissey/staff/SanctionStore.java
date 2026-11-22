package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Historial de todo lo que el staff ha hecho: avisos, silencios, baneos y
 * expulsiones.
 *
 * Antes no habia nada: un aviso a consola y un mensaje al chat del staff. Si el
 * staff no estaba, se perdia, y no habia forma de consultar si a alguien ya le
 * habian baneado antes ni por que. Con esto, /staffinfo y /staffhistory salen de
 * aqui.
 *
 * Se guarda en un archivo aparte del de silencios porque son cosas distintas: un
 * silencio caduca y desaparece, un historial se consulta para siempre. El
 * archivo se poda a los ultimos N registros para que no crezca sin fin.
 */
public final class SanctionStore {

    /** Una anotacion del historial. */
    public static final class Entrada {
        public long id;
        public String tipo = "";        // aviso, silencio, baneo, expulsion
        public String jugador = "";
        public String objetivo = "";
        public String staff = "";
        public String motivo = "";
        public String extra = "";       // duracion, o "levantado por X"
        public boolean levantada;
        public long cuando;

        /** Cuanto quedo de la anotacion, para /staffhistory. */
        public String linea() {
            StringBuilder sb = new StringBuilder();
            sb.append(ChatColor.GRAY).append("[")
              .append(ChatColor.WHITE).append(hora(cuando))
              .append(ChatColor.GRAY).append("] ")
              .append(colorTipo(tipo)).append(tipo.toUpperCase())
              .append(ChatColor.GRAY).append(" · ")
              .append(ChatColor.WHITE).append(jugador)
              .append(ChatColor.GRAY).append(" por ")
              .append(ChatColor.WHITE).append(staff);
            if (!motivo.isEmpty()) sb.append(ChatColor.GRAY).append(" · ").append(ChatColor.YELLOW).append(motivo);
            if (!extra.isEmpty()) sb.append(ChatColor.GRAY).append(" (").append(extra).append(")");
            if (levantada) sb.append(ChatColor.GRAY).append(" ").append(ChatColor.GREEN).append("[levantada]");
            return sb.toString();
        }
    }

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final List<Entrada> entradas = new ArrayList<>();
    private long siguienteId = 1;

    public SanctionStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("historial", "historial.yml");
    }


    private static ChatColor colorTipo(String tipo) {
        switch (tipo) {
            case "aviso": return ChatColor.YELLOW;
            case "silencio": return ChatColor.AQUA;
            case "baneo": return ChatColor.RED;
            case "expulsion": return ChatColor.LIGHT_PURPLE;
            default: return ChatColor.WHITE;
        }
    }

    private static String hora(long ms) {
        return Fechas.diaHora(ms);
    }

    // ---------- Guardar ----------

    public long anotar(String tipo, String jugador, String objetivo, String staff,
                      String motivo, String extra) {
        Entrada e = new Entrada();
        e.id = siguienteId++;
        e.tipo = tipo == null ? "" : tipo;
        e.jugador = jugador == null ? "" : jugador;
        e.objetivo = objetivo == null ? "" : objetivo;
        e.staff = staff == null ? "" : staff;
        e.motivo = motivo == null ? "" : motivo;
        e.extra = extra == null ? "" : extra;
        e.cuando = System.currentTimeMillis();
        entradas.add(e);
        podar();
        return e.id;
    }

    /** Marca como levantada la anotacion de un silencio concreto. */
    public void marcarLevantada(long id, String quien) {
        for (Entrada e : entradas) {
            if (e.id == id) {
                e.levantada = true;
                e.extra = e.extra.isEmpty() ? ("levantado por " + quien) : (e.extra + ", levantado por " + quien);
                return;
            }
        }
    }

    /** El id de la ultima anotacion de un tipo sobre un jugador, o -1. */
    public long ultimaDe(String tipo, String jugador) {
        for (int i = entradas.size() - 1; i >= 0; i--) {
            Entrada e = entradas.get(i);
            if (e.tipo.equals(tipo) && e.jugador.equalsIgnoreCase(jugador)) return e.id;
        }
        return -1L;
    }

    // ---------- Consultar ----------

    /** Todo el historial de un jugador, del mas nuevo al mas viejo. */
    public List<Entrada> de(String jugador) {
        List<Entrada> out = new ArrayList<>();
        for (Entrada e : entradas) {
            if (e.jugador.equalsIgnoreCase(jugador)) out.add(e);
        }
        out.sort(Comparator.comparingLong((Entrada e) -> e.cuando).reversed());
        return out;
    }

    /** Las ultimas anotaciones de todo el servidor. */
    public List<Entrada> recientes(int limite) {
        List<Entrada> out = new ArrayList<>(entradas);
        out.sort(Comparator.comparingLong((Entrada e) -> e.cuando).reversed());
        if (out.size() > limite) return new ArrayList<>(out.subList(0, limite));
        return out;
    }

    /** Historial de acciones realizadas por un miembro concreto del staff. */
    public List<Entrada> porStaff(String nombreStaff) {
        if (nombreStaff == null || nombreStaff.isBlank()) return new ArrayList<>();
        return entradas.stream()
                .filter(e -> e.staff != null && e.staff.equalsIgnoreCase(nombreStaff))
                .sorted(Comparator.comparingLong((Entrada e) -> e.cuando).reversed())
                .toList();
    }

    public Entrada ultimo(String tipo, String jugador) {
        List<Entrada> lista = de(jugador);
        for (Entrada e : lista) {
            if (e.tipo.equals(tipo)) return e;
        }
        return null;
    }

    public int total() {
        return entradas.size();
    }

    private void podar() {
        int max = Math.max(100, plugin.config().getInt("historial.max-registros", 2000));
        while (entradas.size() > max) entradas.remove(0);
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        siguienteId = Math.max(1, y.getLong("siguiente-id", 1));
        entradas.clear();
        org.bukkit.configuration.ConfigurationSection seccion = y.getConfigurationSection("entradas");
        if (seccion == null) return;
        long maximo = 0;
        for (String clave : seccion.getKeys(false)) {
            String p = "entradas." + clave + ".";
            Entrada e = new Entrada();
            e.id = y.getLong(p + "id", 0);
            e.tipo = y.getString(p + "tipo", "");
            e.jugador = y.getString(p + "jugador", "");
            e.objetivo = y.getString(p + "objetivo", "");
            e.staff = y.getString(p + "staff", "");
            e.motivo = y.getString(p + "motivo", "");
            e.extra = y.getString(p + "extra", "");
            e.levantada = y.getBoolean(p + "levantada", false);
            e.cuando = y.getLong(p + "cuando", 0);
            if (e.id > 0) {
                entradas.add(e);
                maximo = Math.max(maximo, e.id);
            }
        }
        // El id siguiente nunca puede ser menor que el mayor que hay en el
        // archivo. Si se restaura una copia vieja sin la clave "siguiente-id"
        // (/staffrestaurar), el contador volvia a 1 y las siguientes anotaciones
        // se escribian encima de las que ya habia, perdiendo historial en
        // silencio.
        siguienteId = Math.max(siguienteId, maximo + 1);
        if (!entradas.isEmpty()) {
            plugin.getLogger().info("[historial] " + entradas.size() + " anotaciones cargadas.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente-id", siguienteId);
        for (Entrada e : entradas) {
            String p = "entradas." + e.id + ".";
            y.set(p + "id", e.id);
            y.set(p + "tipo", e.tipo);
            y.set(p + "jugador", e.jugador);
            y.set(p + "objetivo", e.objetivo);
            y.set(p + "staff", e.staff);
            y.set(p + "motivo", e.motivo);
            y.set(p + "extra", e.extra);
            y.set(p + "levantada", e.levantada);
            y.set(p + "cuando", e.cuando);
        }
        almacen.escribir(y);
    }
}
