package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Registro de entradas y salidas de cada jugador.
 *
 * Para /staffonline: "estuvo conectado a las 3 de la madrugada" es la clase de
 * pregunta que sale cuando alguien rompe algo de noche y nadie sabe por que.
 * Essentials tiene /seen de un jugador, pero no te dice quien estaba a la vez.
 *
 * De aqui salen tambien dos cosas del /staffalt: las horas jugadas hoy y el
 * indice de IP, para buscar cuentas alternativas. En un servidor con
 * online-mode=false casi todo el mundo entra por IP, asi que eso filtra mucho.
 */
public final class ConexionStore {

    public static final class Sesion {
        public String nombre = "";
        public java.util.UUID uuid;
        public String ip = "";
        public long entro;
        public long salio;   // 0 = sigue dentro
    }


    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final List<Sesion> sesiones = new ArrayList<>();

    /**
     * Cuando entro en su sesion actual, o 0 si no hay ninguna abierta.
     *
     * Lo usa la ficha de jugador para decir "lleva aqui 1h 20m". Se mira la ultima
     * sesion sin cerrar, porque una cuenta puede tener muchas viejas guardadas.
     */
    public long entroDe(String nombre) {
        for (int i = sesiones.size() - 1; i >= 0; i--) {
            Sesion s = sesiones.get(i);
            if (s.salio == 0 && s.nombre.equalsIgnoreCase(nombre)) return s.entro;
        }
        return 0L;
    }
    /** IP -> nombres que han entrado con ella. Base para el anti-alt. */
    private final Map<String, java.util.Set<String>> porIp = new HashMap<>();

