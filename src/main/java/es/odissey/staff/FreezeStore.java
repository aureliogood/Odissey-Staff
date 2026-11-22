package es.odissey.staff;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El estado de congelado, guardado en disco.
 *
 * Antes vivia solo en memoria: si el servidor se reiniciaba, todos los
 * congelados quedaban libres. Y con una sancion de 30 minutos, reiniciar el
 * server (por un crash o un reinicio manual) era una forma de salir sin
 * penalizacion. Muy facil de usar en tu contra.
 *
 * Ahora se guarda en congelados.yml y se reaplica al entrar. Se guarda tambien
 * en que punto del mundo estaba, para que no aparezca en su base al volver.
 *
 * Si tiene una sancion activa NO se guarda aqui: la sancion ya guarda su sitio
 * y su tiempo, y meter las dos cosas seria tener dos verdades distintas sobre
 * donde esta el jugador.
 */
public final class FreezeStore {

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<UUID, Datos> datos = new HashMap<>();

    public static final class Datos {
        public UUID uuid;
        public String nombre = "";
        public String motivo = "";
        public String por = "";
        public long desde;
        /** Como estaba antes de congelarlo, para devolvérselo igual. */
        public org.bukkit.GameMode gameMode;
        public float walkSpeed = 0.2f;
        public boolean allowFlight;
        public boolean flying;
        public String mundo = "";
        public double x, y, z, yaw, pitch;
        /**
         * El casco que llevaba cuando lo congelaron, ya serializado.
         *
         * Sin esto, congelar a alguien con un casco y que se desconectara le
         * costaba el casco para siempre: al volver, congelar() veia que ya
         * llevaba hielo en la cabeza y no sabia que debajo habia un casco
         * guardado en memoria que ya se habia perdido al salir.
         */
        public String casco = "";
    }

