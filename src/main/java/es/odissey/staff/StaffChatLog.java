package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Historial del chat de staff.
 *
 * Lo que se dice por /staffchat se perdia en cuanto se cerraba el Minecraft. Y
 * hay mensajes que importan de verdad: "dejame el server un dia y te lo
 * devuelvo", "eso ya lo habia arreglado", "no es la primera vez que lo hace".
 *
 * Con esto se puede buscar lo que dijo alguien sobre un jugador, con fecha, en
 * vez de quedarse con la palabra del staff de turno.
 *
 * Se corta por antiguedad: un chat de staff llena mucho y de lo viejo no
 * sirve para nada.
 */
public final class StaffChatLog {

    public static final class Linea {
        public String autor = "";
        public String texto = "";
        public long cuando;
    }


    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final List<Linea> lineas = new ArrayList<>();

    public StaffChatLog(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("staffchat", "staffchat.yml");
    }


    public void anotar(String autor, String texto) {
        if (!plugin.config().getBoolean("staff-chat.guardar-historial", true)) return;
        Linea l = new Linea();
        l.autor = autor == null ? "" : autor;
        l.texto = texto == null ? "" : texto;
        l.cuando = System.currentTimeMillis();
        lineas.add(l);
        podar();
    }

    /**
     * Las ultimas lineas, opcionalmente solo las de un autor.
     *
     * Buscar por texto tambien: /staffchatlog Rob es por autor, pero si
     * escribes "buscar palabras" encuentra esas palabras en lo que dijo el
     * staff sobre ese jugador.
     */
    public List<Linea> buscar(String filtro, int limite) {
        List<Linea> salida = new ArrayList<>();
        String f = filtro == null ? "" : filtro.toLowerCase(java.util.Locale.ROOT);
        for (int i = lineas.size() - 1; i >= 0 && salida.size() < limite; i--) {
            Linea l = lineas.get(i);
            if (f.isEmpty()
                    || l.autor.toLowerCase(java.util.Locale.ROOT).contains(f)
                    || l.texto.toLowerCase(java.util.Locale.ROOT).contains(f)) {
                salida.add(l);
            }
        }
        return salida;
    }

    public int total() {
        return lineas.size();
    }

    private void podar() {
        int max = Math.max(50, plugin.config().getInt("staff-chat.max-lineas", 1000));
        while (lineas.size() > max) lineas.remove(0);
        int dias = Math.max(1, plugin.config().getInt("staff-chat.dias-guardados", 30));
        long limite = System.currentTimeMillis() - dias * 24L * 60L * 60L * 1000L;
        lineas.removeIf(l -> l.cuando < limite);
    }

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        lineas.clear();
        int n = y.getInt("cuantas", 0);
        for (int i = 1; i <= n; i++) {
            Linea l = new Linea();
            l.autor = y.getString("lineas." + i + ".autor", "");
            l.texto = y.getString("lineas." + i + ".texto", "");
            l.cuando = y.getLong("lineas." + i + ".cuando", 0);
            if (!l.texto.isEmpty()) lineas.add(l);
        }
        if (!lineas.isEmpty()) {
            plugin.getLogger().info("[staffchat] " + lineas.size() + " mensajes cargados.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cuantas", lineas.size());
        int i = 1;
        for (Linea l : lineas) {
            y.set("lineas." + i + ".autor", l.autor);
            y.set("lineas." + i + ".texto", l.texto);
            y.set("lineas." + i + ".cuando", l.cuando);
            i++;
        }
        almacen.escribir(y);
    }

    public static String fecha(long ms) {
        return Fechas.diaHora(ms);
    }
}
