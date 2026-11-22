package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lo que ha dicho cada jugador, guardado en disco.
 *
 * ActividadStore guarda el chat, pero solo en memoria y por poco rato: sirve
 * para /staffsnapshot, no para mirar una queja de ayer.
 *
 * Esto es lo otro: un historial del chat por jugador que sobrevive al
 * reinicio, para poder responder a "me ha toxiqueado" o a "que dijo antes de
 * que le baneaseis". Se lee con /staffchatlog &lt;jugador&gt;.
 *
 * Solo se guarda lo que dice la gente de verdad. El staff ya tiene su propio
 * registro en StaffChatLog, y un administrador no deberia salir anotado aqui
 * cada vez que habla por su radio.
 *
 * Se poda por numero de lineas y por antiguedad, porque un jugador que escribe
 * mucho llenaria el archivo en un dia.
 *
 * OJO: el chat es un evento asincrono. Aqui no se toca nada de Bukkit, solo
 * listas propias. El guardado se hace desde el hilo principal.
 */
public final class ChatJugadoresLog {

    public static final class Linea {
        public String texto = "";
        public long cuando;
    }

    private static final java.time.format.DateTimeFormatter FECHA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    /** Jugadores en el orden en el que se han escrito. */
    private final Map<String, List<Linea>> porJugador = new java.util.concurrent.ConcurrentHashMap<>();

    /** Los ultimos mensajes de cada uno, para pillar el spam al vuelo. */
    private final Map<String, List<String>> recientes = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cuando se dijo cada mensaje reciente, en el mismo orden que "recientes". */
    private final Map<String, List<Long>> recientesMs = new java.util.concurrent.ConcurrentHashMap<>();

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private volatile boolean sucio;

    public ChatJugadoresLog(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("chat", "chat.yml");
    }


    /** Cuantos mensajes recientes se guardan para detectar spam. */
    public int ventana() {
        // Es la misma ventana que usa el antispam: lo que se guarda aqui es
        // para mirar si el ultimo mensaje ya se ha repetido.
        return Math.max(3, Math.min(20,
                plugin.config().getInt("antispam.ventana-mensajes", 10)));
    }

    public void anotar(String jugador, String texto) {
        if (jugador == null || texto == null || texto.isEmpty()) return;

        // La ventana de los ultimos mensajes se rellena SIEMPRE, antes de mirar
        // si el chat se guarda en disco. Antes el "chat.guardar: false" hacia un
        // return antes de llegar aqui, y esa ventana es lo unico que lee el
        // antid-spam: con la configuracion que se pone para no guardar datos
        // personales, el antid-spam se quedaba sin fuente y no detectaba nada.
        List<String> ultimos = recientes.computeIfAbsent(jugador, k -> new ArrayList<>());
        List<Long> tiempos = recientesMs.computeIfAbsent(jugador, k -> new ArrayList<>());
        synchronized (ultimos) {
            ultimos.add(texto.toLowerCase(Locale.ROOT));
            while (ultimos.size() > ventana()) ultimos.remove(0);
        }
        synchronized (tiempos) {
            tiempos.add(System.currentTimeMillis());
            while (tiempos.size() > ventana()) tiempos.remove(0);
        }
        sucio = true;

        if (!plugin.config().getBoolean("chat.guardar", true)) return;

        Linea l = new Linea();
        l.texto = texto.length() > 200 ? texto.substring(0, 200) : texto;
        l.cuando = System.currentTimeMillis();

        List<Linea> lista = porJugador.computeIfAbsent(jugador, k -> new ArrayList<>());
        synchronized (lista) {
            lista.add(l);
            podar(lista);
        }
    }

    private void podar(List<Linea> lista) {
        int max = Math.max(50, plugin.config().getInt("chat.max-lineas-por-jugador", 300));
        while (lista.size() > max) lista.remove(0);
        int dias = Math.max(1, plugin.config().getInt("chat.dias-guardados", 14));
        long limite = System.currentTimeMillis() - dias * 24L * 60L * 60L * 1000L;
        lista.removeIf(x -> x.cuando < limite);
    }

    /**
     * Lo que dijo un jugador.
     *
     * @param minutos solo estos ultimos minutos; 0 o menos = todo lo guardado
     * @param palabra si no esta vacia, solo las lineas que la contengan
     */
    public List<Linea> de(String jugador, int minutos, String palabra, int limite) {
        List<Linea> salida = new ArrayList<>();
        List<Linea> lista = porJugador.get(jugador);
        if (lista == null) return salida;
        String f = palabra == null ? "" : palabra.toLowerCase(Locale.ROOT);
        long desde = minutos > 0 ? System.currentTimeMillis() - minutos * 60_000L : 0L;

        synchronized (lista) {
            for (int i = lista.size() - 1; i >= 0 && salida.size() < limite; i--) {
                Linea l = lista.get(i);
                if (l.cuando < desde) continue;
                if (!f.isEmpty() && !l.texto.toLowerCase(Locale.ROOT).contains(f)) continue;
                salida.add(l);
            }
        }
        return salida;
    }