    public ConexionStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("conexiones", "conexiones.yml");
    }


    public void entrar(String nombre, java.util.UUID uuid, String ip) {
        Sesion s = new Sesion();
        s.nombre = nombre;
        s.uuid = uuid;
        s.ip = ip == null ? "" : ip;
        s.entro = System.currentTimeMillis();
        sesiones.add(s);
        if (!s.ip.isEmpty() && !"127.0.0.1".equals(s.ip)) {
            porIp.computeIfAbsent(s.ip, k -> new java.util.LinkedHashSet<>()).add(nombre);
        }
        podar();
    }

    public void salir(java.util.UUID uuid) {
        long ahora = System.currentTimeMillis();
        // Se cierra la ultima sesion abierta de ese jugador.
        for (int i = sesiones.size() - 1; i >= 0; i--) {
            Sesion s = sesiones.get(i);
            if (s.salio == 0 && s.uuid != null && s.uuid.equals(uuid)) {
                s.salio = ahora;
                break;
            }
        }
    }

    /** Quien ha estado conectado en las ultimas X horas, agrupado por jugador. */
    public Map<String, List<Sesion>> enLasUltimas(int horas) {
        long desde = System.currentTimeMillis() - horas * 3600_000L;
          Map<String, List<Sesion>> fuera = new LinkedHashMap<>();
          List<Sesion> ordenadas = new ArrayList<>(sesiones);
          // comparingLong y no restar: "b.entro - a.entro" devuelve int, y el
          // comparator pide long, asi que no compila.
          ordenadas.sort(Comparator.comparingLong((Sesion s) -> s.entro).reversed());
          for (Sesion s : ordenadas) {
            if (s.entro < desde) continue;
            fuera.computeIfAbsent(s.nombre, k -> new ArrayList<>()).add(s);
        }
        return fuera;
    }

    /** Milisegundos jugados hoy por ese jugador. */
    public long jugadosHoy(String nombre) {
        long desde = hoyDesde();
        long total = 0;
        for (Sesion s : sesiones) {
            if (!s.nombre.equalsIgnoreCase(nombre)) continue;
            long fin = s.salio == 0 ? System.currentTimeMillis() : s.salio;
            long a = Math.max(s.entro, desde);
            long b = Math.max(fin, a);
            total += (b - a);
        }
        return total;
    }

    private long hoyDesde() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** La ultima IP vista de ese jugador. */
    public String ultimaIp(String nombre) {
        for (int i = sesiones.size() - 1; i >= 0; i--) {
            Sesion s = sesiones.get(i);
            if (s.nombre.equalsIgnoreCase(nombre) && !s.ip.isEmpty()) return s.ip;
        }
        return "";
    }

    /** UUID conocido de una cuenta, o null si nunca quedó registrada. */
    public java.util.UUID uuidDe(String nombre) {
        if (nombre == null) return null;
        for (int i = sesiones.size() - 1; i >= 0; i--) {
            Sesion s = sesiones.get(i);
            if (s.nombre.equalsIgnoreCase(nombre) && s.uuid != null) return s.uuid;
        }
        return null;
    }

    /** Otros nombres que han entrado con la misma IP. */
    public List<String> mismaIp(String ip, String excepto) {
        List<String> fuera = new ArrayList<>();
        if (ip == null || ip.isEmpty()) return fuera;
        java.util.Set<String> set = porIp.get(ip);
        if (set == null) return fuera;
        for (String n : set) {
            if (!n.equalsIgnoreCase(excepto)) fuera.add(n);
        }
        return fuera;
    }

    /**
     * Nombres parecidos al dado.
     *
     * Busca dos cosas: que solo se parezcan en un par de letras (OscarMod y
     * OscarMad), y que compartan principio y final, que es como se hacen las
     * cuentas de reserva.
     */
    public List<String> nombresParecidos(String nombre) {
        List<String> fuera = new ArrayList<>();
        String n = nombre == null ? "" : nombre.toLowerCase(java.util.Locale.ROOT);
        if (n.isEmpty()) return fuera;
        java.util.Set<String> nombres = new java.util.LinkedHashSet<>();
        for (Sesion s : sesiones) {
            if (!s.nombre.equalsIgnoreCase(nombre)) nombres.add(s.nombre);
        }
        for (String otro : nombres) {
            String o = otro.toLowerCase(java.util.Locale.ROOT);
            if (o.length() == n.length() && distancia(n, o) <= 2) fuera.add(otro);
            else if (o.length() >= 4 && n.length() >= 4
                    && o.substring(0, 3).equals(n.substring(0, 3))
                    && o.substring(o.length() - 2).equals(n.substring(n.length() - 2))) fuera.add(otro);
        }
        return fuera;
    }

    /** Levenshtein, para comparar nombres. */
    static int distancia(String a, String b) {
        int[] previo = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previo[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int coste = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previo[j] + 1), previo[j - 1] + coste);
            }
            int[] cambio = previo;
            previo = actual;
            actual = cambio;
        }
        return previo[b.length()];
    }

    private void podar() {
        int horas = Math.max(1, plugin.config().getInt("conexiones.horas-guardadas", 72));
        long limite = System.currentTimeMillis() - horas * 3600_000L;
        sesiones.removeIf(s -> s.entro < limite && s.salio != 0);
        int max = Math.max(200, plugin.config().getInt("conexiones.max-sesiones", 5000));
        while (sesiones.size() > max) sesiones.remove(0);
    }

    public static String hora(long ms) {
        return Fechas.diaHora(ms);
    }

    /** "2h 15m" a partir de milisegundos. */
    public static String bonito(long ms) {
        long min = ms / 60000L;
        long h = min / 60;
        long m = min % 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        sesiones.clear();
        porIp.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("sesiones");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            String p = "sesiones." + clave + ".";
            Sesion ses = new Sesion();
            ses.nombre = y.getString(p + "nombre", "");
            String u = y.getString(p + "uuid", "");
            try {
                ses.uuid = u.isEmpty() ? null : java.util.UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                ses.uuid = null;
            }
            ses.ip = y.getString(p + "ip", "");
            ses.entro = y.getLong(p + "entro", 0);
            ses.salio = y.getLong(p + "salio", 0);
            if (ses.nombre.isEmpty()) continue;
            sesiones.add(ses);
            if (!ses.ip.isEmpty() && !"127.0.0.1".equals(ses.ip)) {
                porIp.computeIfAbsent(ses.ip, k -> new java.util.LinkedHashSet<>()).add(ses.nombre);
            }
        }
        podar();
        plugin.getLogger().info("[conexiones] " + sesiones.size() + " sesiones cargadas.");
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cuantas", sesiones.size());
        for (int i = 0; i < sesiones.size(); i++) {
            Sesion s = sesiones.get(i);
            String p = "sesiones." + i + ".";
            y.set(p + "nombre", s.nombre);
            y.set(p + "uuid", s.uuid == null ? "" : s.uuid.toString());
            y.set(p + "ip", s.ip);
            y.set(p + "entro", s.entro);
            y.set(p + "salio", s.salio);
        }
        almacen.escribir(y);
    }
}
