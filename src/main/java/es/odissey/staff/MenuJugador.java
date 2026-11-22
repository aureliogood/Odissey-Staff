package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * La ficha de jugador, a la que se entra desde /staff.
 *
 * Antes este menu solo servia para teletransportarse: una lista de cabezas y,
 * al pulsar una, aparecías junto a ese jugador. Era util, pero obligaba a
 * volver a escribir el comando para cualquier otra cosa.
 *
 * Ahora la cabeza es una ficha con todo lo que se sabe de ese jugador de
 * un vistazo (vida, mundo, ping, si esta sancionado, congelado, silenciado,
 * en vigilancia, su IP, sus alts, su historial...) y al pulsarla se abre un
 * menu con lo que se le puede hacer:
 *
 *   - ir hasta el, que es lo de antes,
 *   - sancionarlo, que entra en el flujo de /staffsancion con el jugador ya
 *     elegido.
 *
 * Asi /staffmenu es el comando global y /staffsancion queda como atajo.
 */
public final class MenuJugador implements Listener {

    /** Todos los menus: seis filas, como un cofre grande. */
    private static final int TAM = 54;

    /** Huecos libres dentro del marco. */
    private static final int[] INTERIOR = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final int PAGINA = 28;

    /** Lo que este staff tiene abierto. */
    private static final class Estado {
        UUID objetivo;              // a quien le ha dado click
        int pagina;
        String busqueda = "";       // filtrar la lista por nombre ("" = todos)
        int filtro;                 // 0 todos, 1 solo staff, 2 solo con problemas
        Inventory invLista;
        Inventory invAcciones;
    }

    /**
     * Lo que el menu esta esperando que el staff escriba en el chat.
     *
     * La mayoria de las acciones (aviso, silencio, expulsion, nota, vigilancia,
     * jaula) necesitan texto que no se puede pedir con una cabeza, asi que se
     * pregunta por el chat y, al recibirlo, se lanza el comando de siempre. Asi
     * no hay dos caminos distintos para lo mismo: la cabeza solo es un atajo.
     */
    private enum Esperando { NADA, MOTIVO, JAULA }

    private final OdisseyStaff plugin;
    // ConcurrentHashMap y no HashMap: el listener del chat (AsyncPlayerChatEvent) lee
    // y quita estos mapas desde el hilo asincrono de Bukkit.
    private final Map<UUID, Estado> estados = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Esperando> esperando = new java.util.concurrent.ConcurrentHashMap<>();
    /** Que accion esta esperando texto ("nota", "silencio"...). */
    private final Map<UUID, String> pendiente = new java.util.concurrent.ConcurrentHashMap<>();
    private final org.bukkit.NamespacedKey MARCA;

    public MenuJugador(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.MARCA = new org.bukkit.NamespacedKey(plugin, "menu_jugador");
    }

    public void registrar() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Estado estado(Player staff) {
        return estados.computeIfAbsent(staff.getUniqueId(), k -> new Estado());
    }

    // ================= La lista =================

    public void abrirRaiz(Player staff) {
        Estado e = new Estado();
        estados.put(staff.getUniqueId(), e);
        abrirLista(staff, e);
    }

    /** Vuelve a la lista sin perder el estado (lo usa el boton "atras"). */
    public void volver(Player staff) {
        Estado e = estados.get(staff.getUniqueId());
        if (e == null) {
            abrirRaiz(staff);
            return;
        }
        e.objetivo = null;
        e.pagina = 0;
        abrirLista(staff, e);
    }

    private void abrirLista(Player staff, Estado e) {
        List<Player> todos = new ArrayList<>();
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (!p.equals(staff)) todos.add(p);   // a uno mismo no se le vigila
        }

        // Filtro por nombre y por estado. Se aplica sobre la copia, asi que el
        // estado guardado no se toca y al quitar el filtro vuelven a salir todos.
        int antes = todos.size();
        if (!e.busqueda.isEmpty()) {
            String b = e.busqueda.toLowerCase(Locale.ROOT);
            todos.removeIf(p -> !p.getName().toLowerCase(Locale.ROOT).contains(b));
        }
        if (e.filtro == 1) {
            todos.removeIf(p -> !plugin.esStaff(p));
        } else if (e.filtro == 2) {
            todos.removeIf(p -> !conProblema(p));
        }

