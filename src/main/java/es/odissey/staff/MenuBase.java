package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Lo comun a todos los menus de este plugin.
 *
 * Hace tres cosas que si no se repetirian en cada menu, y que se olvidan
 * justo en el que se olvidan:
 *
 *   1. Cancelar SIEMPRE el click y el arrastre. Sin esto, un menu se puede
 *      vaciar: el jugador coge los items con shift y se los lleva. Es el bug
 *      clasico de los menus hechos a mano, y no aparece hasta que alguien con
 *      muchas horas de juego lo prueba.
 *   2. Pintar el marco de una vez, al abrir.
 *   3. Reconocer el boton pulsado por su etiqueta, no por el nombre del item:
 *      un mundo o un jugador que se llamen "cancelar" no pueden dispararlo.
 *
 * Los menus que ya existian (MenuSancion, MenuJugador) tienen su propio
 * manejador porque refrescan contenido en vivo y no encajan aqui tal cual.
 */
public abstract class MenuBase implements Listener {

    protected static final int TAM = 54;

    protected final OdisseyStaff plugin;
    private final org.bukkit.NamespacedKey MARCA;

    /** Que menus ha abierto cada jugador, para no cancelar clicks ajenos. */
    private final java.util.Map<UUID, Inventory> abiertos =
            new java.util.concurrent.ConcurrentHashMap<>();

    protected MenuBase(OdisseyStaff plugin, org.bukkit.NamespacedKey marca) {
        this.plugin = plugin;
        this.MARCA = marca;
    }

    public void registrar() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    protected abstract String titulo();

    /** Pinta el menu una vez, al abrir. */
    protected abstract void contents(Inventory inv, Player staff);

    /** Accion del boton. `id` es lo que lleva el item en su etiqueta. */
    protected abstract void click(Player staff, String id, ItemStack en);

    public void abrir(Player staff) {
        Inventory inv = crear(titulo());
        contents(inv, staff);
        abiertos.put(staff.getUniqueId(), inv);
        staff.openInventory(inv);
        plugin.sonidos().abrir(staff);
    }

    /**
     * Vuelve a pintar el menu SIN sonido de apertura.
     *
     * Se usa cuando un boton refresca el propio menu (cambiar el estado de algo)
     * y no queremos que suene como si se acabara de abrir: solo el click que ya
     * sono. Antes cada refresco volvia a abrir y cantaba otra vez.
     */
    protected void refrescar(Player staff) {
        Inventory inv = abiertos.get(staff.getUniqueId());
        if (inv == null) {
            abrir(staff);
            return;
        }
        contents(inv, staff);
        staff.updateInventory();
    }

    /**
     * Un cofre de seis filas con el borde negro ya puesto.
     *
     * TODOS los menus pasan por aqui. Antes cada uno hacia su propio marco y
     * eran tres versiones distintas de lo mismo, con dos siendo subtly
     * diferentes: es exactamente como un menu se queda con el borde a medias o
     * con el interior sin rellenar y parece que falta algo.
     *
     * El resultado es siempre el mismo:
     *
     *   ████████████████████████████████████████   <- borde (fila 0 y 5)
     *   █   ·   ·   ·   ·   ·   ·   ·   █          <- columnas laterales
     *   █   ·   ·   ·   ·   ·   ·   ·   █
     *   █   ·   ·   ·   ·   ·   ·   ·   █
     *   █   ·   ·   ·   ·   ·   ·   ·   █
     *   ████████████████████████████████████████
     *
     * Relleno gris claro dentro (para que se vea que el menu esta tapado y no
     * que faltan items) y negro en el borde (para que se vea donde acaba).
     */
    protected Inventory crear(String titulo) {
        return conMarco(titulo);
    }

    /**
     * Un cofre de seis filas con el borde ya puesto, sin necesidad de heredar.
     *
     * Lo usan tambien los menus que gestionan su propio inventario (el de
     * espiar, que refresca en vivo y tiene dos inventario a la vez).
     */
    public static Inventory conMarco(String titulo) {
        Inventory inv = Bukkit.createInventory(null, TAM, titulo);
        marco(inv);
        return inv;
    }

