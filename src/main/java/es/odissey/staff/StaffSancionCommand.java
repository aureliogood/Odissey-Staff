package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /staffsancion <jugador> <tiempo> <motivo>
 * /staffsancion <jugador> off       -> se la levanta antes de tiempo
 * /staffsancion                    -> lista las sanciones en curso
 *
 * El sancionado se queda quieto, a oscuras y con un titulo en pantalla, y el
 * reloj SOLO corre mientras esta conectado. Si se va, el tiempo se para y al
 * volver le devuelve al mismo sitio.
 */
public final class StaffSancionCommand extends BaseCommand {

    private static final Pattern TIEMPO = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    public StaffSancionCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        // Sin argumentos, abre el menu. Todo lo demas (con jugador, tiempo y
        // motivo) se hace ahi, que es mas seguro que escribirlo de memoria.
        if (args.length == 0) {
            if (!soloJugador(quien)) return;
            plugin.menuSancion().abrirRaiz((Player) quien);
            return;
        }

        String nombre = args[0];

        // "lista" no es un jugador, es una peticion de ver las sanciones vivas.
        if (nombre.equalsIgnoreCase("lista") || nombre.equalsIgnoreCase("list")) {
            listar(quien);
            return;
        }

        // Levantar ANTES de comprobar si tiene sancion en curso.
        //
        // El orden importa: si se mira primero "ya tiene sancion" y se avisa, el
        // comando nunca llega al "off". Y el caso de usar "off" es justamente
        // cuando el jugador SI tiene sancion.
        if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("quitar"))) {
            levantar(quien, nombre);
            return;
        }

        // Lo de siempre, por si alguien lo escribe a mano o viene de un script.
        if (args.length >= 3) {
            legacy(quien, args);
            return;
        }

        quien.sendMessage(Text.error("Uso: /staffsancion  (abre el menu)"));
        quien.sendMessage(Text.info("Para levantarla: /staffsancion <jugador> off"));
        quien.sendMessage(Text.info("El menu es la via normal: motivo, jugador, tiempo, destino y confirmar."));
        quien.sendMessage(Text.info("/staffsancion lista  -> ver las sanciones en curso."));
    }

    /**
     * El metodo antiguo, con /staffsancion <jugador> <tiempo> <motivo>.
     *
     * Se deja por si un script o un atajo lo sigue usando, y por si en el menu
     * se echa de menos escribirlo de golpe.
     */
    private void legacy(CommandSender quien, String[] args) {
        String nombre = args[0];

        // Levantar ANTES de comprobar si tiene sancion en curso.
        //
        // El orden importa: si se mira primero "ya tiene sancion" y se avisa, el
        // comando nunca llega al "off". Y el caso de usar "off" es justamente
        // cuando el jugador SI tiene sancion, asi que /staffsancion Rob off no
        // hacia nada: decia "ya tiene una sancion en curso" y ahi se quedaba.
        if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("quitar"))) {
            levantar(quien, nombre);
            return;
        }

        // Y ahora, si ya tiene una en curso, avisar: no se dejan dos sanciones a
        // la vez sobre el mismo jugador, que es como se le pierde la cuenta del
        // tiempo.
        java.util.UUID posible = resolverUuid(nombre);
        if (plugin.sanciones().tiene(posible)) {
            SancionStore.Registro actual = plugin.sanciones().obtener(posible);
            quien.sendMessage(Text.error(actual.nombre + " ya tiene una sanción en curso: le quedan "
                    + ChatColor.YELLOW + SancionStore.enLetras(actual.restante) + ChatColor.RED + "."));
            quien.sendMessage(Text.info("Levántala primero con /staffsancion " + actual.nombre + " off"));
            return;
        }

        if (args.length < 3) {
            quien.sendMessage(Text.error("Uso: /staffsancion <jugador> <tiempo> <motivo>"));
            quien.sendMessage(Text.info("El tiempo va antes del motivo: /staffsancion Rob 30m Making spam"));
            quien.sendMessage(Text.info("Unidades: 30s, 10m, 2h, 1d. Para levantarla antes: /staffsancion Rob off"));
            return;
        }

        Player objetivo = jugador(quien, nombre);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, nombre);
        if (objetivo == null) {
            quien.sendMessage(Text.error("Ese jugador no está conectado."));
            quien.sendMessage(Text.info("Esta sanción necesita que el jugador esté dentro: "
                    + "si no, no hay a quién congelar ni dónde devolverlo."));
            return;
        }
        if (!plugin.puedeSancionar(quien, objetivo)) return;

        long ms = leerTiempo(args[1]);
        if (ms <= 0) {
            quien.sendMessage(Text.error("El tiempo no vale. Usa algo como 30m, 2h o 1d."));
            return;
        }

        StringBuilder motivo = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (motivo.length() > 0) motivo.append(' ');
            motivo.append(args[i]);
        }

        SancionStore.Registro r = plugin.sanciones().poner(objetivo, ms, motivo.toString(), quien.getName());
        plugin.sanciones().congelar(objetivo, r);
        plugin.sanciones().mostrar(objetivo, r);

        objetivo.sendMessage(Text.aviso("Has sido sancionado por " + quien.getName() + "."));
        objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
        objetivo.sendMessage(Text.info("Tiempo restante: " + ChatColor.WHITE + SancionStore.enLetras(ms) + ChatColor.GRAY + "."));
        objetivo.sendMessage(Text.info("El reloj solo corre mientras estés conectado."));

        quien.sendMessage(Text.exito("Sancionado " + objetivo.getName() + " por "
                + ChatColor.YELLOW + SancionStore.enLetras(ms) + ChatColor.GREEN + ": " + motivo));
        plugin.getLogger().info("[sanciones] " + quien.getName() + " -> " + objetivo.getName()
                + " (" + SancionStore.legible(ms) + ") " + motivo);
        plugin.anunciarSancion("sancion", objetivo.getName(), quien.getName(), motivo.toString(), SancionStore.enLetras(ms));

        for (Player otro : Bukkit.getOnlinePlayers()) {
            if (otro.equals(quien) || !plugin.esStaff(otro)) continue;
            otro.sendMessage(Text.prefijo() + ChatColor.GRAY + "[staff] " + ChatColor.WHITE + quien.getName()
                    + ChatColor.GRAY + " ha sancionado a " + ChatColor.WHITE + objetivo.getName()
                    + ChatColor.GRAY + " por " + ChatColor.YELLOW + SancionStore.enLetras(ms));
        }
    }

    private void levantar(CommandSender quien, String nombre) {
        Player objetivo = jugador(quien, nombre);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, nombre);

        java.util.UUID uuid;
        if (objetivo != null) {
            uuid = objetivo.getUniqueId();
        } else {
            uuid = Bukkit.getOfflinePlayer(nombre).getUniqueId();
        }
        SancionStore.Registro r = plugin.sanciones().obtener(uuid);
        if (r == null) {
            quien.sendMessage(Text.aviso((objetivo != null ? objetivo.getName() : nombre)
                    + " no tiene ninguna sanción."));
            return;
        }
        // Antes no se comprobaba nada aqui: cualquier miembro del staff podia
        // levantar con /staffsancion <nombre> off la sancion que le habia puesto
        // otro admin a otro staff. Ahora pasa por el mismo filtro que el resto.
        if (objetivo != null && !puedeTocar(quien, objetivo, "levantar la sanción de")) return;

        plugin.sanciones().quitar(uuid);
        if (objetivo != null) {
            plugin.gestor().descongelar(objetivo);
            objetivo.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
            objetivo.sendTitle(null, null, 0, 0, 0);
            objetivo.sendMessage(Text.exito("Tu sanción se ha levantado antes de tiempo."));
        }
        quien.sendMessage(Text.exito("Sanción levantada a "
                + (r.nombre.isEmpty() ? nombre : r.nombre) + "."));
        plugin.getLogger().info("[sanciones] " + quien.getName() + " levanto la sancion de " + r.nombre);
    }

    private void listar(CommandSender quien) {
        java.util.List<SancionStore.Registro> lista = plugin.sanciones().todas();
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                + "══ Sanciones en curso (" + lista.size() + ") ══");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info("No hay ninguna."));
            return;
        }
        for (SancionStore.Registro r : lista) {
            boolean dentro = r.uuid != null && Bukkit.getPlayer(r.uuid) != null;
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + r.nombre
                    + ChatColor.YELLOW + " · " + SancionStore.legible(r.restante)
                    + (dentro ? ChatColor.GREEN + " · contando"
                              : ChatColor.DARK_GRAY + " · reloj parado (no está conectado)")
                    + (r.motivo.isEmpty() ? "" : ChatColor.GRAY + " · " + r.motivo));
        }
    }

    /** El UUID de un jugador, este conectado o no. */
    private java.util.UUID resolverUuid(String nombre) {
        Player enLinea = plugin.getServer().getPlayerExact(nombre);
        if (enLinea != null) return enLinea.getUniqueId();
        enLinea = jugadorQueEmpiezaPor(Bukkit.getConsoleSender(), nombre);
        if (enLinea != null) return enLinea.getUniqueId();
        return Bukkit.getOfflinePlayer(nombre).getUniqueId();
    }

    private long leerTiempo(String texto) {        Matcher m = TIEMPO.matcher(texto.trim());
        if (!m.matches()) return 0;
        long n;
        try {
            n = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
        // Tope de 30 dias: mas que eso es mejor un ban, que no exige que el
        // jugador este conectado para cumplirlo.
        switch (m.group(2).toLowerCase(java.util.Locale.ROOT)) {
            case "s": return Math.min(n * 1000L, 30L * 86400_000L);
            case "m": return Math.min(n * 60_000L, 30L * 86400_000L);
            case "h": return Math.min(n * 3_600_000L, 30L * 86400_000L);
            case "d": return Math.min(n * 86_400_000L, 30L * 86400_000L);
            default: return 0;
        }
    }
}
