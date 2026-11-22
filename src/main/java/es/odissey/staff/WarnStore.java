package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avisos (warns) que se acumulan y acaban en algo automatico.
 *
 * Un aviso suelto no hace nada: el staff avisa, el jugador sigue igual y a la
 * cuarta vez ya nadie se cree que avisaron. Lo que hace esto es que al llegar a
 * un numero de avisos se dispare solo una accion: un silencio, un ban, lo que
 * se haya puesto en el config.
 *
 * Los umbrales son datos, no codigo. En config.yml:
 *
 *   warns:
 *     activo: true
 *     caducan-en-dias: 90
 *     niveles:
 *       - avisos: 3
 *         accion: mute      # aviso | mute | ban
 *         tiempo: 1h        # solo para mute y ban
 *         motivo: "..."     # si falta, se usa uno por defecto
 *
 * Asi el servidor cambia de politica sin tocar el plugin ni recompilar, que es
 * como tiene que ser: los numeros concretos dependen de cada comunidad.
 *
 * Los avisos CADUCAN. Un jugador que hace dos años que cumple y tiene un aviso
 * viejo no debe arrastrarlo para siempre, o el sistema se acaba quejandose
 * solo y el staff deja de mirarlo. Por eso `caducan-en-dias`.
 */
public final class WarnStore {

    /** Un aviso concreto. */
    public static final class Aviso {
        public long id;
        public String jugador = "";
        public UUID uuid;
        public String staff = "";
        public String motivo = "";
        public long cuando;
        public boolean retirado;
        public String retiradoPor = "";
    }

    /** Un escalon de la escalera. */
    public static final class Nivel {
        public int avisos;
        public String accion = "";
        public String tiempo = "";
        public String motivo = "";
    }

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final List<Aviso> avisos = new ArrayList<>();
    private long siguienteId = 1;

    public WarnStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("avisos", "warns.yml");
    }

    /**
     * Comprueba al arrancar que la configuracion de avisos se entiende.
     *
     * Este metodo existe por un fallo real: los escalones estaban escritos como
     * una LISTA de yaml (con el "-" delante) y el codigo los leia con
     * getConfigurationSection(), que sobre una lista devuelve null sin decir
     * nada. El plugin arrancaba, /staffwarn contaba avisos con normalidad y no
     * disparaba nunca una sola accion. Sin este aviso en consola, un fallo asi
     * no se descubre en semanas.
     *
     * Ahora, si no hay escalones o estan mal escritos, se dice al arrancar.
     */
    public void avisarSiEstaMalConfigurado() {
        if (!activo()) {
            plugin.getLogger().info("[warns] desactivado (warns.activo: false).");
            return;
        }
        List<Nivel> niveles = niveles();
        if (niveles.isEmpty()) {
            plugin.getLogger().warning("╔══════════════════════════════════════════╗");
            plugin.getLogger().warning("║ [warns] NO HAY ESCALONES DEFINIDOS.          ║");
            plugin.getLogger().warning("║ /staffwarn contará avisos pero no hará      ║");
            plugin.getLogger().warning("║ NADA automático. Revisa 'warns.niveles'     ║");
            plugin.getLogger().warning("║ en config.yml.                             ║");
            plugin.getLogger().warning("╚══════════════════════════════════════════╝");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Nivel n : niveles) {
            sb.append(n.avisos).append(" → ").append(n.accion);
            if (!n.tiempo.isEmpty()) sb.append(' ').append(n.tiempo);
            sb.append("; ");
        }
        plugin.getLogger().info("[warns] escalones: " + sb.toString().trim());
    }

    public boolean activo() {
        return plugin.config().getBoolean("warns.activo", true);
    }

    // ---------------- Los avisos ----------------

    /** Anota un aviso. No aplica nada: eso lo decide el que llama. */
    public Aviso anotar(String jugador, UUID uuid, String staff, String motivo) {
        Aviso a = new Aviso();
        a.id = siguienteId++;
        a.jugador = jugador == null ? "" : jugador;
        a.uuid = uuid;
        a.staff = staff == null ? "" : staff;
        a.motivo = motivo == null ? "" : motivo;
        a.cuando = System.currentTimeMillis();
        avisos.add(a);
        podar();
        guardar();
        return a;
    }

    /** Los avisos vivos de un jugador: los que no han caducado ni se han retirado. */
    public List<Aviso> de(String jugador) {
        List<Aviso> fuera = new ArrayList<>();
        long limite = limiteCaducidad();
        for (Aviso a : avisos) {
            if (!a.jugador.equalsIgnoreCase(jugador)) continue;
            if (a.retirado) continue;
            if (a.cuando < limite) continue;
            fuera.add(a);
        }
        fuera.sort((x, y) -> Long.compare(y.cuando, x.cuando));
        return fuera;
    }

    /** Todos, incluidos los caducados y retirados: para el historial. */
    public List<Aviso> todosDe(String jugador) {
        List<Aviso> fuera = new ArrayList<>();
        for (Aviso a : avisos) {
            if (a.jugador.equalsIgnoreCase(jugador)) fuera.add(a);
        }
        fuera.sort((x, y) -> Long.compare(y.cuando, x.cuando));
        return fuera;
    }

    public int contar(String jugador) {
        return de(jugador).size();
    }

    /** Retira un aviso (el staff se arrepiente, o era un error). */
    public boolean retirar(long id, String quien) {
        for (Aviso a : avisos) {
            if (a.id != id || a.retirado) continue;
            a.retirado = true;
            a.retiradoPor = quien == null ? "" : quien;
            guardar();
            return true;
        }
        return false;
    }

    private long limiteCaducidad() {
        int dias = plugin.config().getInt("warns.caducan-en-dias", 90);
        if (dias <= 0) return 0L;   // 0 = no caducan nunca
        return System.currentTimeMillis() - dias * 24L * 60L * 60L * 1000L;
    }

    private void podar() {
        int max = Math.max(100, plugin.config().getInt("warns.max-avisos", 5000));
        while (avisos.size() > max) avisos.remove(0);
    }

    // ---------------- La escalera ----------------

    /**
 * Los escalones, de menor a mayor.
 *
 * Se leen con getMapList() y NO con getConfigurationSection(). En el config son
 * una lista de mapas:
 *
 *   niveles:
 *     - avisos: 3
 *       accion: mute
 *
 * getConfigurationSection() sobre eso devuelve null, porque una lista no es una
 * seccion. Con null aqui, niveles() devolvia una lista vacia, el sistema entero
 * se quedaba mudo y /staffwarn seguia contando avisos sin disparar nunca nada.
 * Un fallo silencioso de esos: el plugin arrancaba bien y no pasaba nada.
 */
