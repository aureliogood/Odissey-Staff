package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Silencios con caducidad. Se guardan en un archivo aparte para que un silencio
 * temporal se acabe solo aunque el servidor se reinicie, y para que el staff
 * reciba el aviso cuando se le acaba.
 */
public final class MuteStore {

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<UUID, Registro> registros = new ConcurrentHashMap<>();
    private final Map<String, Registro> registrosIp = new ConcurrentHashMap<>();
    /** Silencios que se acabaron con el jugador desconectado: se le avisa al entrar. */
    private final Map<String, String> caducadosPendientes = new ConcurrentHashMap<>();

    public MuteStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("silencios", "silencios.yml");
    }

    public static final class Registro {
        public UUID uuid;
        public String nombre = "";
        public String motivo = "";
        public long hasta;   // Long.MAX_VALUE = permanente
        public long puesto;
        public boolean fantasma;
        public String ip = "";
    }

    public void cargar() {
        YamlConfiguration y = (YamlConfiguration) almacen.leer();
        registros.clear();
        registrosIp.clear();
        caducadosPendientes.clear();
        for (String clave : y.getKeys(false)) {
            // "caducados-pendientes" es una lista al mismo nivel que las UUID.
            // Sin saltarla, UUID.fromString() petaba con esa clave y en cada
            // arranque aparecia un "entrada invalida ignorada" que no era tal.
            if (clave.equals("caducados-pendientes") || clave.equals("ips")) continue;
            try {
                Registro r = new Registro();
                r.uuid = UUID.fromString(clave);
                r.nombre = y.getString(clave + ".nombre", "");
                r.motivo = y.getString(clave + ".motivo", "");
                r.hasta = y.getLong(clave + ".hasta", Long.MAX_VALUE);
                r.puesto = y.getLong(clave + ".puesto", 0);
                r.fantasma = y.getBoolean(clave + ".fantasma", false);
                registros.put(r.uuid, r);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[silencios] entrada invalida ignorada: " + clave);
            }
        }
        for (String clave : y.getStringList("caducados-pendientes")) {
            int corte = clave.indexOf(':');
            if (corte <= 0) continue;
            caducadosPendientes.put(clave.substring(0, corte), clave.substring(corte + 1));
        }
        var ips = y.getConfigurationSection("ips");
        if (ips != null) {
            for (String clave : ips.getKeys(false)) {
                String p = "ips." + clave + ".";
                String ip = y.getString(p + "ip", "");
                if (ip.isBlank()) continue;
                Registro r = new Registro();
                r.ip = ip;
                r.nombre = "IP " + ip;
                r.motivo = y.getString(p + "motivo", "");
                r.hasta = y.getLong(p + "hasta", Long.MAX_VALUE);
                r.puesto = y.getLong(p + "puesto", 0);
                r.fantasma = y.getBoolean(p + "fantasma", false);
                registrosIp.put(ip, r);
            }
        }
        limpiarCaducados();
    }

    private final Object candado = new Object();

    public void guardar() {
        // El chat de Bukkit es asincrono y silenciado() se llama desde ahi. Dos
        // escrituras a la vez del mismo almacen lo dejaban a medias, asi que el
        // guardado se serializa.
        synchronized (candado) {
            YamlConfiguration y = new YamlConfiguration();
            List<String> pendientes = new ArrayList<>();
            for (Map.Entry<String, String> e : caducadosPendientes.entrySet()) {
                pendientes.add(e.getKey() + ":" + e.getValue());
            }
            y.set("caducados-pendientes", pendientes);
            for (Registro r : registros.values()) {
                String c = r.uuid.toString();
                y.set(c + ".nombre", r.nombre);
                y.set(c + ".motivo", r.motivo);
                y.set(c + ".hasta", r.hasta);
                y.set(c + ".puesto", r.puesto);
                y.set(c + ".fantasma", r.fantasma);
            }
            for (Registro r : registrosIp.values()) {
                String c = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(r.ip.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String p = "ips." + c + ".";
                y.set(p + "ip", r.ip);
                y.set(p + "nombre", r.nombre);
                y.set(p + "motivo", r.motivo);
                y.set(p + "hasta", r.hasta);
                y.set(p + "puesto", r.puesto);
                y.set(p + "fantasma", r.fantasma);
            }
            almacen.escribir(y);
        }
    }


    public void poner(UUID uuid, String nombre, String motivo, long hasta) {
        poner(uuid, nombre, motivo, hasta, false);
    }

    public void poner(UUID uuid, String nombre, String motivo, long hasta, boolean fantasma) {
        Registro r = new Registro();
        r.uuid = uuid;
        r.nombre = nombre;
        r.motivo = motivo;
        r.hasta = hasta;
        r.puesto = System.currentTimeMillis();
        r.fantasma = fantasma;
        registros.put(uuid, r);
        guardar();
    }

    public void quitar(UUID uuid) {
        if (registros.remove(uuid) != null) guardar();
    }

    public void ponerIp(String ip, String motivo, long hasta, boolean fantasma) {
        Registro r = new Registro();
        r.ip = ip;
        r.nombre = "IP " + ip;
        r.motivo = motivo;
        r.hasta = hasta;
        r.puesto = System.currentTimeMillis();
        r.fantasma = fantasma;
        registrosIp.put(ip, r);
        guardar();
    }

    public void quitarIp(String ip) {
        if (ip != null && registrosIp.remove(ip) != null) guardar();
    }

    public Registro registroIp(String ip) { return ip == null ? null : registrosIp.get(ip); }

    public boolean silenciadoIp(String ip) {
        Registro r = registroIp(ip);
        if (r == null) return false;
        if (r.hasta <= System.currentTimeMillis()) {
            if (registrosIp.remove(ip, r)) {
                try { plugin.getServer().getScheduler().runTask(plugin, this::guardar); }
                catch (IllegalPluginAccessException | IllegalStateException ignorado) { }
            }
            return false;
        }
        return true;
    }

    public boolean silenciado(UUID uuid) {
        Registro r = registros.get(uuid);
        if (r == null) return false;
        if (r.hasta <= System.currentTimeMillis()) {
            // Esta comprobacion se hace en el hilo del chat (Bukkit dispara el
            // evento de escritura fuera del principal), y escribir aqui el
            // silencios.yml bloqueaba el chat y podia pisar el guardado que
            // estuviera haciendo el hilo principal a la vez. Se borra de memoria
            // y el guardado se encola; si el servidor muere antes, al arrancar
            // limpiarCaducados() vuelve a quitar este mismo silencio ya caducado.
            if (registros.remove(uuid) != null) {
                try {
                    plugin.getServer().getScheduler().runTask(plugin, this::guardar);
                } catch (IllegalPluginAccessException | IllegalStateException ignorado) {
                    // el plugin se esta apagando: guardar() lo llama onDisable
                }
            }
            return false;
        }
        return true;
    }

    public Registro registro(UUID uuid) {
        return registros.get(uuid);
    }

    public List<Registro> todos() {
        List<Registro> lista = new ArrayList<>(registros.values());
        lista.removeIf(r -> r.hasta <= System.currentTimeMillis());
        lista.addAll(registrosIp.values());
        lista.removeIf(r -> r.hasta <= System.currentTimeMillis());
        return lista;
    }

    /**
     * Avisa a quien se quedaba sin saber que se le acabo el silencio.
     *
     * Antes solo se avisaba al staff que estuviera conectado JUSTO en el segundo
     * de la expiracion, que casi nunca pasa. Si el silencio se acaba con el
     * jugador desconectado, no se le dice nada y sigue pensando que esta mudo.
     * Por eso se guardan los nombres de los caducados, y al entrar se le avisa.
     */
    public void limpiarCaducados() {
        long ahora = System.currentTimeMillis();
        boolean cambio = false;
        for (Map.Entry<UUID, Registro> e : registros.entrySet()) {
            if (e.getValue().hasta > ahora) continue;
            Registro r = e.getValue();
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) {
                p.sendMessage(Text.exito("Ya no estás silenciado."));
                plugin.getLogger().info("[silencios] se acabo el silencio de " + r.nombre);
            } else {
                // Se guarda el aviso para cuando entre.
                caducadosPendientes.put(r.uuid.toString(), r.nombre);
                plugin.getLogger().info("[silencios] se acabo el silencio de " + r.nombre + " (offline)");
            }
            registros.remove(e.getKey());
            cambio = true;
        }
        if (cambio) guardar();
    }

    /** Al entrar, si se le acabo un silencio mientras no estaba, se le avisa. */
    public void avisarAlEntrar(org.bukkit.entity.Player jugador) {
        String nombre = caducadosPendientes.remove(jugador.getUniqueId().toString());
        if (nombre == null) return;
        jugador.sendMessage(Text.exito("Tu silencio ya habia terminado."));
        guardar();
    }
}