    /**
     * Pone el borde negro y el relleno gris.
     *
     * Va SIEMPRE antes de contents(): si se pusiera despues, al rellenar los
     * 54 huecos borraria todo lo que se acabara de escribir encima.
     */
    public static void marco(Inventory inv) {
        int filas = inv.getSize() / 9;
        ItemStack relleno = cristal(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemStack negro = cristal(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, relleno);
        if (filas <= 0) return;
        for (int i = 0; i < 9; i++) {              // fila de arriba
            inv.setItem(i, negro);
            inv.setItem((filas - 1) * 9 + i, negro);   // fila de abajo
        }
        for (int fila = 1; fila < filas - 1; fila++) {  // columnas de los lados
            inv.setItem(fila * 9, negro);
            inv.setItem(fila * 9 + 8, negro);
        }
    }

    /**
     * Las cuatro filas del interior, sin las columnas del borde.
     *
     * Son los unicos huecos donde se pone contenido. Tenerlo aqui en un solo
     * sitio es lo que hace que los menus se parezcan entre si en vez de que
     * cada uno invente donde coloca sus botones.
     */
    protected static final int[] INTERIOR = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClick(InventoryClickEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        Inventory abierto = evento.getView().getTopInventory();
        // Comparacion por identidad con lo que se abrio, no por titulo: si
        // otro plugin abre un cofre con el mismo nombre, no se toca.
        if (!abierto.equals(abiertos.get(staff.getUniqueId()))) return;

        evento.setCancelled(true);
        if (evento.getRawSlot() < 0 || evento.getRawSlot() >= abierto.getSize()) return;
        ItemStack en = evento.getCurrentItem();
        if (en == null || !en.hasItemMeta()) return;
        String id = en.getItemMeta().getPersistentDataContainer()
                .get(MARCA, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null || id.isEmpty()) return;
        plugin.sonidos().click(staff);
        try {
            click(staff, id, en);
        } catch (Exception e) {
            staff.sendMessage(Text.error("Ese botón ha fallado: " + e.getMessage()));
            plugin.getLogger().warning("[menu] fallo en " + titulo() + ": " + e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrar(InventoryDragEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) evento.getWhoClicked();
        if (evento.getView().getTopInventory().equals(abiertos.get(staff.getUniqueId()))) {
            evento.setCancelled(true);
        }
    }

    /**
     * Al cerrar el menu se olvida que ese jugador lo tenia abierto.
     *
     * Antes solo se limpiaba al desconectarse (alSalir), asi que quien cerraba
     * el cofre y seguia dentro dejaba su entrada (y el Inventory) colgando en el
     * mapa para siempre. Se comprueba que lo que se cierra es NUESTRO menu, para
     * no pisar la entrada de otro que se abra despues.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void alCerrar(InventoryCloseEvent evento) {
        if (!(evento.getPlayer() instanceof Player)) return;
        Player staff = (Player) evento.getPlayer();
        if (evento.getView().getTopInventory().equals(abiertos.get(staff.getUniqueId()))) {
            abiertos.remove(staff.getUniqueId());
        }
    }

    /** Para cuando un jugador se va con el menu abierto. */
    public void limpiar(Player jugador) {
        abiertos.remove(jugador.getUniqueId());
    }

    // ---------------- Items ----------------

    protected static ItemStack cristal(Material m) {
        return icono(m, " ");
    }

    /** Un item que no hace nada al pulsarlo: decoracion. */
    protected static ItemStack icono(Material m, String nombre, String... lore) {
        return item(new ItemStack(m), nombre, lore);
    }

    /**
     * Un boton: lleva dentro que accion dispara.
     *
     * La etiqueta va DENTRO del item y no se saca del nombre, porque un jugador
     * o un mundo llamados "cancelar" pulsarian ese boton si se buscase por texto.
     */
    protected ItemStack boton(String id, Material m, String nombre, String... lore) {
        ItemStack stack = item(new ItemStack(m), nombre, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(MARCA,
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    protected ItemStack boton(String id, ItemStack base, String nombre, String... lore) {
        ItemStack stack = item(base, nombre, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(MARCA,
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack item(ItemStack stack, String nombre, String[] lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (nombre != null) meta.setDisplayName(nombre);
            if (lore != null && lore.length > 0) {
                meta.setLore(java.util.Arrays.asList(lore));
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------------- Botones compartidos ----------------

    /**
     * Boton "volver" identico en todos los menus.
     *
     * Antes cada menu dibujaba su propio barrier y su propio texto: unos decian
     * "Volver", otros "Menú de staff", y en los menus de dos niveles no se sabia
     * si volvian un nivel o cerraban. Con uno solo, el boton quiere decir siempre
     * lo mismo y el destino va escrito en el lore.
     */
    protected ItemStack botonVolver(String id, String destino) {
        return boton(id, Material.ARROW,
                ChatColor.YELLOW + "◀ Volver",
                ChatColor.GRAY + "Regresa a " + ChatColor.WHITE + destino);
    }

    /** Boton "cerrar" identico en todos los menus. */
    protected ItemStack botonCerrar(String id) {
        return boton(id, Material.BARRIER,
                ChatColor.RED + "Cerrar",
                ChatColor.GRAY + "Sale del menú");
    }

    /** Una linea de estado para el lore: "Etiqueta: ACTIVADO". */
    protected static String lineaEstado(String etiqueta, boolean activo) {
        return ChatColor.GRAY + etiqueta + ": " + Text.estado(activo);
    }

    /** Una linea de contador para el lore: "Etiqueta: 3". */
    protected static String lineaCuenta(String etiqueta, int cuantos) {
        return ChatColor.GRAY + etiqueta + ": " + ChatColor.WHITE + cuantos;
    }
}