    public FreezeStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("congelados", "congelados.yml");
    }


    public void guardar(StaffManager.Estado e, org.bukkit.entity.Player jugador) {
        if (jugador == null) return;
        if (plugin.sanciones().tiene(jugador.getUniqueId())) return;   // la sancion manda

        Datos d = datos.get(jugador.getUniqueId());
        if (d == null) {
            d = new Datos();
            d.uuid = jugador.getUniqueId();
            datos.put(d.uuid, d);
        }
        d.nombre = jugador.getName();
        d.motivo = e.congeladoMotivo;
        d.por = e.congeladoPor;
        d.desde = e.congeladoDesde;
        d.gameMode = e.gameModePrevio;
        d.walkSpeed = e.walkSpeedPrevio;
        d.allowFlight = e.allowFlightPrevio;
        d.flying = e.flyingPrevio;
        // El casco previo viaja al archivo para que sobreviva a la desconexión.
        d.casco = serializar(e.helmetPrevio);

        Location l = jugador.getLocation();
        if (l != null && l.getWorld() != null) {
            d.mundo = l.getWorld().getName();
            d.x = l.getX();
            d.y = l.getY();
            d.z = l.getZ();
            d.yaw = l.getYaw();
            d.pitch = l.getPitch();
        }
        escribir();
    }

    public void quitar(UUID uuid) {
        if (datos.remove(uuid) != null) escribir();
    }

    public boolean esta(UUID uuid) {
        return datos.containsKey(uuid);
    }

    public Datos datosDe(UUID uuid) {
        return datos.get(uuid);
    }

    public java.util.Collection<Datos> todos() {
        return new java.util.ArrayList<>(datos.values());
    }

    /** Que se le de al jugador al volver a entrar. */
    public Location sitioDe(Datos d) {
        if (d.mundo == null || d.mundo.isEmpty()) return null;
        org.bukkit.World w = plugin.getServer().getWorld(d.mundo);
        if (w == null) return null;
        return new Location(w, d.x, d.y, d.z, (float) d.yaw, (float) d.pitch);
    }

    /**
     * Un item a texto, para meterlo en el yml.
     *
     * Es codificacion Base64 sobre el item entero (con su nombre, sus
     * encantamientos y sus PDC), no solo el material. Con solo el material, un
     * casco con un encantamiento volveria desnudo.
     */
    public static String serializar(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return "";
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (org.bukkit.util.io.BukkitObjectOutputStream out =
                     new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Throwable t) {
            // No es grave: si no se puede guardar, al soltarlo se le pondra un
            // hueco en la cabeza en vez de su casco. Mejor eso que romper el
            // guardado entero del congelado.
            java.util.logging.Logger.getLogger("OdisseyStaff")
                    .fine("[congelados] no se pudo guardar el casco: " + t);
            return "";
        }
    }

    /** De texto a item. Si falla, null (que es "no llevaba nada"). */
    public static org.bukkit.inventory.ItemStack deserializar(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        try {
            byte[] crudo = java.util.Base64.getDecoder().decode(texto);
            try (org.bukkit.util.io.BukkitObjectInputStream in =
                         new org.bukkit.util.io.BukkitObjectInputStream(
                                 new java.io.ByteArrayInputStream(crudo))) {
                return (org.bukkit.inventory.ItemStack) in.readObject();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        datos.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("congelados");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            String p = "congelados." + clave + ".";
            try {
                Datos d = new Datos();
                d.uuid = UUID.fromString(clave);
                d.nombre = y.getString(p + "nombre", "");
                d.motivo = y.getString(p + "motivo", "");
                d.por = y.getString(p + "por", "");
                d.desde = y.getLong(p + "desde", 0);
                String gm = y.getString(p + "gameMode", "SURVIVAL");
                try {
                    d.gameMode = org.bukkit.GameMode.valueOf(gm);
                } catch (IllegalArgumentException e) {
                    d.gameMode = org.bukkit.GameMode.SURVIVAL;
                }
                d.walkSpeed = (float) y.getDouble(p + "walkSpeed", 0.2);
                d.allowFlight = y.getBoolean(p + "allowFlight", false);
                d.flying = y.getBoolean(p + "flying", false);
d.mundo = y.getString(p + "mundo", "");
            d.x = y.getDouble(p + "x", 0);
            d.y = y.getDouble(p + "y", 0);
            d.z = y.getDouble(p + "z", 0);
            d.yaw = y.getDouble(p + "yaw", 0);
            d.pitch = y.getDouble(p + "pitch", 0);
            d.casco = y.getString(p + "casco", "");
                datos.put(d.uuid, d);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[congelados] entrada invalida ignorada: " + clave);
            }
        }
        if (!datos.isEmpty()) {
            plugin.getLogger().info("[congelados] " + datos.size()
                    + " jugador(es) congelados restaurados tras el reinicio.");
            for (Datos d : datos.values()) {
                // La cuenta va hacia adelante: "desde" es un momento del pasado,
                // asi que es ahora - desde. Al reves salia siempre "0 segundos".
                long lleva = Math.max(0L, System.currentTimeMillis() - d.desde);
                plugin.getLogger().info("  · " + d.nombre + " (" + SancionStore.enLetras(lleva)
                        + " desde que se congeló)");
            }
        }
    }

    public void escribir() {
        YamlConfiguration y = new YamlConfiguration();
        for (Datos d : datos.values()) {
            String p = "congelados." + d.uuid + ".";
            y.set(p + "nombre", d.nombre);
            y.set(p + "motivo", d.motivo);
            y.set(p + "por", d.por);
            y.set(p + "desde", d.desde);
            y.set(p + "gameMode", d.gameMode == null ? "SURVIVAL" : d.gameMode.name());
            y.set(p + "walkSpeed", d.walkSpeed);
            y.set(p + "allowFlight", d.allowFlight);
            y.set(p + "flying", d.flying);
            y.set(p + "mundo", d.mundo);
            y.set(p + "x", d.x);
            y.set(p + "y", d.y);
            y.set(p + "z", d.z);
            y.set(p + "yaw", d.yaw);
            y.set(p + "pitch", d.pitch);
            y.set(p + "casco", d.casco == null ? "" : d.casco);
        }
        almacen.escribir(y);
    }
}
