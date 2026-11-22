package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
 * El menu de sanciones.
 *
 * Antes /staffsancion era todo comando: /staffsancion Rob 30m Making spam. Con
 * cuatro cosas que recordar y un valor mal puesto, era facil banear por error o
 * quedarse sin sanctionar porque no sePoorEggnaba la duracion.
 *
 * Ahora es un recorrido de cuatro pasos:
 *   1. motivo: Hacks, Estafa/Robo, Spam u Otra razon
 *   2. jugador: todos los conectados, con su cara de verdad
 *   3. ajustes: tiempo, donde mandarlo y si se congela
 *   4. confirmar
 *
 * El staff escribe el tiempo o el motivo en el chat y el menu vuelve a abrirse
 * solo. Para eso esta clase escucha el chat: si el staff tiene algo pendiente,
 * lo que escribe se guarda como respuesta y no sale como mensaje.
 *
 * Todos los menus son de 54 huecos (seis filas, un cofre grande). Asi caben
 * juntos los botones, el marco y los jugadores sin que se corte nada.
 */
public final class MenuSancion implements Listener {

    /** Todos los menus: seis filas, como un cofre grande. */
    private static final int TAM = 54;

    /** Huecos libres dentro del marco (cuatro filas por siete columnas). */
    private static final int[] INTERIOR = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final int PAGINA = 28;   // jugadores o mundos por pagina

    /** Lo que esta esperando que escriba en el chat. */
    private enum Esperando { NADA, MOTIVO, JUGADOR, TIEMPO }

    /** Una sancion a medio montar. */
    private static final class Estado {
        String motivo = "";
        String jugadorNombre = "";
        UUID jugadorUuid;
        long tiempo;
        String mundo = "";               // "" = donde ya esta
        boolean congelar = true;
        /**
         * Como corre el tiempo de la sancion.
         *
         * true (por defecto) = reloj REAL: sigue contando aunque el jugador se
         * desconecte y se le mantiene baneado hasta cumplir. false = reloj en
         * pausa, que es como funcionaba al principio.
         */
        boolean relojReal = true;
        /** Si es true, los botones "volver" salen al el panel de staff y no al
         * selector de jugadores, porque el jugador ya venia elegido desde ahi. */
        boolean volverAlGlobal;
        int pagina;

        Inventory invMotivo;
        Inventory invJugadores;
        Inventory invAjustes;
        Inventory invMundos;
    }

    private final OdisseyStaff plugin;
    // ConcurrentHashMap y no HashMap: el listener del chat (AsyncPlayerChatEvent) lee
    // y quita estos mapas desde el hilo asincrono de Bukkit.
    private final Map<UUID, Estado> estados = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Esperando> esperando = new java.util.concurrent.ConcurrentHashMap<>();

    /** La etiqueta que lleva cada item para saber que boton es al pulsar. */
    private final org.bukkit.NamespacedKey MARCA;

    public MenuSancion(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.MARCA = new org.bukkit.NamespacedKey(plugin, "menu_sancion");
    }

    public void registrar() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Estado estado(Player staff) {
        return estados.computeIfAbsent(staff.getUniqueId(), k -> new Estado());
    }

    /**
     * Empieza una sancion con el jugador YA elegido.
     *
     * Es lo que llama el panel de staff al pulsar "Sancionar": no tiene sentido volver
     * a preguntar de quien es, porque se acaba de pulsar su cabeza.
     */
    public void sancionarA(Player staff, Player objetivo) {
        Estado e = estado(staff);
        e.volverAlGlobal = true;
        e.jugadorNombre = objetivo.getName();
        e.jugadorUuid = objetivo.getUniqueId();
        e.motivo = "";
        e.tiempo = 0;
        e.mundo = "";
        e.congelar = true;
        e.relojReal = true;
        e.pagina = 0;
        abrirMotivo(staff, e);
    }
    public void abrirRaiz(Player staff) {
        Estado e = new Estado();
        estados.put(staff.getUniqueId(), e);
        abrirMotivo(staff, e);
    }

    // ================= Fondo comun =================

    /**
     * Delegado: el borde y el relleno los pone MenuBase.crear(), que es el
     * unico sitio donde se deciden. Antes cada menu tenia su propia copia y
     * eran tres versiones distintas de lo mismo.
     */
    private Inventory base(String titulo) {
        Inventory inv = MenuBase.conMarco(titulo);
        return inv;
    }

    // ================= 1. Motivo =================

    private void abrirMotivo(Player staff, Estado e) {
        e.invMotivo = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "SANCION "
                + ChatColor.DARK_GRAY + "· " + ChatColor.RED + "Motivo");

        e.invMotivo.setItem(19, item("hacks", Material.DIAMOND_SWORD,
                ChatColor.RED + "⚔ Hacks / Cheats",
                ChatColor.GRAY + "X-ray, fly, kill aura, Reach,",
                ChatColor.GRAY + "bots, exploits y trampas alike."));

