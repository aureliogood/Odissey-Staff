package es.odissey.staff;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Los avisos automaticos al entrar un jugador.
 *
 * Son las cuatro cosas que un staff novel no se da cuenta de mirar y que luego
 * importan mucho:
 *
 *   - que la cuenta es de hace dos dias,
 *   - que la IP es la misma que la de alguien baneado,
 *   - que vuelve con una sancion puesta,
 *   - y que esta en la lista de vigilancia.
 *
 * Van solos, sin que nadie los pida, y salen a todo el staff. El motivo es que
 * dependen del turno: el que mira el log a las once de la noche no es el mismo
 * que entro por la ma�ana, y si el aviso se queda en un mensaje que nadie lee,
 * no sirve para nada.
 */
public final class AvisosEntrada {

    private AvisosEntrada() {
    }

    /** Ultimo aviso de comando bloqueado por jugador, para no repetirlo. */
    private static final java.util.Map<java.util.UUID, Long> ULTIMO_AVISO =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void revisar(Player jugador, OdisseyStaff plugin) {
        if (!plugin.config().getBoolean("avisos.al-entrar", true)) return;
        // Al staff no se le avisa de nada: ya sabe lo que hace y solo genera ruido.
        if (plugin.esStaff(jugador)) return;

        List<String> avisos = new ArrayList<>();

        cuentaNueva(jugador, plugin, avisos);
        sanctioned(jugador, plugin, avisos);
        vigilado(jugador, plugin, avisos);
        ipDeBaneado(jugador, plugin, avisos);

        for (String a : avisos) {
            plugin.avisarEquipo(a);
        }
    }

    private static void cuentaNueva(Player jugador, OdisseyStaff plugin, List<String> avisos) {
        int dias = plugin.config().getInt("avisos.cuenta-nueva-dias", 7);
        if (dias <= 0) return;
        long firstPlayed = jugador.getFirstPlayed();
        long antiguedad = (firstPlayed <= 0L || !jugador.hasPlayedBefore()) ? 0L : (System.currentTimeMillis() - firstPlayed);
        if (antiguedad > dias * 24L * 60L * 60L * 1000L) return;

        String texto = ChatColor.RED + jugador.getName() + ChatColor.YELLOW
                + " acaba de entrar con una cuenta de "
                + ChatColor.WHITE + (antiguedad == 0L ? "primer ingreso" : ConexionStore.bonito(antiguedad))
                + ChatColor.YELLOW + " · IP " + ChatColor.WHITE + plugin.ipDe(jugador);
        String alt = altDeLaIp(jugador, plugin);
        if (!alt.isEmpty()) texto += ChatColor.YELLOW + " · mismo equipo que " + ChatColor.WHITE + alt;
        avisos.add(texto);
    }

    private static void sanctioned(Player jugador, OdisseyStaff plugin, List<String> avisos) {
        SancionStore.Registro r = plugin.sanciones().obtener(jugador.getUniqueId());
        if (r == null) return;
        avisos.add(ChatColor.RED + jugador.getName() + ChatColor.YELLOW
                + " vuelve con una sanción puesta: le quedan "
                + ChatColor.WHITE + SancionStore.enLetras(r.restante)
                + ChatColor.YELLOW + " (" + r.motivo + ")");
    }

    private static void vigilado(Player jugador, OdisseyStaff plugin, List<String> avisos) {
        if (!plugin.vigilancia().estaVigilado(jugador.getName())) return;
        VigilanciaStore.Marca m = plugin.vigilancia().marca(jugador.getName());
        avisos.add(ChatColor.LIGHT_PURPLE + jugador.getName() + ChatColor.YELLOW
                + " está en la lista de vigilancia"
                + (m == null ? "" : ChatColor.GRAY + ": " + m.motivo));
    }

    /**
     * Alguien con la IP de un baneado.
     *
     * Se mira la lista de baneos del servidor, que es donde acaban tanto los
     * baneos manuales como los temporales que pone este plugin. Una IP puede
     * tener a tres cuentas baneadas: se avisa una vez, con la cuenta que mas
     * veces aparece.
     */
    private static void ipDeBaneado(Player jugador, OdisseyStaff plugin, List<String> avisos) {
        if (!plugin.config().getBoolean("avisos.ip-de-baneado", true)) return;
        String ip = plugin.ipDe(jugador);
        if (ip.isEmpty()) return;

        List<String> nombres = plugin.conexiones().mismaIp(ip, "");
        if (nombres.isEmpty()) return;

        // Dos fuentes, y las dos se miran:
        //   - la lista de NOMBRES: es donde mete los bans este plugin y /ban,
        //   - la lista de IPs: es donde acaban los baneos por IP, que los
        //     plugins y los.put手动 hacen mucho mas a menudo que los manuales.
        // Antes solo se miraba la de nombres, asi que un baneo por IP (que es
        // justo el que se pone cuando el del nombre no ha servido) no
        // disparaba ni un aviso.
        List<String> porNombre = new ArrayList<>();
        List<String> porIp = new ArrayList<>();
        for (String nombre : nombres) {
            if (nombre.equalsIgnoreCase(jugador.getName())) continue;
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(nombre)) porNombre.add(nombre);
        }
        if (Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) {
            porIp.add("su propia IP está baneada");
        }
        if (porNombre.isEmpty() && porIp.isEmpty()) return;

        StringBuilder detalle = new StringBuilder();
        int cuantos = Math.min(3, porNombre.size());
        for (int i = 0; i < cuantos; i++) {
            if (i > 0) detalle.append(", ");
            detalle.append(porNombre.get(i));
        }
        if (porNombre.size() > 3) detalle.append(" y ").append(porNombre.size() - 3).append(" más");
        if (!porIp.isEmpty()) {
            if (detalle.length() > 0) detalle.append(" · ");
            detalle.append(porIp.get(0));
        }

        avisos.add(ChatColor.RED + jugador.getName() + ChatColor.YELLOW
                + " entra con la IP " + ChatColor.WHITE + ip
                + ChatColor.YELLOW + " que tiene baneada a " + ChatColor.WHITE + detalle);
    }

    /** Si ha entrado antes con esta IP, el nombre mas antiguo. */
    private static String altDeLaIp(Player jugador, OdisseyStaff plugin) {
        String ip = plugin.ipDe(jugador);
        if (ip.isEmpty()) return "";
        List<String> otros = plugin.conexiones().mismaIp(ip, jugador.getName());
        if (otros.isEmpty()) return "";
        return otros.get(0).toLowerCase(Locale.ROOT);
    }

    /**
     * Alguien que choca contra un comando bloqueado.
     *
     * Con /staffbloquear se le quitan comandos a alguien, y el aviso es la
     * unica forma de enterarse de que el jugador lo ha notado y esta probando
     * a saltarselo. Se corta el bucle con un minuto de margen: lo justo para
     * enterarse, sin llenar el chat del staff de lo mismo.
     */
    public static void comandoBloqueado(Player jugador, String comando, OdisseyStaff plugin) {
        if (!plugin.config().getBoolean("avisos.comando-bloqueado", true)) return;

        long ahora = System.currentTimeMillis();
        Long anterior = ULTIMO_AVISO.get(jugador.getUniqueId());
        if (anterior != null && ahora - anterior < 60_000L) return;
        ULTIMO_AVISO.put(jugador.getUniqueId(), ahora);

        plugin.avisarEquipo(ChatColor.RED + jugador.getName() + ChatColor.YELLOW
                + " intentó usar " + ChatColor.WHITE + "/" + comando
                + ChatColor.YELLOW + ", que tiene bloqueado");
    }
}
