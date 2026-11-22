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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * El modo espiar: mirar a un jugador sin que se entere.
 *
 * Son tres cosas a la vez:
 *
 *   1. El staff entra en modo espectador con el objetivo之上. El jugador no ve
 *      un cuerpo extra, porque en espectador no hay cuerpo.
 *   2. Una ventana con lo que esta pasando de verdad: donde esta, cuanta vida
 *      tiene, que lleva en la mano, que se ha puesto, y lo que acaba de decir.
 *      Se refresca sola.
 *   3. Lo que dice el objetivo le llega al staff por el chat, con un prefijo
 *      para que no se confunda con un mensaje normal (lo hace StaffListener).
 *
 * Por que NO hay mensajes privados ni ventas aqui: el chat privado lo lleva el
 * plugin que lo implementa (essentials, towny...) y las ventas el de la economy.
 * Ningun listener de este plugin los ve nunca. Meterlo aqui seria prometer algo
 * que no llega.
 *
 * El menu es de seis filas. Los huecos del marco se rellenan con un item de
 * cristal, y el contenido se cambia en su sitio cada medio segundo: en vez de
 * cerrar y abrir (que hace parpadear la pantalla y cierra las animaciones),
 * solo se sustituyen los items que han cambiado.
 */
public final class MenuEspiar implements Listener {

    private static final int TAM = 54;

    private final OdisseyStaff plugin;
    private final org.bukkit.NamespacedKey MARCA;

    /** Los menus de espio abiertos ahora mismo: staff -> inventario. */
    private final java.util.Map<UUID, Inventory> abiertos =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Los selectores de objetivo: staff -> inventario. */
    private final java.util.Map<UUID, Inventory> selectores =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MenuEspiar(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.MARCA = new org.bukkit.NamespacedKey(plugin, "menu_espiar");
    }

    public void registrar() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ================= Entrar y salir =================

    /**
     * Empieza a espiar a un jugador.
     *
     * Se le pone en espectador Y se le registra en el store. Las dos cosas: el
     * espectador es lo que se ve, y el store es lo que permite reenviarle el
     * chat y saber a quien hay que soltarle cuando el objetivo se vaya.
     */
    public void entrar(Player staff, Player objetivo) {
        if (objetivo.equals(staff)) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No puedes espiarte a ti mismo."));
            return;
        }
        // Si ya estaba espiando a otro, se limpia todo lo de antes.
        salir(staff, true);

        plugin.espia().espiar(staff.getUniqueId(), objetivo.getUniqueId());
        plugin.gestor().entrarEspectador(staff, objetivo);