        e.invMotivo.setItem(20, item("insultos", Material.FEATHER,
                ChatColor.YELLOW + "✎ Insultos / Faltas de respeto",
                ChatColor.GRAY + "Groserías, ofensas o faltas",
                ChatColor.GRAY + "de respeto a otros jugadores."));

        e.invMotivo.setItem(21, item("robo", Material.GOLD_INGOT,
                ChatColor.GOLD + "⚑ Estafa / Robo",
                ChatColor.GRAY + "Robo de bases, dupes, cuentas",
                ChatColor.GRAY + "prestadas, estafas con trampas."));

        e.invMotivo.setItem(22, item("amenazas", Material.IRON_SWORD,
                ChatColor.RED + "⚔ Amenazas",
                ChatColor.GRAY + "Amenazas de daño real, doxing",
                ChatColor.GRAY + "o acoso fuera del juego."));

        e.invMotivo.setItem(23, item("spam", Material.PAPER,
                ChatColor.YELLOW + "✉ Spam",
                ChatColor.GRAY + "Mensajes repetidos, mayúsculas",
                ChatColor.GRAY + "continuas, publicidad."));

        e.invMotivo.setItem(25, item("otra", Material.WRITABLE_BOOK,
                ChatColor.AQUA + "✎ Otra razón",
                ChatColor.GRAY + "La escribes tú en el chat.",
                ChatColor.DARK_GRAY + "Vale cualquier cosa:"));

        e.invMotivo.setItem(13, item(null, Material.BOOK,
                ChatColor.DARK_RED + "Motivo de la sanción",
                ChatColor.GRAY + "Elige por qué se le va a sancionar.",
                ChatColor.DARK_GRAY + "La razón se le enseña al",
                ChatColor.DARK_GRAY + "jugador al aplicarla."));

        if (e.volverAlGlobal) {
            e.invMotivo.setItem(48, item("volver-jugadores", Material.ARROW,
                    ChatColor.YELLOW + "◀ Volver a " + e.jugadorNombre,
                    ChatColor.GRAY + "Sin elegir motivo."));
        }

        e.invMotivo.setItem(49, item("cancelar", Material.BARRIER,
                ChatColor.RED + "✖ Cancelar",
                ChatColor.GRAY + "Cierra el menú sin sancionar a nadie."));

