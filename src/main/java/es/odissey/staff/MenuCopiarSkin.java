package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Copiar skin": quedarse con la cabeza de un jugador conectado.
 *
 * Cuando el staff pulsa una cabeza se le mete en el inventario una cabeza con la
 * skin de ese jugador. Con eso puede: enseñarsela a otro por el chat, dejarla
 * como prueba en un reporte, o guardarsela.
 *
 * LO QUE ESTO NO ES, y conviene decirlo claro:
 *
 * No cambia la skin del staff. En un servidor no se puede poner a uno jugador
 * la piel de otro sin renegociar con los servidores de Mojang: la skin de un
 * jugador es suya y esta atada a su cuenta. Cualquier plugin que prometa eso lo
 * esta haciendo con parcheos internos que se rompen en cada version. Aqui se
 * entrega una cabeza, que es lo unico que se puede hacer bien.
 *
 * Y sobre el modo offline: si el servidor esta en online-mode=false, el
 * servidor NO tiene las texturas de los jugadores (no se las manda nadie), asi
 * que la cabeza saldra con el modelo por defecto aunque el jugador de verdad
 * tenga una skin personalizada. En el menu se avisa de eso, porque si no el
 * staff copia una skin y cree que el plugin esta roto.
 */
public final class MenuCopiarSkin extends MenuBase {

    private static final String TITULO = "Copiar skin";