        e.pagina = Math.min(e.pagina, Math.max(0, (int) Math.ceil(todos.size() / (double) PAGINA) - 1));
        int paginas = (int) Math.ceil(todos.size() / (double) PAGINA);
        String cuenta = todos.size() + (todos.size() == 1 ? " jugador" : " jugadores")
                + (paginas > 1 ? " · " + (e.pagina + 1) + "/" + paginas : "");
        String aviso = "";
        if (!e.busqueda.isEmpty()) aviso += " · «" + e.busqueda + "»";
        if (e.filtro == 1) aviso += " · staff";
        else if (e.filtro == 2) aviso += " · con problemas";
        e.invLista = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.YELLOW + "" + ChatColor.BOLD + "JUGADORES "
                + ChatColor.GRAY + cuenta + aviso);

        if (todos.isEmpty()) {
            String que;
            if (!e.busqueda.isEmpty() || e.filtro != 0) {
                que = "Nadie encaja con el filtro.";
            } else {
                que = "No hay nadie más dentro";
            }
            e.invLista.setItem(22, item(null, Material.GRAY_DYE,
                    ChatColor.RED + que,
                    ChatColor.GRAY + "Quita el filtro para verlos todos."));
        }

        int desde = e.pagina * PAGINA;
        int fin = Math.min(todos.size(), desde + PAGINA);
        for (int i = desde; i < fin; i++) {
            e.invLista.setItem(INTERIOR[i - desde], cabeza(todos.get(i), ficha(todos.get(i))));
        }

        if (todos.size() > PAGINA) {
            e.invLista.setItem(45, item("atras", Material.ARROW,
                    ChatColor.YELLOW + "◀ Anterior"));
            e.invLista.setItem(53, item("siguiente", Material.ARROW,
                    ChatColor.YELLOW + "Siguiente ▶"));
        }

        // El titulo va en el borde de arriba (slot 4), no en el 13: el 13 es
        // parte de INTERIOR y, al pintarlo DESPUES de las cabezas, tapaba al 4º
        // jugador de cada pagina, que quedaba invisible e inaccesible.
        e.invLista.setItem(4, item(null, Material.PAPER,
                ChatColor.DARK_GRAY + "Staff menu",
                ChatColor.GRAY + "Pulsa una cabeza para ver",
                ChatColor.GRAY + "todo lo que se sabe de ese",
                ChatColor.GRAY + "jugador y decidir qué hacer.",
                "",
                ChatColor.DARK_GRAY + "Sin negrita ni colores chillones:",
                ChatColor.DARK_GRAY + "aquí se lee, no se juega."));

        e.invLista.setItem(48, item("buscar", Material.NAME_TAG,
                e.busqueda.isEmpty()
                        ? ChatColor.AQUA + "Buscar por nombre"
                        : ChatColor.AQUA + "Buscando: " + ChatColor.WHITE + e.busqueda,
                ChatColor.GRAY + "Escribe parte del nombre en el chat.",
                ChatColor.DARK_GRAY + "Escribe '-' para quitarla."));

        String[] loreFiltro = { "Staff", "Con problemas", "Todos" };
        String ahora = loreFiltro[Math.max(0, Math.min(2, e.filtro))];
        e.invLista.setItem(50, item("filtro", Material.HOPPER,
                ChatColor.YELLOW + "Filtro: " + ChatColor.WHITE + ahora,
                ChatColor.GRAY + "Click para pasar al siguiente.",
                ChatColor.DARK_GRAY + "Está " + antes + " dentro en total."));

        // Boton de vuelta al panel. Va en el 46, no en el 48: ese lo lleva
        // "buscar" y se pisan. Sin el, el staff entra a la ficha desde /staff y
        // al cerrarla se queda sin camino de vuelta que no sea abrir /staff otra
        // vez, que es justo lo que se quiere evitar.
        e.invLista.setItem(46, item("panel", Material.SPYGLASS,
                ChatColor.YELLOW + "◀ Volver al panel",
                ChatColor.GRAY + "Menú principal de staff."));

        e.invLista.setItem(49, item("cerrar", Material.BARRIER,
                ChatColor.RED + "✖ Cerrar"));

        staff.openInventory(e.invLista);
    }

    // ================= Las acciones sobre un jugador =================

    private void abrirAcciones(Player staff, Player objetivo, Estado e) {
        e.objetivo = objetivo.getUniqueId();
        e.invAcciones = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.YELLOW + "" + ChatColor.BOLD + objetivo.getName());

        // La cabecera va en el 13 (centro de la primera fila), igual que en
        // todos los demas menus. El boton de volver se queda en la fila de
        // abajo, con los demas controles.
        e.invAcciones.setItem(13, item(null, Material.PAPER,
                ChatColor.DARK_GRAY + "Ficha",
                fichaLarga(objetivo)));

        e.invAcciones.setItem(45, item("atras-lista", Material.ARROW,
                ChatColor.YELLOW + "◀ Volver a la lista"));

        // ---- Lo que se le hace al cuerpo ----
        boolean congelado = plugin.gestor().congelado(objetivo);
        e.invAcciones.setItem(19, item("congelar", congelado ? Material.PACKED_ICE : Material.ICE,
                congelado
                        ? ChatColor.AQUA + "❄ Descongelar"
                        : ChatColor.AQUA + "❄ Congelar",
                ChatColor.GRAY + "Click para " + (congelado ? "soltarlo" : "dejarlo quieto") + ".",
                ChatColor.DARK_GRAY + "Congelado = quieto, no protegido."));

        MuteStore.Registro silencio = plugin.silencios().registro(objetivo.getUniqueId());
        boolean silenciado = silencio != null;
        e.invAcciones.setItem(20, item("silenciar", Material.BELL,
                silenciado
                        ? ChatColor.YELLOW + "✦ Quitar el silencio"
                        : ChatColor.YELLOW + "✦ Silenciar",
                silenciado
                        ? ChatColor.GRAY + "Le devuelve el chat."
                        : ChatColor.GRAY + "Le quita el chat un rato.",
                silenciado
                        ? ChatColor.DARK_GRAY + "Click para levantar el silencio."
                        : ChatColor.DARK_GRAY + "Te pedirá tiempo y motivo."));

        e.invAcciones.setItem(21, item("traer-aqui", Material.LEAD,
                ChatColor.AQUA + "Traerlo aquí",
                ChatColor.GRAY + "Lo teletransporta a tu sitio.",
                ChatColor.DARK_GRAY + "Es él quien se mueve, no tú."));

        e.invAcciones.setItem(22, item("ir-hasta", Material.ENDER_PEARL,
                ChatColor.AQUA + "Ir hasta " + objetivo.getName(),
                ChatColor.GRAY + "Te teletransportas a su sitio.",
                ChatColor.GRAY + "No lo mueves a él.",
                ChatColor.DARK_GRAY + "Coordenadas: " + xyz(objetivo)));

        e.invAcciones.setItem(23, item("jaula", Material.IRON_BARS,
                ChatColor.RED + "Meter en una celda",
                ChatColor.GRAY + "Lo encierra y le quita lo que",
                ChatColor.GRAY + "no debería tener encima.",
                ChatColor.DARK_GRAY + "Te pedirá celda, tiempo y motivo."));

        e.invAcciones.setItem(24, item("sancionar", Material.REDSTONE,
                ChatColor.RED + "Sancionar a " + objetivo.getName(),
                ChatColor.GRAY + "Abre el flujo de sanciones:",
                ChatColor.GRAY + "motivo, tiempo, a qué mundo",
                ChatColor.GRAY + "mandarlo y si se congela.",
                "",
                ChatColor.DARK_GRAY + "Es lo mismo que /staffsancion,",
                ChatColor.DARK_GRAY + "pero sin elegir el jugador."));

        e.invAcciones.setItem(25, item("expulsar", Material.IRON_DOOR,
                ChatColor.RED + "Expulsar",
                ChatColor.GRAY + "Lo saca del servidor sin ban.",
                ChatColor.DARK_GRAY + "Te pedirá el motivo."));

        // ---- Lo que se consulta ----
        e.invAcciones.setItem(28, item("historial", Material.WRITTEN_BOOK,
                ChatColor.LIGHT_PURPLE + "Historial",
                ChatColor.GRAY + "Avisos, silencios, expulsiones",
                ChatColor.GRAY + "y sanciones que lleva."));

        e.invAcciones.setItem(30, item("inventario", Material.CHEST,
                ChatColor.GOLD + "Inventario",
                ChatColor.GRAY + "Lo que lleva encima ahora mismo."));

        e.invAcciones.setItem(31, item(null, Material.BOOK,
                ChatColor.DARK_GRAY + "Estado",
                estadoLargo(objetivo)));

        e.invAcciones.setItem(32, item("chatlog", Material.PAPER,
                ChatColor.WHITE + "Qué ha dicho",
                ChatColor.GRAY + "Su chat de los últimos minutos."));

        e.invAcciones.setItem(34, item("snapshot", Material.SPYGLASS,
                ChatColor.GOLD + "Lo que ha hecho",
                ChatColor.GRAY + "Bloques, comandos y chat de",
                ChatColor.GRAY + "los últimos minutos."));

        // ---- Lo que se anota sobre él ----
        e.invAcciones.setItem(38, item("notas", Material.MAP,
                ChatColor.YELLOW + "Añadir una nota",
                ChatColor.GRAY + "Nota interna que el jugador",
                ChatColor.GRAY + "no ve. Solo la lee el staff."));

        e.invAcciones.setItem(40, item("alts", Material.SKELETON_SKULL,
                ChatColor.DARK_PURPLE + "Cuentas alternativas",
                ChatColor.GRAY + "Misma IP, nombres parecidos",
                ChatColor.GRAY + "y horas jugadas."));

        e.invAcciones.setItem(42, item("vigilancia", Material.ENDER_EYE,
                ChatColor.DARK_PURPLE + "Vigilancia",
                ChatColor.GRAY + "Avisa al staff cuando entre.",
                ChatColor.DARK_GRAY + "Te pedirá el motivo."));

        e.invAcciones.setItem(49, item("cerrar", Material.BARRIER,
                ChatColor.RED + "✖ Cerrar"));

        staff.openInventory(e.invAcciones);
    }

    // ================= Clicks =================

    @EventHandler(priority = EventPriority.HIGH)
    public void alClick(InventoryClickEvent evento) {
        Estado e = estados.get(evento.getWhoClicked().getUniqueId());
        if (e == null) return;
        Inventory abierto = evento.getView().getTopInventory();
        if (!abierto.equals(e.invLista) && !abierto.equals(e.invAcciones)) return;

        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (evento.getRawSlot() < 0 || evento.getRawSlot() >= abierto.getSize()) return;
        ItemStack clicked = evento.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(MARCA, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null || id.isEmpty()) return;
        plugin.sonidos().click(staff);

        if (id.startsWith("jug:")) {
            clickCabeza(staff, e, id.substring(4));
            return;
        }

        switch (id) {
            case "atras":
                if (e.pagina > 0) e.pagina--;
                abrirLista(staff, e);
                break;
            case "siguiente":
                e.pagina++;
                abrirLista(staff, e);
                break;

            case "atras-lista": abrirLista(staff, e); break;
            case "panel":
                // Se suelta el espiado antes de volver: si no, el staff
                // reabriria el panel con un espiado que cree haber cerrado.
                plugin.menuEspiar().salir(staff, true);
                estados.remove(staff.getUniqueId());
                staff.closeInventory();
                plugin.menuStaff().abrir(staff);
                break;

            case "buscar":
                esperando.put(staff.getUniqueId(), Esperando.MOTIVO);
                pendiente.put(staff.getUniqueId(), "buscar");
                staff.closeInventory();
                staff.sendMessage(Text.aviso("🔎 Escribe parte del nombre, "
                        + ChatColor.GRAY + "o '-' para quitar la búsqueda."));
                break;

            case "filtro":
                e.filtro = (e.filtro + 1) % 3;
                e.pagina = 0;
                abrirLista(staff, e);
                break;

            case "ir-hasta": {
                Player objetivo = objetivoDe(e);
                if (objetivo == null) {
                    plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
                    volver(staff);
                    return;
                }
                // El bypass es el mismo de antes: si el staff estuviera congelado
                // por un admin, esta es una orden suya y no se le corta.
                plugin.gestor().teletransportar(staff, objetivo.getLocation());
                staff.closeInventory();
                plugin.sonidos().exito(staff);
                staff.sendMessage(Text.exito("Has ido con " + ChatColor.WHITE
                        + objetivo.getName() + ChatColor.GREEN + "."));
                break;
            }

            case "sancionar": {
                if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_MUTE)) {
                    staff.sendMessage(Text.error("No tienes permiso para aplicar sanciones."));
                    return;
                }
                Player objetivo = objetivoDe(e);
                if (objetivo == null) {
                    plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
                    volver(staff);
                    return;
                }
                if (!plugin.puedeSancionar(staff, objetivo)) return;
                // El menu de sanciones se encarga, con el jugador ya elegido.
                plugin.menuSancion().sancionarA(staff, objetivo);
                break;
            }

            case "congelar":
                toggleCongelar(staff, e);
                break;

            case "traer-aqui": {
                Player objetivo = objetivoDe(e);
                if (objetivo == null) {
                    plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
                    volver(staff);
                    return;
                }
                plugin.gestor().teletransportar(objetivo, staff.getLocation());
                objetivo.setFallDistance(0f);
                staff.closeInventory();
                plugin.sonidos().exito(staff);
                staff.sendMessage(Text.exito("Has traído a " + ChatColor.WHITE
                        + objetivo.getName() + ChatColor.GREEN + "."));
                objetivo.sendMessage(Text.info("Un miembro del staff te ha traído a su sitio."));
                break;
            }

            case "silenciar":
                if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_MUTE)) {
                    staff.sendMessage(Text.error("No tienes permiso para silenciar."));
                    return;
                }
                if (e.objetivo != null && plugin.silencios().registro(e.objetivo) != null) {
                    lanzar(staff, e, "staffunmute");
                } else {
                    pedir(staff, e, "silencio",
                            "Escribe el tiempo y el motivo:5m insultos...");
                }
                break;
            case "expulsar":
                if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_MUTE)) {
                    staff.sendMessage(Text.error("No tienes permiso para expulsar jugadores."));
                    return;
                }
                pedir(staff, e, "expulsion", "Escribe el motivo de la expulsión.");
                break;
            case "jaula":
                if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_MUTE)) {
                    staff.sendMessage(Text.error("No tienes permiso para aplicar sanciones."));
                    return;
                }
                pedir(staff, e, "jaula", "Escribe celda, tiempo y motivo:1 30m robar.");
                break;
            case "notas":
                pedir(staff, e, "nota", "Escribe la nota interna.");
                break;
            case "vigilancia":
                pedir(staff, e, "vigilancia", "Escribe por qué lo pones en vigilancia.");
                break;

            case "historial": lanzar(staff, e, "staffhistory"); break;
            case "inventario": lanzar(staff, e, "staffinvsee"); break;
            case "chatlog": lanzar(staff, e, "staffchatlog"); break;
            case "snapshot": lanzar(staff, e, "staffsnapshot"); break;
            case "alts": lanzar(staff, e, "staffalt"); break;

            case "cerrar":
                estados.remove(staff.getUniqueId());
                staff.closeInventory();
                break;
            default:
                break;
        }
    }

    private void clickCabeza(Player staff, Estado e, String uuid) {
        Player objetivo;
        try {
            objetivo = staff.getServer().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException ex) {
            objetivo = null;
        }
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            abrirLista(staff, e);
            return;
        }
        abrirAcciones(staff, objetivo, e);
    }

    private Player objetivoDe(Estado e) {
        return e == null || e.objetivo == null ? null : Bukkit.getPlayer(e.objetivo);
    }

    /** Si tiene algo pendiente: sanción, congelado, silencio o vigilancia. */
    private boolean conProblema(Player p) {
        if (plugin.sanciones().tiene(p.getUniqueId())) return true;
        if (plugin.gestor().congelado(p)) return true;
        if (plugin.silencios().registro(p.getUniqueId()) != null) return true;
        return plugin.vigilancia().marca(p.getName()) != null;
    }

    /**
     * Congela o suelta al jugador segun como esté.
     *
     * Es la misma regla que /freeze: con una sanción activa no se suelta, porque
     * el reloj volveria a arrancar desde cero al no estar ya en la lista.
     */
    private void toggleCongelar(Player staff, Estado e) {
        Player objetivo = objetivoDe(e);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            volver(staff);
            return;
        }
        boolean congelado = plugin.gestor().congelado(objetivo);
        if (congelado && plugin.sanciones().tiene(objetivo.getUniqueId())) {
            plugin.sonidos().denegado(staff);
            staff.sendMessage(Text.error(objetivo.getName() + " tiene una sanción activa."));
            staff.sendMessage(Text.info("Para soltarlo: /staffsancion "
                    + objetivo.getName() + " off"));
            return;
        }
        if (congelado) {
            plugin.gestor().descongelar(objetivo);
            objetivo.sendMessage(Text.exito("Ya puedes moverte."));
            staff.sendMessage(Text.exito(objetivo.getName() + " ya puede moverse."));
        } else {
            plugin.gestor().congelar(objetivo, "", staff.getName());
            objetivo.sendMessage(Text.error("Has sido congelado por un miembro del staff. "
                    + "No te muevas."));
            staff.sendMessage(Text.aviso(objetivo.getName() + " está congelado."));
        }
        plugin.sonidos().exito(staff);
        abrirAcciones(staff, objetivo, e);
    }

    /** Pide por el chat el texto que hace falta para una accion. */
    private void pedir(Player staff, Estado e, String accion, String queEscribir) {
        esperando.put(staff.getUniqueId(), accion.equals("jaula")
                ? Esperando.JAULA : Esperando.MOTIVO);
        pendiente.put(staff.getUniqueId(), accion);
        staff.closeInventory();
        staff.sendMessage(Text.aviso("✎ " + queEscribir));
        staff.sendMessage(Text.info("Escríbelo en el chat, o 'cancelar'."));
    }

    /**
     * Lanza un comando de consulta tal cual.
     *
     * En vez de copiar aqui lo que hace /staffhistory o /staffinvsee, se lanza el
     * comando de siempre. Asi la cabeza es un atajo y no un segundo sitio donde
     * arreglar lo mismo dos veces.
     */
    private void lanzar(Player staff, Estado e, String comando) {
        Player objetivo = objetivoDe(e);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            volver(staff);
            return;
        }
        staff.closeInventory();
        Bukkit.dispatchCommand(staff, comando + " " + objetivo.getName());
    }

    /** Recoge lo escrito para la accion que estaba esperando. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alChat(AsyncPlayerChatEvent evento) {
        if (!esperando.containsKey(evento.getPlayer().getUniqueId())) return;
        if (evento.isCancelled()) return;

        String accionPendiente = pendiente.get(evento.getPlayer().getUniqueId());
        if (accionPendiente != null && List.of("silencio", "expulsion", "jaula").contains(accionPendiente)
                && !BaseCommand.tienePermisoAccion(evento.getPlayer(), BaseCommand.PERMISO_MUTE)) {
            esperando.remove(evento.getPlayer().getUniqueId());
            pendiente.remove(evento.getPlayer().getUniqueId());
            evento.setCancelled(true);
            evento.getPlayer().sendMessage(Text.error("Ya no tienes permiso para aplicar esa sanción."));
            return;
        }

        esperando.remove(evento.getPlayer().getUniqueId());
        String accion = pendiente.remove(evento.getPlayer().getUniqueId());
        evento.setCancelled(true);

        String texto = evento.getMessage().trim();
        Player staff = evento.getPlayer();
        if (accion == null) return;

        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            staff.sendMessage(Text.info("Cancelado."));
            volver(staff);
            return;
        }
        if (texto.isEmpty()) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No has escrito nada."));
            volver(staff);
            return;
        }

        // Buscar es el unico que no va sobre un jugador elegido: filtra la lista.
        if (accion.equals("buscar")) {
            Estado lista = estados.get(staff.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!staff.isOnline() || lista == null) return;
                lista.busqueda = texto.equals("-") ? "" : texto;
                lista.pagina = 0;
                abrirLista(staff, lista);
            });
            return;
        }

        Estado e = estados.get(staff.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!staff.isOnline()) return;
            Player objetivo = objetivoDe(e);
            if (objetivo == null) {
                plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
                return;
            }
            String nombre = objetivo.getName();
            String comando;
            switch (accion) {
                case "nota": comando = "staffnota " + nombre + " " + texto; break;
                case "vigilancia": comando = "staffvigilar " + nombre + " " + texto; break;
                case "silencio": comando = "staffmute " + nombre + " " + texto; break;
                case "expulsion": comando = "staffkick " + nombre + " " + texto; break;
                case "jaula": comando = "staffjaula " + nombre + " " + texto; break;
                default: return;
            }
            staff.closeInventory();
            Bukkit.dispatchCommand(staff, comando);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alArrastrar(InventoryDragEvent evento) {
        Estado e = estados.get(evento.getWhoClicked().getUniqueId());
        if (e == null) return;
        Inventory abierto = evento.getView().getTopInventory();
        if (abierto.equals(e.invLista) || abierto.equals(e.invAcciones)) {
            evento.setCancelled(true);
        }
    }

    /** Para cuando alguien se desconecta con el menu abierto. */
    public void limpiar(Player jugador) {
        estados.remove(jugador.getUniqueId());
        esperando.remove(jugador.getUniqueId());
        pendiente.remove(jugador.getUniqueId());
    }

    // ================= La ficha =================

    /** Lo que va en la lore de la cabeza de la lista. */
    private String[] ficha(Player p) {
        List<String> l = new ArrayList<>();
        l.add("");
        l.add(ChatColor.GRAY + "Mundo " + ChatColor.WHITE + p.getWorld().getName()
                + ChatColor.DARK_GRAY + "  " + xyz(p));
        l.add(ChatColor.GRAY + "Vida " + ChatColor.RED + Math.round(p.getHealth())
                + ChatColor.DARK_GRAY + "/" + ChatColor.RED + Math.round(p.getMaxHealth())
                + ChatColor.GRAY + "  Comida " + ChatColor.WHITE + p.getFoodLevel()
                + ChatColor.GRAY + "  XP " + ChatColor.WHITE + p.getLevel());
        l.add(ChatColor.GRAY + "Ping " + ChatColor.WHITE + p.getPing() + " ms"
                + ChatColor.GRAY + "  " + modo(p));
        l.add("");

        boolean limpio = true;
        List<String> estados = new ArrayList<>();
        SancionStore.Registro s = plugin.sanciones().obtener(p.getUniqueId());
        if (s != null) {
            estados.add(ChatColor.RED + "Sancionado: " + SancionStore.enLetras(s.restante)
                    + ChatColor.GRAY + " (" + s.motivo + ")");
        }
        if (plugin.gestor().congelado(p)) {
            StaffManager.Estado g = plugin.gestor().estado(p);
            estados.add(ChatColor.AQUA + "Congelado" + (g != null && !g.congeladoPor.isEmpty()
                    ? ChatColor.GRAY + " por " + g.congeladoPor : ""));
        }
        MuteStore.Registro m = plugin.silencios().registro(p.getUniqueId());
        if (m != null) {
            estados.add(ChatColor.YELLOW + "Silenciado"
                    + (m.hasta == Long.MAX_VALUE ? ChatColor.GRAY + " (perm)"
                    : ChatColor.GRAY + " hasta " + ConexionStore.hora(m.hasta)));
        }
        VigilanciaStore.Marca v = plugin.vigilancia().marca(p.getName());
        if (v != null) {
            estados.add(ChatColor.LIGHT_PURPLE + "En vigilancia" + ChatColor.GRAY + ": " + v.motivo);
        }
        if (plugin.gestor().vanish(p)) estados.add(ChatColor.GRAY + "Invisible");
        if (plugin.esStaff(p)) {
            StaffManager.Estado g = plugin.gestor().estado(p);
            estados.add(ChatColor.AQUA + "Staff"
                    + (g != null && !g.rango.isEmpty() ? ChatColor.GRAY + " " + g.rango : ""));
        }
        if (estados.isEmpty()) {
            l.add(ChatColor.GREEN + "Sin nada pendiente");
        } else {
            limpio = false;
            l.addAll(estados);
        }

        l.add("");
        int avisos = 0, silencios = 0, expulsiones = 0, sanciones = 0;
        for (SanctionStore.Entrada en : plugin.historial().de(p.getName())) {
            switch (en.tipo) {
                case "aviso": avisos++; break;
                case "silencio": silencios++; break;
                case "expulsion": expulsiones++; break;
                case "sancion": sanciones++; break;
                default: break;
            }
        }
        if (avisos + silencios + expulsiones + sanciones > 0) {
            l.add(ChatColor.GRAY + "Historial: " + ChatColor.WHITE + avisos + " avisos"
                    + ChatColor.DARK_GRAY + " · " + ChatColor.WHITE + silencios + " silencios"
                    + ChatColor.DARK_GRAY + " · " + ChatColor.WHITE + expulsiones + " expulsiones"
                    + ChatColor.DARK_GRAY + " · " + ChatColor.WHITE + sanciones + " sanciones");
        }
        int notas = plugin.notas().cantidad(p.getName());
        if (notas > 0) l.add(ChatColor.GRAY + "Notas internas: " + ChatColor.WHITE + notas);
        List<String> bloqueados = plugin.bloqueos().de(p.getName());
        if (!bloqueados.isEmpty()) {
            l.add(ChatColor.GRAY + "Sin acceso a " + ChatColor.WHITE + bloqueados.size()
                    + ChatColor.GRAY + " comandos");
        }

        String ip = plugin.ipDe(p);
        if (!ip.isEmpty()) {
            List<String> alts = plugin.conexiones().mismaIp(ip, p.getName());
            List<String> dentro = new ArrayList<>();
            for (String a : alts) {
                if (Bukkit.getPlayerExact(a) != null) dentro.add(a);
            }
            l.add(ChatColor.GRAY + "IP " + ChatColor.WHITE + ip
                    + (dentro.isEmpty() ? "" : ChatColor.GRAY + " · " + dentro.size()
                    + " alt" + (dentro.size() == 1 ? "" : "s") + " dentro"));
        }

        long entro = plugin.conexiones().entroDe(p.getName());
        if (entro > 0) {
            l.add(ChatColor.GRAY + "En esta sesion: " + ChatColor.WHITE
                    + ConexionStore.bonito(System.currentTimeMillis() - entro));
        }
        return l.toArray(new String[0]);
    }

    /** La ficha completa del menu de acciones. */
    private String[] fichaLarga(Player p) {
        List<String> l = new ArrayList<>();
        l.add("");
        l.add(ChatColor.GRAY + "Mundo " + ChatColor.WHITE + p.getWorld().getName());
        l.add(ChatColor.GRAY + xyz(p));
        l.add("");
        l.add(ChatColor.GRAY + "Vida " + ChatColor.RED + Math.round(p.getHealth())
                + ChatColor.DARK_GRAY + "/" + ChatColor.RED + Math.round(p.getMaxHealth())
                + ChatColor.GRAY + "   Comida " + ChatColor.WHITE + p.getFoodLevel()
                + ChatColor.GRAY + "   Saturacion " + ChatColor.WHITE + p.getSaturation());
        l.add(ChatColor.GRAY + "XP " + ChatColor.WHITE + p.getLevel()
                + ChatColor.GRAY + "   Ping " + ChatColor.WHITE + p.getPing() + " ms");
        l.add(ChatColor.GRAY + "Modo " + ChatColor.WHITE + modo(p)
                + ChatColor.GRAY + "   En el suelo " + (p.isOnGround() ? ChatColor.GREEN + "sí"
                : ChatColor.YELLOW + "no"));
        l.add("");
        l.add(ChatColor.GRAY + "IP " + ChatColor.WHITE + (plugin.ipDe(p).isEmpty()
                ? "sin IP" : plugin.ipDe(p)));
        l.add(ChatColor.GRAY + "Cuenta de " + ChatColor.WHITE
                + ConexionStore.bonito(System.currentTimeMillis() - p.getFirstPlayed()));
        l.add(ChatColor.GRAY + "Hoy lleva " + ChatColor.WHITE
                + ConexionStore.bonito(plugin.conexiones().jugadosHoy(p.getName()) * 60_000L));
        long entro = plugin.conexiones().entroDe(p.getName());
        l.add(ChatColor.GRAY + "Sesion de " + ChatColor.WHITE
                + (entro > 0 ? ConexionStore.bonito(System.currentTimeMillis() - entro) : "?"));
        return l.toArray(new String[0]);
    }

    /** El estado largo, para el menu de acciones. */
    private String[] estadoLargo(Player p) {
        List<String> l = new ArrayList<>();
        l.add("");
        SancionStore.Registro s = plugin.sanciones().obtener(p.getUniqueId());
        l.add(s == null ? ChatColor.GREEN + "Sin sancion activa"
                : ChatColor.RED + "Sancionado: " + SancionStore.enLetras(s.restante)
                + ChatColor.GRAY + " · " + s.motivo);
        if (s != null && !s.staff.isEmpty()) l.add(ChatColor.GRAY + "  por " + s.staff);

        StaffManager.Estado g = plugin.gestor().estado(p);
        l.add(plugin.gestor().congelado(p)
                ? ChatColor.AQUA + "Congelado" + (g != null && !g.congeladoPor.isEmpty()
                ? ChatColor.GRAY + " por " + g.congeladoPor : "")
                : ChatColor.DARK_GRAY + "No congelado");

        MuteStore.Registro m = plugin.silencios().registro(p.getUniqueId());
        l.add(m == null ? ChatColor.DARK_GRAY + "No silenciado"
                : ChatColor.YELLOW + "Silenciado" + ChatColor.GRAY + " · " + m.motivo);

        VigilanciaStore.Marca v = plugin.vigilancia().marca(p.getName());
        l.add(v == null ? ChatColor.DARK_GRAY + "No vigilado"
                : ChatColor.LIGHT_PURPLE + "En vigilancia" + ChatColor.GRAY + ": " + v.motivo);

        l.add(plugin.gestor().vanish(p) ? ChatColor.GRAY + "Invisible"
                : ChatColor.DARK_GRAY + "Visible");
        l.add("");
        int notas = plugin.notas().cantidad(p.getName());
        l.add(ChatColor.GRAY + "Notas internas: " + (notas == 0
                ? ChatColor.DARK_GRAY + "ninguna" : ChatColor.WHITE + "" + notas));
        List<String> bloqueados = plugin.bloqueos().de(p.getName());
        l.add(ChatColor.GRAY + "Comandos bloqueados: " + (bloqueados.isEmpty()
                ? ChatColor.DARK_GRAY + "ninguno"
                : ChatColor.WHITE + String.join(", ", bloqueados)));
        return l.toArray(new String[0]);
    }

    // ================= Ayudantes =================

    private String xyz(Player p) {
        org.bukkit.Location l = p.getLocation();
        return ChatColor.WHITE + "" + Math.round(l.getX()) + " "
                + Math.round(l.getY()) + " " + Math.round(l.getZ());
    }

    private String modo(Player p) {
        return p.getGameMode().name().toLowerCase(Locale.ROOT);
    }

    // ================= Iconos =================

    /**
     * Delegado: el borde y el relleno los pone MenuBase.crear(), que es el
     * unico sitio donde se deciden. Antes cada menu tenia su propia copia.
     */
    private Inventory base(String titulo) {
        return MenuBase.conMarco(titulo);
    }

    private ItemStack item(String id, Material m, String nombre, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nombre);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            if (id != null) {
                meta.getPersistentDataContainer().set(MARCA,
                        org.bukkit.persistence.PersistentDataType.STRING, id);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cabeza(Player jugador, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            try {
                ((SkullMeta) meta).setOwningPlayer(jugador);
            } catch (Throwable t) {
                // Si la API de skins cambia, sale sin cara pero el menu funciona.
            }
        }
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + jugador.getName());
            meta.getPersistentDataContainer().set(MARCA,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    "jug:" + jugador.getUniqueId());
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