        staff.sendMessage(Text.exito("Estás espiando a " + ChatColor.WHITE
                + objetivo.getName() + ChatColor.GREEN + "."));
        staff.sendMessage(Text.info("No puede verte. Lo que diga te llega aquí con un "
                + ChatColor.YELLOW + "[espía]" + ChatColor.GRAY + " delante."));
        staff.sendMessage(Text.info("Para volver al panel: /staffespiar. "
                + "Para soltar: /staffespiar off."));
        plugin.sonidos().exito(staff);
        abrirPanel(staff);
    }

    /** El staff deja de espiar a quien estuviera. */
    public void salir(Player staff, boolean silencioso) {
        plugin.espia().soltar(staff.getUniqueId());
        Inventory abierto = abiertos.remove(staff.getUniqueId());
        tituloDel.remove(staff.getUniqueId());
        if (abierto != null) staff.closeInventory();
        boolean estaba = plugin.gestor().esEspectador(staff);
        plugin.gestor().salirEspectador(staff);
        if (estaba && !silencioso) {
            staff.sendMessage(Text.info("Has dejado de espiar."));
        }
    }

    /**
     * Al salir el objetivo, se le suelta a todos los que lo estaban mirando.
     *
     * Sin esto el staff se queda en espectador dentro de un sitio vacio y, si
     * el objetivo vuelve, el menu sigue mirando un UUID que ya no era suyo.
     */
    public void alSalirObjetivo(Player objetivo) {
        for (UUID staff : plugin.espia().espiasDe(objetivo.getUniqueId())) {
            Player p = Bukkit.getPlayer(staff);
            if (p == null) {
                plugin.espia().soltar(staff);
                continue;
            }
            salir(p, true);
            p.sendMessage(Text.aviso(objeto(objetivo.getName())
                    + " ha salido del servidor. Has dejado de espiarlo."));
        }
    }

    // ================= El panel =================

    /** Abre (o refresca) la ventana del staff. */
    public void abrirPanel(Player staff) {
        UUID objetivoId = plugin.espia().objetivoDe(staff.getUniqueId());
        if (objetivoId == null) {
            staff.closeInventory();
            return;
        }
        Player objetivo = Bukkit.getPlayer(objetivoId);
        if (objetivo == null) {
            // aqui el que se ha ido es el OBJETIVO, no el staff: lo que hay que
            // soltar es la espia de ESTE staff. Antes se llamaba
            // alSalirObjetivo(staff), que hacia la pregunta inversa ("¿quien
            // espia al staff?"), siempre vacia porque nadie puede espiarse a si
            // mismo, asi que no soltaba nada: el staff se quedaba en espectador
            // mirando un mundo vacio para siempre.
            salir(staff, true);
            return;
        }

        Inventory inv = abiertos.get(staff.getUniqueId());
        if (inv == null) {
            inv = nuevoPanel(staff, objetivo);
            pintar(inv, objetivo, true);
            staff.openInventory(inv);
            return;
        }
        // Si el staff ha cambiado de objetivo sin cerrar el panel, el cofre es
        // del otro: se rehace entero. Inventory no tiene getTitle() en la API
        // de Bukkit, asi que el titulo se guarda aparte al crear el panel.
        if (!tituloDe(objetivo).equals(tituloDel.get(staff.getUniqueId()))) {
            inv = nuevoPanel(staff, objetivo);
            pintar(inv, objetivo, true);
            staff.openInventory(inv);
            return;
        }
        // Ya lo tiene abierto: solo se refresca. La vista sigue siendo la misma
        // y la pantalla no parpadea.
        if (!inv.equals(staff.getOpenInventory().getTopInventory())) {
            staff.openInventory(inv);
        }
        pintar(inv, objetivo, false);
    }

    private final java.util.Map<UUID, String> tituloDel =
            new java.util.concurrent.ConcurrentHashMap<>();