        staff.openInventory(e.invMotivo);
    }

    // ================= 2. Jugador =================

    private void abrirJugadores(Player staff, Estado e) {
        List<Player> otros = new ArrayList<>();
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (!p.equals(staff)) otros.add(p);
        }

        int paginas = (int) Math.ceil(otros.size() / (double) PAGINA);
        // Sin este tope, "siguiente" subia la pagina sin fin y salian hojas
        // vacias con contadores imposibles ("3/2").
        e.pagina = Math.min(e.pagina, Math.max(0, paginas - 1));
        String cuenta = otros.size() + " conectados"
                + (paginas > 1 ? " · " + (e.pagina + 1) + "/" + paginas : "");
        e.invJugadores = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "SANCION "
                + ChatColor.DARK_GRAY + "· " + ChatColor.RED + "Jugador "
                + ChatColor.GRAY + cuenta);

        if (otros.isEmpty()) {
            e.invJugadores.setItem(22, item(null, Material.GRAY_DYE,
                    ChatColor.RED + "No hay nadie más dentro",
                    ChatColor.GRAY + "Espera a que entre alguien"));
        }

        int desde = e.pagina * PAGINA;
        int fin = Math.min(otros.size(), desde + PAGINA);
        for (int i = desde; i < fin; i++) {
            Player p = otros.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + p.getWorld().getName());
            lore.add(ChatColor.GRAY + "Vida: " + ChatColor.WHITE + (int) p.getHealth()
                    + ChatColor.GRAY + "   Ping: " + ChatColor.WHITE + p.getPing());
            lore.add("");
            if (plugin.sanciones().tiene(p.getUniqueId())) {
                lore.add(ChatColor.RED + "✖ Ya tiene una sanción activa");
            } else if (plugin.gestor().congelado(p)) {
                lore.add(ChatColor.RED + "✖ Está congelado");
            } else if (plugin.esStaff(p)) {
                lore.add(ChatColor.GOLD + "⚠ Es del staff");
                lore.add(ChatColor.DARK_GRAY + "Necesitas odissey.admin.");
            } else {
                lore.add(ChatColor.GREEN + "✔ Sin sanciones");
            }
            lore.add(ChatColor.DARK_GRAY + "Click para elegirlo.");
            e.invJugadores.setItem(INTERIOR[i - desde], cabeza(p, lore.toArray(new String[0])));
        }

        // Paginacion, solo si de verdad hay mas jugadores que huecos.
        if (otros.size() > PAGINA) {
            e.invJugadores.setItem(45, item("atras", Material.ARROW,
                    ChatColor.YELLOW + "◀ Anterior"));
            e.invJugadores.setItem(53, item("siguiente", Material.ARROW,
                    ChatColor.YELLOW + "Siguiente ▶"));
        }

        // En el 46 (borde de abajo) y no en el 13: el 13 esta dentro de INTERIOR
        // y pisaba al 4º jugador de cada pagina.
        e.invJugadores.setItem(46, item("volver-motivo", Material.DIAMOND_SWORD,
                ChatColor.RED + "⚔ Volver al motivo",
                ChatColor.GRAY + "Cambia la razón de la sanción.",
                ChatColor.DARK_GRAY + "Elige una cabeza de las de abajo."));
        e.invJugadores.setItem(48, item("cancelar", Material.BARRIER,
                ChatColor.RED + "✖ Cancelar"));

        staff.openInventory(e.invJugadores);
    }

    // ================= 3. Ajustes =================

    private void abrirAjustes(Player staff, Estado e) {
        e.invAjustes = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "SANCION "
                + ChatColor.DARK_GRAY + "· " + ChatColor.RED + "Ajustes");

        e.invAjustes.setItem(31, item(null, Material.PAPER,
                ChatColor.DARK_RED + "Resumen",
                ChatColor.GRAY + "Motivo: " + (e.motivo.isEmpty()
                        ? ChatColor.YELLOW + "sin poner" : ChatColor.WHITE + e.motivo),
                ChatColor.GRAY + "Jugador: " + (e.jugadorNombre.isEmpty()
                        ? ChatColor.YELLOW + "sin poner" : ChatColor.WHITE + e.jugadorNombre),
                ChatColor.GRAY + "Reloj: " + (e.relojReal
                        ? ChatColor.GREEN + "real" : ChatColor.YELLOW + "pausa")
                + ChatColor.GRAY + " · Congelar: "
                + (e.congelar ? ChatColor.AQUA + "sí" : ChatColor.YELLOW + "no")));

        e.invAjustes.setItem(20, item("tiempo", Material.CLOCK,
                e.tiempo > 0
                        ? ChatColor.GREEN + "⏱ Tiempo: " + SancionStore.enLetras(e.tiempo)
                        : ChatColor.YELLOW + "⏱ Tiempo: sin poner",
                ChatColor.GRAY + "Click y escríbelo en el chat.",
                ChatColor.DARK_GRAY + "30s · 10m · 2h · 1d",
                ChatColor.DARK_GRAY + "Con perm no tiene límite."));

        // Plantillas de tiempo: lo mas habitual a un solo click, para no tener
        // que soltar el menu y escribir. El reloj de arriba sigue estando para
        // los tiempos raros.
        e.invAjustes.setItem(45, item("tpl:30m", Material.CLOCK,
                ChatColor.GRAY + "Tiempo: 30 minutos"));
        e.invAjustes.setItem(46, item("tpl:1h", Material.CLOCK,
                ChatColor.GRAY + "Tiempo: 1 hora"));
        e.invAjustes.setItem(47, item("tpl:1d", Material.CLOCK,
                ChatColor.GRAY + "Tiempo: 1 día"));
        e.invAjustes.setItem(48, item("tpl:7d", Material.CLOCK,
                ChatColor.GRAY + "Tiempo: 7 días"));

        e.invAjustes.setItem(22, item("congelar", e.congelar ? Material.ICE : Material.MELON,
                e.congelar ? ChatColor.AQUA + "❄ Congelar: SÍ"
                           : ChatColor.YELLOW + "❄ Congelar: NO",
                ChatColor.GRAY + "Click para cambiarlo.",
                "",
                e.congelar
                        ? ChatColor.AQUA + "Se queda quieto y a oscuras."
                        : ChatColor.YELLOW + "Puede andar y hablar.",
                ChatColor.DARK_GRAY + "En los dos casos no puede usar",
                ChatColor.DARK_GRAY + "comandos ni abrir cofres."));

        e.invAjustes.setItem(24, item("destino", Material.COMPASS,
                e.mundo.isEmpty()
                        ? ChatColor.YELLOW + "📍 Destino: donde está"
                        : ChatColor.GREEN + "📍 Destino: " + e.mundo,
                ChatColor.GRAY + "Click para elegir el mundo."));

        // El reloj: lo que decide si la sancion se le acaba a un jugador que
        // sale y no vuelve. Si esta en pausa y el jugador desconecta, el tiempo
        // se queda ahi para siempre; con reloj real, ademas, se le bannea hasta
        // que se cumpla y no puede esquivarla volviendo en otro momento.
        e.invAjustes.setItem(26, item("reloj", e.relojReal ? Material.REPEATER : Material.COMPARATOR,
                e.relojReal
                        ? ChatColor.GREEN + "⏱ Reloj: REAL"
                        : ChatColor.YELLOW + "⏱ Reloj: PAUSA",
                ChatColor.GRAY + "Click para cambiarlo.",
                "",
                e.relojReal
                        ? ChatColor.GREEN + "Corre aunque se desconecte."
                        : ChatColor.YELLOW + "Se para al salirse.",
                e.relojReal
                        ? ChatColor.DARK_GRAY + "No podra volver hasta cumplirla."
                        : ChatColor.DARK_GRAY + "Puede volver y seguir serviendo."));

        boolean listo = e.tiempo > 0 && !e.jugadorNombre.isEmpty();
        String quien = e.jugadorNombre.isEmpty() ? "nadie" : e.jugadorNombre;
        String[] loreConfirmar;
        if (listo) {
            loreConfirmar = new String[]{
                    ChatColor.GRAY + "Sanciona a " + ChatColor.WHITE + e.jugadorNombre,
                    ChatColor.GRAY + "por " + ChatColor.WHITE + e.motivo + ChatColor.GRAY + ".",
                    "",
                    ChatColor.DARK_GRAY + "No se puede deshacer con este",
                    ChatColor.DARK_GRAY + "menú. Para corregirlo, /staffsancion",
                    ChatColor.DARK_GRAY + "<jugador> off."};
        } else {
            loreConfirmar = new String[]{
                    ChatColor.RED + "Falta " + (e.tiempo <= 0 ? "el tiempo" : "el jugador"),
                    ChatColor.DARK_GRAY + "Está elegido: " + quien};
        }
        e.invAjustes.setItem(40, item("confirmar",
                listo ? Material.LIME_CONCRETE : Material.BARRIER,
                listo ? ChatColor.GREEN + "✔ CONFIRMAR SANCIÓN"
                      : ChatColor.RED + "✖ FALTA TIEMPO",
                loreConfirmar));

        // El 13 es la cabecera de este menu: quien va a ser sancionado, con su
        // tiempo y su motivo a la vista. Si no hay jugador todavia, se lo
        // recuerda en vez de dejar el hueco vacio.
        e.invAjustes.setItem(13, item(null, Material.PLAYER_HEAD,
                e.jugadorNombre.isEmpty()
                        ? ChatColor.YELLOW + "Sin jugador todavía"
                        : ChatColor.WHITE + e.jugadorNombre,
                e.jugadorNombre.isEmpty()
                        ? ChatColor.DARK_GRAY + "Elige a alguien en el paso anterior."
                        : ChatColor.GRAY + "Motivo: "
                        + ChatColor.YELLOW + (e.motivo.isEmpty() ? "(sin elegir)" : e.motivo),
                ChatColor.GRAY + "Tiempo: " + ChatColor.YELLOW
                        + (e.tiempo <= 0 ? "(sin poner)" : SancionStore.enLetras(e.tiempo))));

        e.invAjustes.setItem(38, item("volver-jugadores", Material.PLAYER_HEAD,
                ChatColor.YELLOW + "◀ Cambiar de jugador",
                ChatColor.GRAY + "Vuelve a la lista."));

        e.invAjustes.setItem(39, item("volver-motivo", Material.DIAMOND_SWORD,
                ChatColor.RED + "⚔ Cambiar el motivo"));

        e.invAjustes.setItem(41, item("otra", Material.WRITABLE_BOOK,
                ChatColor.AQUA + "✎ Escribir otro motivo",
                ChatColor.GRAY + "Lo escribes en el chat."));

        e.invAjustes.setItem(42, item("otro-jugador", Material.PLAYER_HEAD,
                ChatColor.AQUA + "✎ Escribir otro jugador",
                ChatColor.GRAY + "Si no sale en la lista."));

        e.invAjustes.setItem(49, item("cancelar", Material.BARRIER,
                ChatColor.RED + "✖ Cancelar"));

        staff.openInventory(e.invAjustes);
    }

    // ================= 4. Mundos =================

    private void abrirMundos(Player staff, Estado e) {
        List<World> mundos = Bukkit.getWorlds();
        int paginasMundos = (int) Math.ceil(mundos.size() / (double) PAGINA);
        e.pagina = Math.min(e.pagina, Math.max(0, paginasMundos - 1));
        String cuenta = mundos.size() + " mundos"
                + (paginasMundos > 1 ? " · " + (e.pagina + 1) + "/" + paginasMundos : "");
        e.invMundos = base(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "SANCION "
                + ChatColor.DARK_GRAY + "· " + ChatColor.RED + "Mundo "
                + ChatColor.GRAY + cuenta);

        // En el 50 (borde de abajo) y no en el 22: el 22 esta dentro de INTERIOR
        // y, con 12 mundos o mas, un mundo lo tapaba.
        e.invMundos.setItem(50, item("mismo-mundo", e.mundo.isEmpty()
                        ? Material.LIME_CONCRETE : Material.PAPER,
                e.mundo.isEmpty()
                        ? ChatColor.GREEN + "✔ Donde está ya"
                        : ChatColor.GRAY + "Donde está ya",
                ChatColor.GRAY + "No lo mueves de sitio.",
                e.mundo.isEmpty() ? ChatColor.DARK_GRAY + "Está elegido." : ""));

        int desde = e.pagina * PAGINA;
        int fin = Math.min(mundos.size(), desde + PAGINA);
        for (int i = desde; i < fin; i++) {
            World w = mundos.get(i);
            boolean elegido = e.mundo.equals(w.getName());
            ItemStack it = item("mundo:" + w.getName(), Material.GRASS_BLOCK,
                    (elegido ? ChatColor.GREEN : ChatColor.AQUA)
                            + "🌍 " + w.getName(),
                    ChatColor.GRAY + "Jugadores: " + ChatColor.WHITE + w.getPlayers().size(),
                    ChatColor.GRAY + "Ambiente: " + ChatColor.WHITE
                            + w.getEnvironment().name().toLowerCase(Locale.ROOT),
                    ChatColor.GRAY + "Tiempo: " + ChatColor.WHITE
                            + (w.getTime() < 6000 || w.getTime() > 18000 ? "día" : "noche"),
                    elegido ? ChatColor.GREEN + "✔ Elegido." : ChatColor.DARK_GRAY + "Click para mandar allí.");
            if (elegido) brillar(it);
            e.invMundos.setItem(INTERIOR[i - desde], it);
        }

        if (mundos.size() > PAGINA) {
            e.invMundos.setItem(45, item("atras", Material.ARROW,
                    ChatColor.YELLOW + "◀ Anterior"));
            e.invMundos.setItem(53, item("siguiente", Material.ARROW,
                    ChatColor.YELLOW + "Siguiente ▶"));
        }

        e.invMundos.setItem(46, item("volver-ajustes", Material.COMPASS,
                ChatColor.YELLOW + "◀ Volver a los ajustes",
                ChatColor.GRAY + "Sin cambiar nada."));
        e.invMundos.setItem(48, item("cancelar", Material.BARRIER,
                ChatColor.RED + "✖ Cancelar"));

        staff.openInventory(e.invMundos);
    }

    // ================= Clicks =================

    @EventHandler(priority = EventPriority.HIGH)
    public void alClick(InventoryClickEvent evento) {
        Estado e = estados.get(evento.getWhoClicked().getUniqueId());
        if (e == null) return;
        Inventory abierto = evento.getView().getTopInventory();
        boolean mio = abierto.equals(e.invMotivo) || abierto.equals(e.invJugadores)
                || abierto.equals(e.invAjustes) || abierto.equals(e.invMundos);
        if (!mio) return;

        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (evento.getRawSlot() < 0 || evento.getRawSlot() >= abierto.getSize()) return;
        ItemStack clicked = evento.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(MARCA, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null || id.isEmpty()) return;   // relleno o marco: no hacen nada
        plugin.sonidos().click(staff);

        if (id.startsWith("jug:")) {
            clickJugador(staff, e, id.substring(4));
            return;
        }
        if (id.startsWith("mundo:")) {
            e.mundo = id.substring(6);
            staff.sendMessage(Text.exito("Destino: " + ChatColor.WHITE + e.mundo));
            abrirAjustes(staff, e);
            return;
        }
        if (id.startsWith("tpl:")) {
            long ms = plugin.leerTiempoEnMs(id.substring(4));
            if (ms > 0) {
                e.tiempo = ms;
                staff.sendMessage(Text.exito("⏱ Tiempo: "
                        + ChatColor.WHITE + SancionStore.enLetras(ms)));
            }
            abrirAjustes(staff, e);
            return;
        }

        switch (id) {
            case "hacks": e.motivo = "Hacks / Cheats"; trasElegirMotivo(staff, e); break;
            case "insultos": e.motivo = "Insultos / Faltas de respeto"; trasElegirMotivo(staff, e); break;
            case "robo": e.motivo = "Estafa / Robo"; trasElegirMotivo(staff, e); break;
            case "amenazas": e.motivo = "Amenazas"; trasElegirMotivo(staff, e); break;
            case "spam": e.motivo = "Spam"; trasElegirMotivo(staff, e); break;
            case "otra":
                staff.closeInventory();
                esperando.put(staff.getUniqueId(), Esperando.MOTIVO);
                staff.sendMessage(Text.aviso("✎ Escribe el motivo en el chat "
                        + "(o pon 'cancelar' para dejarlo)"));
                break;
            case "otro-jugador":
                staff.closeInventory();
                esperando.put(staff.getUniqueId(), Esperando.JUGADOR);
                staff.sendMessage(Text.aviso("✎ Escribe el nombre del jugador "
                        + "(o pon 'cancelar')"));
                break;

            case "tiempo":
                staff.closeInventory();
                esperando.put(staff.getUniqueId(), Esperando.TIEMPO);
                staff.sendMessage(Text.aviso("⏱ Escribe el tiempo en el chat: "
                        + ChatColor.GRAY + "30m, 2h, 1d... o 'perm' sin límite"));
                break;
            case "congelar":
                e.congelar = !e.congelar;
                abrirAjustes(staff, e);
                break;
            case "reloj":
                e.relojReal = !e.relojReal;
                staff.sendMessage(e.relojReal
                        ? Text.exito("Reloj REAL: corre aunque se desconecte "
                                + ChatColor.GRAY + "y no podrá volver hasta cumplirla.")
                        : Text.aviso("Reloj en PAUSA: se para al salirse "
                                + ChatColor.GRAY + "y podrá volver cuando quiera."));
                abrirAjustes(staff, e);
                break;
            case "destino":
                e.pagina = 0;
                abrirMundos(staff, e);
                break;
            case "mismo-mundo":
                e.mundo = "";
                staff.sendMessage(Text.info("Destino: donde está ya"));
                abrirAjustes(staff, e);
                break;
            case "confirmar":
                if (e.tiempo <= 0) {
                    plugin.sonidos().error(staff);
                    staff.sendMessage(Text.error("Pon primero el tiempo."));
                    abrirAjustes(staff, e);
                    return;
                }
                confirmar(staff, e);
                break;
            case "atras":
                if (e.pagina > 0) e.pagina--;
                reabrir(staff, e, abierto);
                break;
            case "siguiente":
                e.pagina++;
                reabrir(staff, e, abierto);
                break;

            case "volver-motivo": e.pagina = 0; abrirMotivo(staff, e); break;
            case "volver-jugadores": e.pagina = 0; abrirOrigen(staff, e); break;
            case "volver-ajustes": e.pagina = 0; abrirAjustes(staff, e); break;
            case "cancelar":
                estados.remove(staff.getUniqueId());
                staff.closeInventory();
                staff.sendMessage(Text.info("Sanción cancelada."));
                break;
            default:
                break;
        }
    }

    /**
     * A donde vuelve el boton "volver" del flujo de sancion.
     *
     * Si la sancion se empezo desde el panel de staff con el jugador ya elegido, volver
     * lleva a la lista de jugadores. Si se empezo con /staffsancion a pelo,
     * lleva al selector de este mismo menu.
     */
    private void abrirOrigen(Player staff, Estado e) {
        if (e.volverAlGlobal) {
            plugin.menuJugadores().volver(staff);
            return;
        }
        abrirJugadores(staff, e);
    }

    /**
     * Despues de elegir el motivo: a donde toca.
     *
     * Si la sancion viene de el panel de staff (o de cualquier sitio donde el jugador ya
     * estuviera elegido) se va directo a los ajustes, sin volver a preguntar de
     * quien es. Si no, se pasa a elegir jugador.
     */
    private void trasElegirMotivo(Player staff, Estado e) {
        if (e.volverAlGlobal && e.jugadorUuid != null) {
            abrirAjustes(staff, e);
            return;
        }
        abrirJugadores(staff, e);
    }
    /** Al cambiar de pagina hay que saber que menu era. */
    private void reabrir(Player staff, Estado e, Inventory abierto) {
        if (abierto.equals(e.invJugadores)) abrirJugadores(staff, e);
        else if (abierto.equals(e.invMundos)) abrirMundos(staff, e);
        else if (abierto.equals(e.invAjustes)) abrirAjustes(staff, e);
        else abrirMotivo(staff, e);
    }

    private void clickJugador(Player staff, Estado e, String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException ex) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            abrirJugadores(staff, e);
            return;
        }
        Player objetivo = staff.getServer().getPlayer(id);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            abrirJugadores(staff, e);
            return;
        }
        e.jugadorNombre = objetivo.getName();
        e.jugadorUuid = objetivo.getUniqueId();
        e.pagina = 0;
        staff.sendMessage(Text.exito("Jugador: " + ChatColor.WHITE + objetivo.getName()));
        abrirAjustes(staff, e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alArrastrar(InventoryDragEvent evento) {
        Estado e = estados.get(evento.getWhoClicked().getUniqueId());
        if (e == null) return;
        Inventory abierto = evento.getView().getTopInventory();
        if (abierto.equals(e.invMotivo) || abierto.equals(e.invJugadores)
                || abierto.equals(e.invAjustes) || abierto.equals(e.invMundos)) {
            evento.setCancelled(true);
        }
    }

    // ================= Lo que escribe en el chat =================

    /**
     * Si el staff tiene algo pendiente, su mensaje es la respuesta.
     *
     * Se cancela el chat para que "30m" no aparezca luego en el global como un
     * mensaje raro de un admin.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alChat(org.bukkit.event.player.AsyncPlayerChatEvent evento) {
        if (!esperando.containsKey(evento.getPlayer().getUniqueId())) return;

        // Los comandos de mute / congelado / silencios también usan el chat para
        // decir cosas. Si uno de esos listeners ya ha cancelado, es porque el
        // mensaje era para él, no para este menu.
        if (evento.isCancelled()) return;

        Esperando que = esperando.remove(evento.getPlayer().getUniqueId());
        if (que == null) return;
        evento.setCancelled(true);

        String texto = evento.getMessage().trim();
        Player staff = evento.getPlayer();
        Estado e = estados.get(staff.getUniqueId());
        if (e == null) return;

        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            estados.remove(staff.getUniqueId());
            staff.sendMessage(Text.info("Sanción cancelada."));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!staff.isOnline()) return;
            switch (que) {
                case MOTIVO:
                    if (texto.length() < 3) {
                        plugin.sonidos().error(staff);
                        staff.sendMessage(Text.error("El motivo es demasiado corto."));
                        abrirJugadores(staff, e);
                        return;
                    }
                    e.motivo = texto;
                    staff.sendMessage(Text.exito("✎ Motivo: " + ChatColor.WHITE + texto));
                    trasElegirMotivo(staff, e);
                    break;
                case JUGADOR: {
                    Player objetivo = buscar(staff, texto);
                    if (objetivo == null) {
                        plugin.sonidos().error(staff);
                        staff.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
                        abrirAjustes(staff, e);
                        return;
                    }
                    e.jugadorNombre = objetivo.getName();
                    e.jugadorUuid = objetivo.getUniqueId();
                    staff.sendMessage(Text.exito("Jugador: " + ChatColor.WHITE + objetivo.getName()));
                    abrirAjustes(staff, e);
                    break;
                }
                case TIEMPO: {
                    long ms = leerTiempo(texto);
                    if (ms <= 0) {
                        plugin.sonidos().error(staff);
                        staff.sendMessage(Text.error("Eso no es un tiempo. "
                                + ChatColor.GRAY + "Ejemplos: 30s, 10m, 2h, 1d, perm"));
                        abrirAjustes(staff, e);
                        return;
                    }
                    e.tiempo = ms;
                    staff.sendMessage(Text.exito("⏱ Tiempo: "
                            + ChatColor.WHITE + SancionStore.enLetras(ms)));
                    abrirAjustes(staff, e);
                    break;
                }
                default:
                    break;
            }
        });
    }

    private Player buscar(Player staff, String texto) {
        Player exacto = staff.getServer().getPlayerExact(texto);
        if (exacto != null) return exacto;
        String busqueda = texto.toLowerCase(Locale.ROOT);
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).equals(busqueda)) return p;
        }
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(busqueda)) return p;
        }
        return null;
    }

    private long leerTiempo(String texto) {
        if (texto.equalsIgnoreCase("perm") || texto.equalsIgnoreCase("infinito")) {
            return Long.MAX_VALUE / 2;
        }
        return plugin.leerTiempoEnMs(texto);
    }

    public boolean esperandoRespuesta(Player jugador) {
        return esperando.containsKey(jugador.getUniqueId());
    }

    /** Para cuando alguien se desconecta a media sanción. */
    public void limpiar(Player jugador) {
        estados.remove(jugador.getUniqueId());
        esperando.remove(jugador.getUniqueId());
    }

    // ================= Aplicar =================

    private void confirmar(Player staff, Estado e) {
        Player objetivo = e.jugadorUuid == null ? null : staff.getServer().getPlayer(e.jugadorUuid);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está dentro. Se ha cancelado."));
            estados.remove(staff.getUniqueId());
            staff.closeInventory();
            return;
        }
        if (!plugin.puedeSancionar(staff, objetivo)) return;

        if (plugin.sanciones().tiene(objetivo.getUniqueId())) {
            SancionStore.Registro actual = plugin.sanciones().obtener(objetivo.getUniqueId());
            // No se le pone una sancion encima de otra, pero si se le puede
            // cambiar el modo del reloj. Es justo lo que hace falta cuando el
            // sancionado se desconecto con el reloj en pausa y el tiempo se
            // quedo congelado para siempre.
            if (actual.relojReal != e.relojReal) {
                plugin.sanciones().cambiarReloj(actual, e.relojReal);
                plugin.sanciones().guardar();
                staff.sendMessage(Text.aviso(objetivo.getName()
                        + " ya tenía una sanción: le quedan "
                        + SancionStore.enLetras(actual.restante) + "."));
                staff.sendMessage(e.relojReal
                        ? Text.exito("  Ahora su reloj es REAL: corre aunque "
                                + ChatColor.GRAY + "esté fuera y no podrá volver hasta cumplirla.")
                        : Text.aviso("  Ahora su reloj está en PAUSA: se para al salirse."));
                return;
            }
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error(objetivo.getName() + " ya tiene una sanción: le quedan "
                    + SancionStore.enLetras(actual.restante) + "."));
            return;
        }

        // 1) Destino
        if (!e.mundo.isEmpty()) {
            World w = plugin.getServer().getWorld(e.mundo);
            if (w == null) {
                staff.sendMessage(Text.error("El mundo " + e.mundo
                        + " no existe. Se queda donde estaba."));
            } else {
                plugin.gestor().teletransportar(objetivo, w.getSpawnLocation());
                objetivo.setFallDistance(0f);
                staff.sendMessage(Text.info("Enviado a " + ChatColor.WHITE + w.getName() + "."));
            }
        }

        // 2) Sanción con el tiempo, el modo de congelar y el modo de reloj
        SancionStore.Registro r = e.relojReal
                ? plugin.sanciones().ponerReal(objetivo, e.tiempo, e.motivo, staff.getName())
                : plugin.sanciones().poner(objetivo, e.tiempo, e.motivo, staff.getName());
        r.congeladoSi = e.congelar;
        plugin.sanciones().guardar();

        if (e.congelar) {
            plugin.sanciones().congelar(objetivo, r);
        } else {
            plugin.gestor().descongelar(objetivo);
            objetivo.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        }
        plugin.sanciones().mostrar(objetivo, r);

        // 3) Avisos
        objetivo.sendMessage(Text.aviso("Has sido sancionado por "
                + ChatColor.WHITE + staff.getName() + ChatColor.YELLOW + "."));
        objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + e.motivo));
        objetivo.sendMessage(Text.info("Tiempo restante: "
                + ChatColor.WHITE + SancionStore.enLetras(e.tiempo) + ChatColor.GRAY + "."));
        objetivo.sendMessage(e.relojReal
                ? Text.info("El reloj corre aunque te desconectes: "
                        + "si sales, no podrás volver hasta cumplirla.")
                : Text.info("El reloj solo corre mientras estés conectado."));

        staff.sendMessage(Text.exito("✔ Sancionado "
                + ChatColor.WHITE + objetivo.getName()
                + ChatColor.GREEN + " por " + ChatColor.WHITE + SancionStore.enLetras(e.tiempo)
                + ChatColor.GREEN + ": " + ChatColor.WHITE + e.motivo
                + (e.mundo.isEmpty() ? "" : ChatColor.GRAY + " · enviado a " + e.mundo)
                + (e.congelar ? "" : ChatColor.GRAY + " · sin congelar")
                + (e.relojReal ? ChatColor.GRAY + " · reloj real" : ChatColor.GRAY + " · reloj en pausa")));
        plugin.sonidos().exito(staff);

        plugin.getLogger().info("[sanciones] " + staff.getName() + " -> " + objetivo.getName()
                + " (" + SancionStore.enLetras(e.tiempo) + ", congelado=" + e.congelar
                + ", reloj=" + (e.relojReal ? "real" : "pausa")
                + ", mundo=" + (e.mundo.isEmpty() ? "-" : e.mundo) + ") " + e.motivo);
        plugin.anunciarSancion("sancion", objetivo.getName(), staff.getName(), e.motivo, SancionStore.enLetras(e.tiempo));

        for (Player otro : Bukkit.getOnlinePlayers()) {
            if (otro.equals(staff) || !plugin.esStaff(otro)) continue;
            otro.sendMessage(Text.prefijo() + ChatColor.GRAY + "[staff] " + ChatColor.WHITE + staff.getName()
                    + ChatColor.GRAY + " ha sancionado a " + ChatColor.WHITE + objetivo.getName()
                    + ChatColor.GRAY + " (" + ChatColor.YELLOW + e.motivo + ChatColor.GRAY + ", "
                    + SancionStore.enLetras(e.tiempo) + ")");
        }
        plugin.avisarStaffDeObjetivo(staff, objetivo, "una sanción (" + e.motivo + ")");

        estados.remove(staff.getUniqueId());
        staff.closeInventory();
    }

    // ================= Iconos =================

    /**
     * Un item del menu. El id va aparte del nombre a proposito: antes se
     * sacaba del texto, y un mundo llamado "cancelar" pulsaba el boton de
     * cancelar.
     */
    private ItemStack item(String id, Material m, String nombre, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nombre);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            if (id != null) {
                meta.getPersistentDataContainer().set(MARCA,
                        org.bukkit.persistence.PersistentDataType.STRING, id);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** El brillo del item elegido en el menu de mundos. */
    private void brillar(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        try {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        } catch (Throwable t) {
            // Si esta version de Paper no lo tiene, el item se ve igual de bien.
        }
    }

    /** La cabeza con la skin real del jugador. */
    private ItemStack cabeza(Player jugador, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            try {
                ((SkullMeta) meta).setOwningPlayer(jugador);
            } catch (Throwable t) {
                // Si la API de skins cambia, el item sale sin cara pero el menu
                // sigue funcionando.
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
