package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo que un staff tiene pendiente de escribir en el chat.
 *
 * Cuando un menu pide un dato (el nombre de quien levantar la sancion, el
 * tiempo, una nota) no hay un chat de entrada: el menu se cierra y el staff
 * escribe en el chat de verdad, que es lo que ya conoce. Este store guarda
 * "este staff esta escribiendo para esto" y el mensaje se convierte en la
 * respuesta.
 *
 * El mensaje se CANCELA: si no, "30m" sale por el global y todo el mundo ve a un
 * admin escribiendo numeros raros.
 *
 * El mapa es un ConcurrentHashMap porque se consulta desde el hilo del chat de
 * Bukkit, que no es el principal.
 */
public final class EsperaStore implements Listener {

    private final OdisseyStaff plugin;
    private final Map<UUID, String> esperando = new ConcurrentHashMap<>();

    public EsperaStore(OdisseyStaff plugin) {
        this.plugin = plugin;
    }

    public void registrar() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Que este staff tiene algo pendiente de escribir, o null. */
    public String de(UUID jugador) {
        return esperando.get(jugador);
    }

    public void poner(UUID jugador, String que) {
        esperando.put(jugador, que == null ? "" : que);
    }

    public void quitar(UUID jugador) {
        esperando.remove(jugador);
    }

    public void limpiar(Player jugador) {
        esperando.remove(jugador.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alChat(AsyncPlayerChatEvent evento) {
        String que = esperando.get(evento.getPlayer().getUniqueId());
        if (que == null) return;
        // Si otro menu ya lo ha cogido antes (MuteStore y compañía tambien
        // escuchan el chat), este mensaje no es para nosotros.
        if (evento.isCancelled()) return;

        esperando.remove(evento.getPlayer().getUniqueId());
        evento.setCancelled(true);

        final Player staff = evento.getPlayer();
        final String texto = evento.getMessage().trim();

        // El menu necesita tocar la API de Bukkit (enviar mensajes, abrir
        // inventarios) y eso va al hilo principal, no al del chat.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!staff.isOnline()) return;
            resolver(staff, que, texto);
        });
    }

    private void resolver(Player staff, String que, String texto) {
        if (que.equals("sanciones-off")) {
            plugin.menuSanciones().resolverEspera(staff, texto);
            return;
        }
        if (que.equals("rango")) {
            resolverRango(staff, texto);
            return;
        }
        if (que.equals("espiar")) {
            resolverEspiar(staff, texto);
            return;
        }
        if (que.equals("congelar")) {
            resolverSimple(staff, texto, "freeze");
            return;
        }
        if (que.equals("chatlog")) {
            resolverSimple(staff, texto, "staffchatlog");
            return;
        }
        if (que.equals("alts")) {
            // "Cuentas alternativas" del menu de staff: lanza /staffalt, no la
            // espia. Antes esta espera no existia y el boton espiaba sin querer.
            resolverSimple(staff, texto, "staffalt");
            return;
        }
        if (que.equals("vigilar")) {
            resolverVigilar(staff, texto);
            return;
        }
        if (que.equals("ban-motivo")) {
            plugin.menuBanear().resolverMotivo(staff, texto);
            return;
        }
        if (que.equals("ban-confirmar")) {
            plugin.menuBanear().resolverConfirmacion(staff, texto);
            return;
        }
        if (que.equals("banear")) {
            Player objetivo = buscar(staff, texto);
            if (objetivo == null) {
                staff.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
                return;
            }
            plugin.menuBanear().abrir(staff, objetivo);
            return;
        }
        if (que.equals("history")) {
            Player objetivo = buscar(staff, texto);
            if (objetivo == null) {
                // El historial de uno que no esta dentro tambien vale: por
                // nombre se busca igual, que es justo cuando mas se consulta.
                plugin.menuHistorial().abrir(staff, texto);
                return;
            }
            plugin.menuHistorial().abrir(staff, objetivo.getName());
            return;
        }
        if (que.equals("warn-quitar")) {
            resolverQuitarAviso(staff, texto);
        }
    }