private Inventory nuevoPanel(Player staff, Player objetivo) {
        String titulo = tituloDe(objetivo);
        Inventory inv = MenuBase.conMarco(titulo);
        abiertos.put(staff.getUniqueId(), inv);
        tituloDel.put(staff.getUniqueId(), titulo);
        return inv;
    }

    private String tituloDe(Player objetivo) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "ESPIA "
                + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + objetivo.getName();
    }

    /** El reloj del refresco. Corre cada medio segundo mientras haya alguien. */
    public void tick() {
        if (abiertos.isEmpty()) return;
        for (java.util.Map.Entry<UUID, Inventory> e : new ArrayList<>(abiertos.entrySet())) {
            Player staff = Bukkit.getPlayer(e.getKey());
            if (staff == null || !staff.isOnline()) {
                abiertos.remove(e.getKey());
                tituloDel.remove(e.getKey());
                continue;
            }
            UUID objetivoId = plugin.espia().objetivoDe(e.getKey());
            if (objetivoId == null) {
                // Si el store ya no sabe a quien espia, hay que SOLTAR al staff
                // (cerrar el cofre y sacarlo de espectador), no solo quitarlo del
                // mapa: si el cofre se queda abierto con la entrada borrada,
                // alClick ya no lo reconoce y deja mover los items a la barra.
                salir(staff, true);
                continue;
            }
            Player objetivo = Bukkit.getPlayer(objetivoId);
            if (objetivo == null) {
                // El objetivo se fue sin que pasara por el PlayerQuitEvent (u
                // otra sesion dejo la entrada puesta). Se suelta al staff Y se
                // quita su cofre de 'abiertos': si no, esta misma rama se
                // repite cada 10 ticks sin hacer nada util.
                salir(staff, true);
                continue;
            }
            // Si el objetivo cruza de dimension, Bukkit no mueve al espectador
            // con el y el staff se queda mirando el vacio de otro mundo.
            seguirDimension(staff, objetivo);
            // Si el staff ha cerrado el panel NO se le vuelve a abrir. Cerrar un
            // cofre para ir al inventario es normal, y reabrirselo encima cada
            // medio segundo haria el espiado inservible. Cerrar no es salir: se
            // sigue espiando, y el panel vuelve con el boton del panel de staff
            // o con /staffespiar. Lo que si se suelta es con el boton rojo.
            pintar(e.getValue(), objetivo, false);
        }
    }

    /**
     * El espectador sigue al objetivo dentro de su dimension, pero si este
     * cambia de dimension el staff se queda en la anterior mirando el vacio.
     * Aqui se le lleva al mismo sitio.
     */
    private void seguirDimension(Player staff, Player objetivo) {
        if (staff.getWorld() == objetivo.getWorld()) return;
        staff.teleport(objetivo.getLocation());
    }

    /** Escribe los datos del objetivo en el menu. */
    private void pintar(Inventory inv, Player objetivo, boolean completo) {
        org.bukkit.Location l = objetivo.getLocation();

        // El borde y el relleno los pone MenuBase.crear(), antes de escribir nada.
        // Si se pusieran despues, al rellenar los 54 huecos se borraria todo.

        // --- cabecera: la cara del objetivo ---
        poner(inv, 4, completo, cabeza(objetivo,
                ChatColor.WHITE + objetivo.getName(),
                ChatColor.GRAY + "Objetivo del espiado",
                ChatColor.DARK_GRAY + "Cierra esto y sigues espiándolo"));

        // --- donde esta ---
        poner(inv, 19, completo, icono(Material.COMPASS,
                ChatColor.YELLOW + "Dónde está",
                ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + l.getWorld().getName(),
                ChatColor.GRAY + "X: " + ChatColor.WHITE + Math.round(l.getX())
                        + ChatColor.GRAY + "  Y: " + ChatColor.WHITE + Math.round(l.getY())
                        + ChatColor.GRAY + "  Z: " + ChatColor.WHITE + Math.round(l.getZ()),
                ChatColor.GRAY + "Mirando: " + ChatColor.WHITE
                        + cardinal(l.getYaw()) + ChatColor.GRAY + " " + cardinal(l.getPitch()),
                ChatColor.GRAY + "Bioma: " + ChatColor.WHITE + biomaDe(l)));

        // --- Efectos de pocion: es lo que mas se pasa por alto en una revision ---
        List<String> efectos = new ArrayList<>();
        for (PotionEffect ef : objetivo.getActivePotionEffects()) {
            int seg = ef.getDuration() / 20;
            StringBuilder sb = new StringBuilder();
            sb.append(ef.getType().getName().toLowerCase(Locale.ROOT)
                    .replace('_', ' ')).append(' ').append(ef.getAmplifier() + 1);
            if (seg < 60) sb.append(" · ").append(seg).append("s");
            else if (seg < 3600) sb.append(" · ").append(seg / 60).append("m");
            else sb.append(" · ").append(seg / 3600).append("h");
            efectos.add(sb.toString());
        }

        // --- como esta ---
        String estado = objetivo.isDead()
                ? ChatColor.RED + "muerto"
                : ChatColor.GREEN + "" + Math.round(objetivo.getHealth() * 100.0 / 20.0) + "%";
        poner(inv, 20, completo, icono(Material.APPLE,
                ChatColor.YELLOW + "Estado",
                ChatColor.GRAY + "Vida: " + estado,
                ChatColor.GRAY + "Comida: " + ChatColor.WHITE + Math.round(objetivo.getFoodLevel()),
                ChatColor.GRAY + "Aire: " + ChatColor.WHITE + Math.round(objetivo.getRemainingAir())
                        + ChatColor.GRAY + "/300",
                ChatColor.GRAY + "Experiencia: " + ChatColor.WHITE
                        + objetivo.getLevel() + ChatColor.GRAY + " ("
                        + Math.round(objetivo.getExp() * 100.0 / objetivo.getExpToLevel())
                        + "%)",
                ChatColor.GRAY + "En el agua: " + (objetivo.isInWater() ? "sí" : "no"),
                ChatColor.GRAY + "Agachado: " + (objetivo.isSneaking() ? "sí" : "no"),
                ChatColor.GRAY + "Corriendo: " + (objetivo.isSprinting() ? "sí" : "no")));

        // --- Efectos de pocion: es lo que mas se pasa por alto en una revision ---
        List<String> loreEfectos = new ArrayList<>();
        if (efectos.isEmpty()) {
            loreEfectos.add(ChatColor.GRAY + "No tiene ninguno");
        } else {
            for (String e : efectos) {
                if (loreEfectos.size() >= 8) break;
                loreEfectos.add(ChatColor.GRAY + " · " + ChatColor.WHITE + e);
            }
        }
        poner(inv, 21, completo, icono(
                efectos.isEmpty() ? Material.GLASS_BOTTLE : Material.POTION,
                ChatColor.YELLOW + "Efectos de poción",
                loreEfectos.toArray(new String[0])));

        // --- Manos ---
        ItemStack mano = objetivo.getInventory().getItemInMainHand();
        poner(inv, 30, completo, objetoDe(mano, "Mano principal",
                "Lo que tiene delante y puede usar"));
        ItemStack otra = objetivo.getInventory().getItemInOffHand();
        poner(inv, 31, completo, objetoDe(otra, "Mano secundaria", "La offhand"));

        // --- Armadura ---
        ItemStack[] armadura = objetivo.getInventory().getArmorContents();
        String[] nombreArma = {"Casco", "Peto", "Pantalón", "Botas"};
        // En 22-25 (cuatro huecos INTERIOR seguidos) y no en 32: el 35, la cuarta
        // pieza, caia en el borde de cristal del marco y lo pintaba encima.
        for (int i = 0; i < 4 && i < armadura.length; i++) {
            poner(inv, 22 + i, completo,
                    objetoDe(armadura[i], nombreArma[i], "Armadura que lleva puesta"));
        }

        // --- Lo que acaba de decir ---
        List<String> loreDicho = new ArrayList<>();
        for (ChatJugadoresLog.Linea linea : plugin.chatJugadores()
                .de(objetivo.getName(), 15, "", 6)) {
            loreDicho.add(ChatColor.GRAY + " · " + ChatColor.WHITE + cortar(linea.texto, 28));
        }
        if (loreDicho.isEmpty()) {
            loreDicho.add(ChatColor.GRAY + "No ha dicho nada en los últimos 15 min");
        }
        loreDicho.add(ChatColor.DARK_GRAY + "Lo que escribe te llega al chat");
        loreDicho.add(ChatColor.DARK_GRAY + "con [espía] delante");
        poner(inv, 39, completo, icono(Material.WRITABLE_BOOK,
                ChatColor.YELLOW + "Lo que acaba de decir",
                loreDicho.toArray(new String[0])));

        // --- Lo que ha hecho ---
        List<String> loreHecho = new ArrayList<>();
        for (ActividadStore.Hecho h : plugin.actividad()
                .reciente(objetivo.getUniqueId(), plugin.actividad().minutos())) {
            if ("chat".equals(h.que)) continue;   // eso ya sale arriba
            if (loreHecho.size() >= 6) break;
            loreHecho.add(ChatColor.GRAY + " · " + ChatColor.WHITE + h.que
                    + (h.detalle.isEmpty() ? "" : ChatColor.GRAY + " " + cortar(h.detalle, 26)));
        }
        if (loreHecho.isEmpty()) {
            loreHecho.add(ChatColor.GRAY + "Nada anotable en los últimos minutos");
        }
        poner(inv, 40, completo, icono(Material.CLOCK,
                ChatColor.YELLOW + "Lo que está haciendo",
                loreHecho.toArray(new String[0])));

        // --- Historial: si esta baneado o vigilado ---
        List<SanctionStore.Entrada> hist = plugin.historial().de(objetivo.getName());
        List<String> textoHist = new ArrayList<>();
        for (int i = hist.size() - 1; i >= 0 && textoHist.size() < 5; i--) {
            SanctionStore.Entrada e = hist.get(i);
            textoHist.add(ChatColor.GRAY + " · " + ChatColor.WHITE + e.tipo
                    + ChatColor.DARK_GRAY + " · " + Fechas.diaHora(e.cuando)
                    + (e.motivo.isEmpty() ? "" : ChatColor.GRAY + " · " + e.motivo));
        }
        StringBuilder aviso = new StringBuilder();
        if (plugin.sanciones().tiene(objetivo.getUniqueId())) {
            SancionStore.Registro r = plugin.sanciones().obtener(objetivo.getUniqueId());
            aviso.append(ChatColor.RED).append("Tiene una sanción: le quedan ")
                    .append(ChatColor.WHITE).append(SancionStore.enLetras(r.restante));
        }
        if (plugin.vigilancia().estaVigilado(objetivo.getName())) {
            aviso.append(aviso.length() > 0 ? ChatColor.GRAY + " · " : "")
                    .append(ChatColor.RED).append("Está en la lista de vigilancia");
        }
        List<String> loreHist = new ArrayList<>(textoHist);
        if (loreHist.isEmpty()) loreHist.add(ChatColor.GRAY + "Sin sanciones registradas");
        loreHist.add(aviso.length() > 0 ? aviso.toString()
                : ChatColor.DARK_GRAY + "Sin sanciones activas");
        poner(inv, 41, completo, icono(Material.WRITABLE_BOOK,
                ChatColor.YELLOW + "Historial", loreHist.toArray(new String[0])));

        // --- Salir ---
        poner(inv, 49, completo, icono(Material.BARRIER,
                ChatColor.RED + "Dejar de espiar",
                ChatColor.GRAY + "Suelta el espiado y te devuelve",
                ChatColor.GRAY + "a donde estabas antes."));
    }

    /**
     * Escribe un item, o lo deja si ya era el mismo.
     *
     * Sin esta comparacion, el menu se actualiza veinte veces por segundo
     * reenviando los mismos items al cliente: el icono "brilla" sin parar y el
     * servidor manda datos sin motivo. Con esto, solo se mueve lo que ha
     * cambiado de verdad (la vida, el lugar, el item de la mano...).
     */
    private void poner(Inventory inv, int slot, boolean completo, ItemStack nuevo) {
        ItemStack viejo = inv.getItem(slot);
        if (viejo != null && nuevo != null && viejo.isSimilar(nuevo)
                && viejo.getAmount() == nuevo.getAmount()) return;
        inv.setItem(slot, nuevo);
    }

    // ================= Selector de objetivo =================

    /**
     * El menu para elegir a quien espiar.
     *
     * Solo se usa cuando hay mas de uno conectado: con uno solo se entra
     * directo, que es lo que hace abrirEspiar() en MenuStaff.
     */