public List<Nivel> niveles() {
        List<Nivel> fuera = new ArrayList<>();
        for (Map<?, ?> crudo : plugin.config().getMapList("warns.niveles")) {
            try {
                Nivel n = new Nivel();
                n.avisos = enteroDe(crudo, "avisos");
                n.accion = texto(crudo, "accion").toLowerCase(Locale.ROOT);
                n.tiempo = texto(crudo, "tiempo");
                n.motivo = texto(crudo, "motivo");
                if (n.avisos > 0 && !n.accion.isEmpty()) fuera.add(n);
            } catch (Exception e) {
                plugin.getLogger().warning("[warns] nivel ignorado en warns.niveles: "
                        + e.getMessage());
            }
        }
        fuera.sort((a, b) -> Integer.compare(a.avisos, b.avisos));
        return fuera;
    }

    private static String texto(Map<?, ?> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static int enteroDe(Map<?, ?> m, String clave) {
        Object v = m.get(clave);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * El escalon que este jugador ha alcanzado con este numero de avisos.
     *
     * Solo devuelve UNO: el mas alto que cumple. Si el jugador lleva 7 avisos y
     * hay escalones en 3 y en 5, el que se aplica es el de 5. Devolver los dos
     * seria silenciarle y banearle en el mismo clic, que casi nunca es lo que
     * quiere el staff.
     *
     * @return el nivel, o null si todavia no llega a ninguno
     */
    public Nivel nivelAlcanzado(String jugador) {
        int n = contar(jugador);
        if (n <= 0) return null;
        Nivel elegido = null;
        for (Nivel nivel : niveles()) {
            if (n >= nivel.avisos) elegido = nivel;
        }
        return elegido;
    }

    /**
     * El siguiente escalon que le falta, o null si ya no hay ninguno.
     * Se usa para enseñarle al staff cuanto le queda.
     */
    public Nivel siguienteNivel(String jugador) {
        int n = contar(jugador);
        for (Nivel nivel : niveles()) {
            if (n < nivel.avisos) return nivel;
        }
        return null;
    }

    // ---------------- Aplicar el escalon ----------------

    /**
     * Aplica la accion del escalon alcanzado, si la hay.
     *
     * @return null si no habia escalon, o si el escalon es solo informativo
     */
    public String aplicar(Player staff, Player objetivo, int avisosPrevios) {
        Nivel nivel = nivelAlcanzado(objetivo.getName());
        if (nivel == null) return null;

        // Si ya se aplico este mismo escalon (porque le quitaron avisos y luego
        // los recouparon), no se repite. Sin esto, /staffwarn tres veces seguidas
        // con el escalon en 3 dispararia tres silencios en vez de uno.
        if (avisosPrevios >= nivel.avisos) return null;

        String accion = nivel.accion;
        String motivo = nivel.motivo.isEmpty()
                ? (avisosPrevios + 1) + " avisos"
                : nivel.motivo;
        String quien = staff == null ? "warns" : staff.getName();

        if (accion.equals("aviso") || accion.equals("ninguno")) return null;

        long ms = 0;
        if (!nivel.tiempo.isEmpty()) {
            ms = plugin.leerTiempoEnMs(nivel.tiempo);
            if (ms <= 0) {
                plugin.getLogger().warning("[warns] tiempo '" + nivel.tiempo
                        + "' no vale en el nivel de " + nivel.avisos + " avisos.");
                return null;
            }
        }

        switch (accion) {
            case "mute": {
                long hasta = ms <= 0 ? Long.MAX_VALUE
                        : System.currentTimeMillis() + ms;
                plugin.silencios().poner(objetivo.getUniqueId(), objetivo.getName(),
                        motivo + " · " + avisosPrevios + " avisos", hasta);
                objetivo.sendMessage(Text.error("Te han silenciado automáticamente."));
                objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
                plugin.getLogger().info("[warns] " + objetivo.getName() + " silenciado automaticamente"
                        + " (" + avisosPrevios + " avisos), por " + quien);
                plugin.anunciarSancion("mute", objetivo.getName(), quien, motivo, ms <= 0 ? "permanente" : SancionStore.enLetras(ms));
                break;
            }
            case "ban": {
                if (ms <= 0) {
                    plugin.getLogger().warning("[warns] el nivel de " + nivel.avisos
                            + " avisos es un ban sin tiempo, y sin tiempo es permanente. "
                            + "No se aplica solo: pásale al staff.");
                    return null;
                }
                org.bukkit.BanList<org.bukkit.profile.PlayerProfile> lista =
                        Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE);
                lista.addBan(
                        plugin.getServer().createProfile(objetivo.getUniqueId(), objetivo.getName()),
                        motivo + " · " + avisosPrevios + " avisos · automatico por " + quien,
                        new java.util.Date(System.currentTimeMillis() + ms),
                        "OdisseyStaff (" + quien + ")");
                objetivo.kickPlayer(ChatColor.RED
                        + "Has sido expulsado: " + ChatColor.GRAY + motivo);
                plugin.getLogger().info("[warns] " + objetivo.getName()
                        + " baneado automaticamente (" + avisosPrevios + " avisos), por " + quien);
                plugin.anunciarSancion("ban", objetivo.getName(), quien, motivo, SancionStore.enLetras(ms));
                break;
            }
            default:
                plugin.getLogger().warning("[warns] accion desconocida: " + accion);
                return null;
        }

        plugin.historial().anotar("warn", objetivo.getName(), "", quien,
                motivo, "avisos automaticos: " + avisosPrevios);
        plugin.historial().guardar();
        plugin.avisarEquipo(ChatColor.RED + objetivo.getName() + ChatColor.YELLOW
                + " Automatico por " + ChatColor.WHITE + avisosPrevios + " avisos"
                + ChatColor.YELLOW + ": " + accion
                + (ms > 0 ? " " + SancionStore.enLetras(ms) : "")
                + ChatColor.GRAY + " (puesto por " + quien + ")");
        return accion + (ms > 0 ? " " + SancionStore.enLetras(ms) : "");
    }

    // ---------------- Disco ----------------


    public void cargar() {
        YamlConfiguration y = almacen.leer();
        avisos.clear();
        siguienteId = Math.max(1, y.getLong("siguiente-id", 1));
        ConfigurationSection s = y.getConfigurationSection("avisos");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            String p = "avisos." + clave + ".";
            Aviso a = new Aviso();
            a.id = y.getLong(p + "id", 0);
            if (a.id <= 0) continue;
            a.jugador = y.getString(p + "jugador", "");
            String u = y.getString(p + "uuid", "");
            try {
                a.uuid = u.isEmpty() ? null : UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                a.uuid = null;
            }
            a.staff = y.getString(p + "staff", "");
            a.motivo = y.getString(p + "motivo", "");
            a.cuando = y.getLong(p + "cuando", 0);
            a.retirado = y.getBoolean(p + "retirado", false);
            a.retiradoPor = y.getString(p + "retiradoPor", "");
            avisos.add(a);
        }
        plugin.getLogger().info("[warns] " + avisos.size() + " avisos cargados.");
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("siguiente-id", siguienteId);
        for (Aviso a : avisos) {
            String p = "avisos." + a.id + ".";
            y.set(p + "id", a.id);
            y.set(p + "jugador", a.jugador);
            y.set(p + "uuid", a.uuid == null ? "" : a.uuid.toString());
            y.set(p + "staff", a.staff);
            y.set(p + "motivo", a.motivo);
            y.set(p + "cuando", a.cuando);
            y.set(p + "retirado", a.retirado);
            y.set(p + "retiradoPor", a.retiradoPor);
        }
        almacen.escribir(y);
    }

    public int total() {
        return avisos.size();
    }
}