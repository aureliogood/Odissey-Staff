package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Los cuatro items que da /staff y como se sabe cual es cual.
 *
 * Para distinguir una perla de staff de una perla normal NO se mira el tipo de
 * item, sino una etiqueta guardada DENTRO del item (PersistentDataContainer).
 * Un item lleva la etiqueta en su informacion, asi que sobrevive a que se
 * guarde en un cofre, pase por un-hopper o se mueva de sitio. Comparar por
 * "es una perla" haria que un jugador normal cogiera una perla y activase el
 * teletransporte del staff.
 *
 * Los items se recognizing por la clave, no por el nombre, asi que-traducir el
 * juego no rompe nada.
 */
public final class StaffItems {

    /** Que hace cada item. */
    public enum Herramienta {
        /** Perla: teletransporte a un jugador conectado al azar. */
        IR_A_JUGADOR,
        /** Cofre: abre el inventario del jugador al que se le da click. */
        INVENTARIO,
        /** Bloque de hielo: congela, y volver a clicarle descongela. */
        CONGELAR,
        /** Potion: activa o quita el vanish. */
        VANISH,
        /** Vara de blaze: manda al jugador a su cama, o al spawn si no tiene. */
        MANDAR_A_CASA
    }

    /**
     * Que ranura de la barra rapida ocupa cada herramienta.
     *
     * Los indices son los de la barra rapida: el 0 es la primera ranura, que en
     * pantalla es el slot 1. Se dejan huecos entre ellos a proposito, para que
     * las herramientas se distingan de un vistazo y el staff no tenga que pasar
     * el raton por encima para saber cual tiene en la mano.
     *
     *   slot 1 (indice 0) -> perla (teletransporte a un jugador)
     *   slot 3 (indice 2) -> cofre
     *   slot 5 (indice 4) -> bloque de hielo
     *   slot 7 (indice 6) -> potion de vanish
     *   slot 9 (indice 8) -> vara de blaze
     */
    private static final int[] RANURAS = {0, 2, 4, 6, 8};

    /**
     * La ranura de la barra rapida que le toca a esta herramienta, o -1.
     *
     * OJO: se devuelve RANURAS[i], el indice i del enum. Son cosas distintas:
     * INVENTARIO es el primero del enum (i = 0) pero vive en la ranura 2, que
     * en pantalla es el slot 3. Devolver el i en vez del RANURAS[i] hacia que
     * la comparacion "esta en su sitio" no cuadrara nunca y los items quedaban
     * libres.
     */
    public int ranuraDe(Herramienta h) {
        Herramienta[] todas = Herramienta.values();
        for (int i = 0; i < todas.length; i++) {
            if (todas[i] == h) return i < RANURAS.length ? RANURAS[i] : -1;
        }
        return -1;
    }

    private final NamespacedKey clave;

    public StaffItems(OdisseyStaff plugin) {
        this.clave = new NamespacedKey(plugin, "herramienta_staff");
    }

    // ---------- Crear ----------