public void abrirSelector(Player staff, List<Player> candidatos) {
        Inventory inv = MenuBase.conMarco(ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "ESPIA "
                + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + "¿A quién?");

        // Los huecos del interior, los mismos cuatro que usa el resto de menus.
        final int[] huecos = MenuBase.INTERIOR;

        int n = 0;
        for (Player p : candidatos) {
            if (n >= huecos.length) break;
            List<String> detalle = new ArrayList<>();
            detalle.add(ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + p.getWorld().getName());
            detalle.add(ChatColor.GRAY + "Vida: " + ChatColor.WHITE
                    + Math.round(p.getHealth() * 100.0 / 20.0) + "%");
            if (plugin.gestor().congelado(p)) detalle.add(ChatColor.RED + "Está congelado");
            if (plugin.sanciones().tiene(p.getUniqueId())) {
                detalle.add(ChatColor.RED + "Tiene una sanción activa");
            }
            if (plugin.vigilancia().estaVigilado(p.getName())) {
                detalle.add(ChatColor.RED + "Está en la lista de vigilancia");
            }
            if (plugin.gestor().vanish(p)) detalle.add(ChatColor.YELLOW + "Está en vanish");
            detalle.add(ChatColor.DARK_GRAY + "Pulsa para espiarle");

            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof SkullMeta) {
                try {
                    ((SkullMeta) meta).setOwningPlayer(p);
                } catch (Throwable t) { /* la API de skins puede cambiar */ }
            }
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + p.getName());
                meta.setLore(detalle);
                meta.getPersistentDataContainer().set(MARCA,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "ver:" + p.getUniqueId());
                item.setItemMeta(meta);
            }
            inv.setItem(huecos[n++], item);
        }

        ItemStack salir = new ItemStack(Material.BARRIER);
        ItemMeta sm = salir.getItemMeta();
        if (sm != null) {
            sm.setDisplayName(ChatColor.RED + "✖ Cancelar");
            sm.getPersistentDataContainer().set(MARCA,
                    org.bukkit.persistence.PersistentDataType.STRING, "salir");
            salir.setItemMeta(sm);
        }
        inv.setItem(49, salir);

        selectores.put(staff.getUniqueId(), inv);
        staff.openInventory(inv);
    }

    /** Clicks del selector (es un menu distinto del panel de espiado). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickSelector(InventoryClickEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        Inventory abierto = evento.getView().getTopInventory();
        if (!abierto.equals(selectores.get(staff.getUniqueId()))) return;
        evento.setCancelled(true);
        if (evento.getRawSlot() < 0 || evento.getRawSlot() >= abierto.getSize()) return;

        ItemStack en = evento.getCurrentItem();
        if (en == null || !en.hasItemMeta()) return;
        String id = en.getItemMeta().getPersistentDataContainer()
                .get(MARCA, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null) return;
        plugin.sonidos().click(staff);

        if (id.equals("salir")) {
            selectores.remove(staff.getUniqueId());
            plugin.sonidos().volver(staff);
            staff.closeInventory();
            plugin.menuStaff().abrir(staff);
            return;
        }
        if (!id.startsWith("ver:")) return;

        UUID objetivoId;
        try {
            objetivoId = UUID.fromString(id.substring(4));
        } catch (IllegalArgumentException e) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            selectores.remove(staff.getUniqueId());
            return;
        }
        Player objetivo = Bukkit.getPlayer(objetivoId);
        selectores.remove(staff.getUniqueId());
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            return;
        }
        entrar(staff, objetivo);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrarSelector(InventoryDragEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (evento.getView().getTopInventory().equals(selectores.get(staff.getUniqueId()))) {
            evento.setCancelled(true);
        }
    }

    // ================= Clicks =================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClick(InventoryClickEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (!abiertos.containsKey(staff.getUniqueId())) return;
        Inventory abierto = evento.getView().getTopInventory();
        if (!abierto.equals(abiertos.get(staff.getUniqueId()))) return;

        // Nada de lo que hay dentro se mueve. Sin esto el staff se lleva el
        // menu entero en la barra rapida.
        evento.setCancelled(true);

        if (evento.getRawSlot() < 0 || evento.getRawSlot() >= abierto.getSize()) return;
        if (evento.getRawSlot() != 49) return;   // el unico boton pulsable es salir

        salir(staff, false);
        plugin.sonidos().volver(staff);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrar(InventoryDragEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (!abiertos.containsKey(staff.getUniqueId())) return;
        if (abiertos.get(staff.getUniqueId()).equals(evento.getView().getTopInventory())) {
            evento.setCancelled(true);
        }
    }

    /** Para cuando un staff se desconecta con el espiado puesto. */
    public void limpiar(Player jugador) {
        // tituloDel tambien: se guardaba al abrir el panel y nunca se quitaba,
        // asi que el mapa crecia una entrada por cada staff que se conecto y el
        // titulo se quedaba apuntando al UUID de una sesion vieja.
        abiertos.remove(jugador.getUniqueId());
        tituloDel.remove(jugador.getUniqueId());
        // El selector de objetivo tambien: si se desconectaba con el abierto, su
        // entrada en 'selectores' se quedaba colgando para siempre.
        selectores.remove(jugador.getUniqueId());
        plugin.espia().soltar(jugador.getUniqueId());
    }

    public boolean abierto(Player jugador) {
        return abiertos.containsKey(jugador.getUniqueId());
    }

    // ================= Iconos =================

    private ItemStack cristal() {
        return cristal(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack cristal(Material m) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack icono(Material m, String nombre, String... lore) {
        return icono(new ItemStack(m), nombre, lore);
    }

    /** Un icono con el item del jugador dentro del lore: "tiene 64x diamante". */
    private ItemStack objetoDe(ItemStack objeto, String nombre, String queEs) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + queEs);
        if (objeto == null || objeto.getType() == Material.AIR) {
            lore.add(ChatColor.DARK_GRAY + "Vacío");
            return icono(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), nombre, lore.toArray(new String[0]));
        }
        lore.add(ChatColor.WHITE + "" + objeto.getAmount() + "x "
                + ChatColor.YELLOW + objeto.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (objeto.getItemMeta() != null && objeto.getItemMeta().hasDisplayName()
                && !objeto.getItemMeta().getDisplayName().equals(objeto.getType().name())) {
            lore.add(ChatColor.GRAY + "Con nombre: " + ChatColor.WHITE
                    + ChatColor.stripColor(objeto.getItemMeta().getDisplayName()));
        }
        return icono(objeto.clone(), nombre, lore.toArray(new String[0]));
    }

    private ItemStack icono(ItemStack base, String nombre, String... lore) {
        ItemStack item = base;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nombre);
            if (lore.length > 0) meta.setLore(java.util.Arrays.asList(lore));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
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
                // Si la API de skins cambia, el item sale sin cara pero el menu
                // sigue funcionando.
            }
        }
        if (meta != null) {
            meta.setDisplayName(lore.length > 0 ? lore[0] : jugador.getName());
            if (lore.length > 1) {
                meta.setLore(java.util.Arrays.asList(java.util.Arrays.copyOfRange(lore, 1, lore.length)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String cortar(String texto, int max) {
        if (texto == null) return "";
        String t = texto.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** "norte", "sureste"... en vez de un numero de grados. */
    private static String cardinal(float grados) {
        String[] puntos = {"sur", "suroeste", "oeste", "noroeste",
                "norte", "noreste", "este", "sureste"};
        int i = (int) ((Math.round(grados / 45f) % 8 + 8) % 8);
        return puntos[i];
    }

    private static String biomaDe(org.bukkit.Location l) {
        org.bukkit.block.Block b = l.getWorld().getBlockAt(l);
        try {
            return b.getBiome().getKey().getKey().replace('_', ' ');
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String objeto(String nombre) {
        return ChatColor.WHITE + nombre;
    }
}