    /** "/staffwarn Rob quitar 12" desde el historial. */
    private void resolverQuitarAviso(Player staff, String texto) {
        String[] partes = texto.split("\\s+");
        if (partes.length < 2) {
            staff.sendMessage(Text.error("Uso: <jugador> <id del aviso>"));
            return;
        }
        Player objetivo = buscar(staff, partes[0]);
        String nombre = objetivo == null ? partes[0] : objetivo.getName();
        long id;
        try {
            id = Long.parseLong(partes[1]);
        } catch (NumberFormatException e) {
            staff.sendMessage(Text.error("El número de aviso no vale."));
            return;
        }
        if (plugin.warns().retirar(id, staff.getName())) {
            staff.sendMessage(Text.exito("Aviso #" + id + " devuelto a " + nombre + "."));
            plugin.getLogger().info("[warns] " + staff.getName()
                    + " devolvio el aviso #" + id + " de " + nombre);
        } else {
            staff.sendMessage(Text.error("No hay ningún aviso #" + id + "."));
        }
    }

    /** "Escribe un nombre y se ejecuta el comando con ese nombre." */
    private void resolverSimple(Player staff, String texto, String comando) {
        if (cancelado(staff, texto)) return;
        Player objetivo = buscar(staff, texto);
        if (objetivo == null) {
            staff.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
            return;
        }
        Bukkit.dispatchCommand(staff, comando + " " + objetivo.getName());
    }

    private void resolverVigilar(Player staff, String texto) {
        if (cancelado(staff, texto)) return;
        Player objetivo = buscar(staff, texto);
        if (objetivo == null) {
            staff.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
            return;
        }
        // "vigilar Rob" sin motivo lo saca de la lista, que es lo que hace el
        // comando cuando no hay texto detras.
        Bukkit.dispatchCommand(staff, "staffvigilar " + objetivo.getName());
    }

    private boolean cancelado(Player staff, String texto) {
        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            staff.sendMessage(Text.info("Cancelado."));
            return true;
        }
        return false;
    }

    private Player buscar(Player staff, String texto) {
        Player exacto = plugin.getServer().getPlayerExact(texto);
        if (exacto != null) return exacto;
        String bajo = texto.toLowerCase(Locale.ROOT);
        Player encontrado = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.getName().toLowerCase(Locale.ROOT).startsWith(bajo)) continue;
            // Con dos nombres parecidos no se elige uno al azar: se pregunta.
            if (encontrado != null) {
                staff.sendMessage(Text.error("Más de uno empieza por \""
                        + texto + "\": " + encontrado.getName() + " y " + p.getName()));
                return null;
            }
            encontrado = p;
        }
        return encontrado;
    }

    private void resolverRango(Player staff, String texto) {
        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("quita")) {
            plugin.gestor().estado(staff).rango = "";
            plugin.gestor().estado(staff).rangoLongitud = 0;
            staff.sendMessage(Text.info("Rango quitado. Vuelves a escribir normal."));
            return;
        }
        if (texto.length() > 20) {
            staff.sendMessage(Text.error("Eso es muy largo para un rango (máximo 20)."));
            return;
        }
        String limpio = ChatColor.stripColor(texto);
        plugin.gestor().estado(staff).rango = texto;
        plugin.gestor().estado(staff).rangoLongitud = limpio.length();
        staff.sendMessage(Text.exito("Rango puesto: " + texto));
        staff.sendMessage(Text.info("Aparecerá delante de tu nombre en el chat."));
    }

    private void resolverEspiar(Player staff, String texto) {
        if (cancelado(staff, texto)) return;
        Player objetivo = buscar(staff, texto);
        if (objetivo == null) return;
        plugin.menuEspiar().entrar(staff, objetivo);
    }
}