    /** Los ultimos mensajes tal cual los dijo, para el detector de spam. */
    public List<String> ultimos(String jugador) {
        List<String> l = recientes.get(jugador);
        if (l == null) return Collections.emptyList();
        synchronized (l) {
            return new ArrayList<>(l);
        }
    }

    /**
     * Cuantos de los ultimos mensajes de un jugador se dijeron en los ultimos
     * "segundos".
     *
     * El antispam de rafagas lo usa para no silenciar solo por haber escrito
     * muchos mensajes: mira tambien CUANDO se escribieron. Antes se contaban
     * los mensajes de la ventana sin tiempo, y como la ventana nunca se vacia,
     * a partir del mensaje numero "ventana" todo el mundo salia silenciado.
     */
    public int enRafaga(String jugador, int segundos) {
        List<Long> l = recientesMs.get(jugador);
        if (l == null) return 0;
        long desde = System.currentTimeMillis() - Math.max(1, segundos) * 1000L;
        synchronized (l) {
            int n = 0;
            for (long t : l) {
                if (t >= desde) n++;
            }
            return n;
        }
    }

    public boolean hayMensajesDe(String jugador) {
        List<Linea> l = porJugador.get(jugador);
        if (l == null) return false;
        synchronized (l) {
            return !l.isEmpty();
        }
    }

    public void olvidar(String jugador) {
        porJugador.remove(jugador);
        recientes.remove(jugador);
        recientesMs.remove(jugador);
        sucio = true;
    }

    public boolean hayCambios() {
        return sucio;
    }

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        porJugador.clear();
        recientes.clear();
        long limiteDias = Math.max(1, plugin.config().getInt("chat.dias-guardados", 14));
        long limite = System.currentTimeMillis() - limiteDias * 24L * 60L * 60L * 1000L;

        // Se recorre la seccion, no un contador. Con el contador habia que
        // acertar el numero de jugadores a mano y cualquier desajuste hacia
        // perder silenciosamente el historial entero.
        org.bukkit.configuration.ConfigurationSection seccion = y.getConfigurationSection("jugadores");
        if (seccion != null) {
            for (String clave : seccion.getKeys(false)) {
                String base = "jugadores." + clave + ".";
                String nombre = y.getString(base + "nombre", "");
                if (nombre.isEmpty()) continue;
                int n = y.getInt(base + "total", 0);
                if (n <= 0) continue;   // seccion "lineas" rota o de un archivo viejo
                List<Linea> lista = new ArrayList<>();
                for (int j = 1; j <= n; j++) {
                    Linea l = new Linea();
                    l.texto = y.getString(base + "lineas." + j + ".texto", "");
                    l.cuando = y.getLong(base + "lineas." + j + ".cuando", 0);
                    if (!l.texto.isEmpty() && l.cuando >= limite) lista.add(l);
                }
                if (!lista.isEmpty()) porJugador.put(nombre, lista);
            }
        }
        sucio = false;
        if (!porJugador.isEmpty()) {
            plugin.getLogger().info("[chat] " + porJugador.size() + " jugadores con historial.");
        }
    }

    public void guardar() {
        if (!sucio) return;
        YamlConfiguration y = new YamlConfiguration();
        Map<String, List<Linea>> foto = new LinkedHashMap<>(porJugador);
        int i = 1;
        for (Map.Entry<String, List<Linea>> e : foto.entrySet()) {
            List<Linea> lista = e.getValue();
            int n = 0;
            synchronized (lista) {
                for (Linea l : lista) {
                    n++;
                    y.set("jugadores." + i + ".lineas." + n + ".texto", l.texto);
                    y.set("jugadores." + i + ".lineas." + n + ".cuando", l.cuando);
                }
            }
            // El numero de lineas va en "total", NO en "lineas": antes se
            // escribia "lineas: <numero>" encima de las lineas que se acababan
            // de escribir, lo cual reemplazaba la seccion entera por un entero.
            // Al releer, "lineas" ya no era una lista y se perdia todo el
            // historial de chat en el primer guardado.
            y.set("jugadores." + i + ".nombre", e.getKey());
            y.set("jugadores." + i + ".total", n);
            i++;
        }
        // El numero de jugadores va en "cuantos" y NO en "jugadores". Antes se
        // escribia y.set("jugadores", i - 1) aqui al final, y eso BORRABA todo
        // lo de dentro: en Bukkit, poner un numero donde ya hay una seccion se
        // la come. El archivo resultante era literalmente "jugadores: 3" y el
        // historial de chat no se guardaba nunca. Se ha comprobado ejecutando
        // el guardado de verdad, no leyendo el codigo.
        y.set("cuantos", i - 1);
        try {
            almacen.escribir(y);
            sucio = false;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[chat] no se pudo guardar: " + e.getMessage());
        }
    }

    public static String fecha(long ms) {
        return FECHA.format(java.time.Instant.ofEpochMilli(ms));
    }
}