    public ItemStack crear(Herramienta herramienta, Plugin plugin) {
        ItemStack item = new ItemStack(materialDe(herramienta));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(clave, PersistentDataType.STRING, herramienta.name());
            meta.setDisplayName(ChatColor.GOLD + ChatColor.translateAlternateColorCodes('&', nombreDe(herramienta)));
            meta.setLore(loreDe(herramienta));
            // Para que no se pueda tirar en el suelo, fundir ni nada raro.
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material materialDe(Herramienta h) {
        switch (h) {
            case IR_A_JUGADOR: return Material.ENDER_PEARL;
            case INVENTARIO: return Material.CHEST;
            // Bloque de hielo. Antes se usaba un cubo de agua porque en las
            // versiones antiguas existia el "cubo de hielo"; en 1.21 ese item ya
            // no existe y lo unico que se parece es el propio bloque.
            case CONGELAR: return Material.ICE;
            case VANISH: return Material.POTION;
            case MANDAR_A_CASA: return Material.BLAZE_ROD;
            default: return Material.PAPER;
        }
    }

    private String nombreDe(Herramienta h) {
        switch (h) {
            case IR_A_JUGADOR: return "Perla de staff";
            case INVENTARIO: return "Cofre de invsee";
            case CONGELAR: return "Bloque de hielo";
            case VANISH: return "Pocion de vanish";
            case MANDAR_A_CASA: return "Vara de blaze";
            default: return "Item de staff";
        }
    }

    private List<String> loreDe(Herramienta h) {
        List<String> lore = new ArrayList<>();
        switch (h) {
            case IR_A_JUGADOR:
                lore.add(ChatColor.GRAY + "Click para ir a un jugador");
                lore.add(ChatColor.DARK_GRAY + "random que este conectado");
                lore.add(ChatColor.DARK_GRAY + "Agachado + click: menu de jugadores");
                lore.add(ChatColor.DARK_GRAY + "No se puede lanzar");
                break;
            case INVENTARIO:
                lore.add(ChatColor.GRAY + "Click derecho a un jugador");
                lore.add(ChatColor.GRAY + "para ver su inventario");
                break;
            case CONGELAR:
                lore.add(ChatColor.GRAY + "Click derecho a un jugador");
                lore.add(ChatColor.GRAY + "para congelarlo");
                lore.add(ChatColor.DARK_GRAY + "Vuelve a clicar para soltarlo");
                break;
            case VANISH:
                lore.add(ChatColor.GRAY + "Click para ponerte invisible");
                lore.add(ChatColor.GRAY + "o volver a ser visible");
                break;
            case MANDAR_A_CASA:
                lore.add(ChatColor.GRAY + "Click derecho a un jugador");
                lore.add(ChatColor.GRAY + "para mandarlo a su cama");
                lore.add(ChatColor.DARK_GRAY + "Si no tiene, al spawn del mundo");
                break;
            default:
                break;
        }
        lore.add(ChatColor.DARK_GRAY + "OdisseyStaff");
        return lore;
    }

    // ---------- Reconocer ----------

    /** Devuelve la herramienta que es, o null si el item no es nuestro. */
    public Herramienta de(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String valor = meta.getPersistentDataContainer().get(clave, PersistentDataType.STRING);
        if (valor == null) return null;
        try {
            return Herramienta.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Item con la etiqueta puesta a mano o de una version vieja.
            return null;
        }
    }

    // ---------- Repartir y recoger ----------

    /** Quita al jugador cualquier item de staff que lleve encima. */
    public void quitar(Player jugador) {
        org.bukkit.inventory.PlayerInventory inv = jugador.getInventory();
        ItemStack[] contenido = inv.getStorageContents();
        boolean cambio = false;
        for (int i = 0; i < contenido.length; i++) {
            if (de(contenido[i]) != null) {
                contenido[i] = null;
                cambio = true;
            }
        }
        if (cambio) inv.setStorageContents(contenido);

        if (de(jugador.getInventory().getItemInOffHand()) != null) {
            jugador.getInventory().setItemInOffHand(null);
        }
        // Y en el cursor, que si no se puede quedar con uno para siempre.
        ItemStack cursor = jugador.getItemOnCursor();
        if (de(cursor) != null) jugador.setItemOnCursor(null);
    }

    /** Da las cuatro herramientas en sus ranuras y quita las que hubiera. */
    public void repartir(Player jugador) {
        quitar(jugador);
        Herramienta[] herramientas = Herramienta.values();
        org.bukkit.inventory.PlayerInventory inv = jugador.getInventory();
        for (int i = 0; i < herramientas.length && i < RANURAS.length; i++) {
            int ranura = RANURAS[i];
            ItemStack previo = inv.getItem(ranura);
            if (previo != null && de(previo) == null) {
                // Si la ranura tenia un item del jugador, moverlo al primer hueco libre o dropearlo
                java.util.Map<Integer, ItemStack> sobrante = inv.addItem(previo);
                if (!sobrante.isEmpty()) {
                    for (ItemStack drop : sobrante.values()) {
                        jugador.getWorld().dropItemNaturally(jugador.getLocation(), drop);
                    }
                }
            }
            inv.setItem(ranura, crear(herramientas[i], null));
        }
        jugador.updateInventory();
    }

    /** Si el jugador lleva alguna herramienta puesta. */
    public boolean llevaHerramientas(Player jugador) {
        org.bukkit.inventory.PlayerInventory inv = jugador.getInventory();
        for (ItemStack contenido : inv.getStorageContents()) {
            if (de(contenido) != null) return true;
        }
        return de(inv.getItemInOffHand()) != null || de(jugador.getItemOnCursor()) != null;
    }
}