    /** Las cuatro filas del interior: 7 huecos por fila, sin las columnas del marco. */
    private static final int[] HUECOS = {
            10, 11, 12, 14, 15, 16,   // el 13 es la cabecera
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Cada uno va mirando su pagina, sin que el otro se lo cambie. */
    private final java.util.Map<UUID, Integer> paginas = new ConcurrentHashMap<>();

    public MenuCopiarSkin(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_copiar"));
    }

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.AQUA + "" + ChatColor.BOLD + "SKINS";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        List<Player> todos = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(staff)) todos.add(p);
        }

        // Sin modo offline el servidor tiene las texturas; con el, no, y la
        // cabeza sale con el modelo por defecto.
        boolean sinTexturas = !tieneTexturas(staff);

        int pagina = paginas.getOrDefault(staff.getUniqueId(), 0);
        int paginasTotal = Math.max(1, (int) Math.ceil(todos.size() / (double) HUECOS.length));
        if (pagina >= paginasTotal) pagina = 0;
        paginas.put(staff.getUniqueId(), pagina);

        List<String> cabecera = new ArrayList<>();
        cabecera.add(ChatColor.GRAY + "Pulsa la cabeza de un jugador");
        cabecera.add(ChatColor.GRAY + "para llevarte una copia de su piel.");
        cabecera.add(ChatColor.DARK_GRAY + "Conectados sin ti: " + todos.size());
        if (sinTexturas) {
            cabecera.add(ChatColor.RED + "⚠ Modo offline: las copias saldrán");
            cabecera.add(ChatColor.DARK_GRAY + "  con el modelo por defecto.");
        }
        inv.setItem(13, icono(Material.PLAYER_HEAD, ChatColor.YELLOW + "Copiar skin",
                cabecera.toArray(new String[0])));

        if (todos.isEmpty()) {
            inv.setItem(22, icono(Material.GRAY_DYE,
                    ChatColor.YELLOW + "No hay nadie más conectado",
                    ChatColor.DARK_GRAY + "Espera a que entre alguien"));
            inv.setItem(49, botonVolver("volver", "el panel de staff"));
            return;
        }

        int desde = pagina * HUECOS.length;
        int pintado = 0;
        for (int i = desde; i < todos.size() && pintado < HUECOS.length; i++, pintado++) {
            Player objetivo = todos.get(i);
            inv.setItem(HUECOS[pintado], boton("copiar:" + objetivo.getUniqueId(),
                    cabezaDe(objetivo), ChatColor.WHITE + objetivo.getName(),
                    ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + objetivo.getWorld().getName(),
                    ChatColor.GRAY + "Vida: " + ChatColor.WHITE
                            + Math.round(objetivo.getHealth() * 100.0 / 20.0) + "%",
                    plugin.gestor().vanish(objetivo)
                            ? ChatColor.YELLOW + "Está en vanish"
                            : ChatColor.GRAY + "En su sitio",
                    ChatColor.DARK_GRAY + "Pulsa para copiar su skin"));
        }

        if (paginasTotal > 1) {
            if (pagina > 0) {
                inv.setItem(48, boton("atras", Material.ARROW, ChatColor.YELLOW + "◀ Anterior"));
            }
            if (pagina < paginasTotal - 1) {
                inv.setItem(50, boton("siguiente", Material.ARROW, ChatColor.YELLOW + "Siguiente ▶"));
            }
            inv.setItem(53, icono(Material.PAPER, ChatColor.GRAY + "Página "
                    + (pagina + 1) + " de " + paginasTotal));
        }
        inv.setItem(49, botonVolver("volver", "el panel de staff"));
    }

    @Override
    protected void click(Player staff, String id, ItemStack en) {
        if (id.equals("volver")) {
            plugin.sonidos().volver(staff);
            staff.closeInventory();
            plugin.menuStaff().abrir(staff);
            return;
        }
        if (id.equals("atras")) {
            paginas.merge(staff.getUniqueId(), -1, (a, b) -> Math.max(0, a + b));
            refrescar(staff);
            return;
        }
        if (id.equals("siguiente")) {
            paginas.merge(staff.getUniqueId(), 1, Integer::sum);
            refrescar(staff);
            return;
        }
        if (!id.startsWith("copiar:")) return;

        UUID objetivoId;
        try {
            objetivoId = UUID.fromString(id.substring(7));
        } catch (IllegalArgumentException e) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            refrescar(staff);
            return;
        }
        Player objetivo = Bukkit.getPlayer(objetivoId);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error(objetivoId + " ya no está conectado."));
            refrescar(staff);
            return;
        }

        ItemStack entrega = entregar(staff, objetivo);

        plugin.sonidos().exito(staff);
        staff.sendMessage(Text.exito("Skin de " + ChatColor.WHITE + objetivo.getName()
                + ChatColor.GREEN + " copiada."));
        if (entrega == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No se ha podido copiar. Mira la consola."));
        } else if (!tieneTexturas(staff)) {
            staff.sendMessage(Text.aviso("⚠ El servidor está en modo offline: la cabeza"
                    + " sale con el modelo por defecto aunque esa persona tenga skin propia."
                    + " No es un fallo del plugin."));
        }
        refrescar(staff);
    }

    /**
     * Da la cabeza, y la devuelve.
     *
     * Se construye de cero en vez de clonar la del menu: la del menu lleva el
     * nombre y el lore del boton, y un "copiar" que sale con la etiqueta
     * "Pulsa para copiar su skin" escrita encima es un estorpo.
     *
     * Si el inventario esta lleno cae al suelo, que es mejor que perderla: el
     * staff acaba de copiar algo y no se le va a avisar.
     */
    private ItemStack entregar(Player staff, Player objetivo) {
        try {
            ItemStack cabeza = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = cabeza.getItemMeta();
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwningPlayer(objetivo);
            }
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + "Skin de " + objetivo.getName());
                meta.setLore(java.util.Arrays.asList(
                        ChatColor.GRAY + "Copia de su apariencia.",
                        ChatColor.DARK_GRAY + "Es una cabeza, no cambia tu piel."));
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                cabeza.setItemMeta(meta);
            }

            java.util.Map<Integer, ItemStack> sobra = staff.getInventory().addItem(cabeza);
            for (ItemStack i : sobra.values()) {
                staff.getWorld().dropItemNaturally(staff.getLocation(), i);
            }
            plugin.getLogger().info("[skins] " + staff.getName()
                    + " copio la skin de " + objetivo.getName());
            return cabeza;
        } catch (Throwable t) {
            plugin.getLogger().warning("[skins] fallo al copiar la skin de "
                    + objetivo.getName() + ": " + t);
            return null;
        }
    }

    /** La cabeza de un jugador, con su skin si el servidor la tiene. */
    private ItemStack cabezaDe(Player jugador) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(jugador);
            // Sin volver a aplicar el meta, la skin no se guarda en el item y la
            // cabeza sale con la textura por defecto.
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Si el servidor tiene las texturas de los jugadores.
     *
     * Solo se puede saber mirando al propio staff: si su perfil lleva
     * "textures", el servidor esta en online-mode (o detras de un proxy que las
     * pasa). Si no las lleva, ninguna copia va a salir con skin real.
     */
    private boolean tieneTexturas(Player staff) {
        try {
            org.bukkit.profile.PlayerProfile p = staff.getPlayerProfile();
            if (p == null) return false;
            return p.getTextures() != null && p.getTextures().isEmpty() == false;
        } catch (Throwable t) {
            return false;
        }
    }
}