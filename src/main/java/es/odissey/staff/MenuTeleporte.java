package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menu con los jugadores conectados para elegir a uno y teletransportarse.
 *
 * La perla lleva a un jugador al azar, que va bien para saltar por el mapa pero
 * mal cuando buscas a uno concreto: si hay doce conectados, hay que pulsarla
 * hasta que salga el que buscas.
 *
 * Antes este menu creaba un cofre del tamaño justo (una fila si hay tres
 * jugadores, seis si hay muchos), lo que rompia el ritmo: al cambiar el numero
 * de conectados el menu cambiaba de alto y habia que volver a buscar el boton de
 * abajo. Ahora siempre son seis filas con el borde negro, como todos los demas.
 *
 * Se abre con shift+click derecho en la perla, o desde el panel de staff.
 */
public final class MenuTeleporte extends MenuBase {

    public MenuTeleporte(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_teleporte"));
    }

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.AQUA + "" + ChatColor.BOLD + "TELEPORTE";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        List<Player> otros = new ArrayList<>();
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (!p.equals(staff)) otros.add(p);
        }

        if (otros.isEmpty()) {
            inv.setItem(22, icono(Material.GRAY_DYE,
                    ChatColor.YELLOW + "No hay nadie más conectado",
                    ChatColor.DARK_GRAY + "Espera a que entre alguien"));
            inv.setItem(49, botonVolver("volver", "el panel de staff"));
            return;
        }

        inv.setItem(13, icono(Material.ENDER_EYE, ChatColor.GOLD + "" + ChatColor.BOLD + "Ir a un jugador",
                ChatColor.GRAY + "Conectados sin ti: " + ChatColor.WHITE + otros.size(),
                ChatColor.GRAY + "El cuerpo desaparece al entrar."));

        int pag = paginas.getOrDefault(staff.getUniqueId(), 0);
        int total = Math.max(1, (int) Math.ceil(otros.size() / (double) INTERIOR.length));
        if (pag >= total) pag = 0;
        paginas.put(staff.getUniqueId(), pag);

        int desde = pag * INTERIOR.length;
        int pintado = 0;
        for (int i = desde; i < otros.size() && pintado < INTERIOR.length; i++, pintado++) {
            inv.setItem(INTERIOR[pintado], boton("ver:" + otros.get(i).getUniqueId(),
                    cabezaDe(otros.get(i)), otros.get(i).getName(),
                    detalleDe(otros.get(i))));
        }

        // Paginacion abajo, siempre en los mismos sitios.
        if (total > 1) {
            if (pag > 0) {
                inv.setItem(48, boton("atras", Material.ARROW, ChatColor.YELLOW + "◀ Anterior"));
            }
            if (pag < total - 1) {
                inv.setItem(50, boton("siguiente", Material.ARROW, ChatColor.YELLOW + "Siguiente ▶"));
            }
        }
        inv.setItem(49, botonVolver("volver", "el panel de staff"));
    }

    private String[] detalleDe(Player p) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Mundo: " + ChatColor.WHITE + p.getWorld().getName());
        lore.add(ChatColor.GRAY + "Vida: " + ChatColor.WHITE
                + Math.round(p.getHealth() * 100.0 / 20.0) + "%");
        if (plugin.gestor().congelado(p)) lore.add(ChatColor.RED + "Está congelado");
        if (plugin.gestor().vanish(p)) lore.add(ChatColor.YELLOW + "Está en vanish");
        if (plugin.sanciones().tiene(p.getUniqueId())) {
            lore.add(ChatColor.RED + "Tiene una sanción activa");
        }
        lore.add(ChatColor.DARK_GRAY + "Pulsa para ir");
        return lore.toArray(new String[0]);
    }

    private final java.util.Map<UUID, Integer> paginas = new ConcurrentHashMap<>();

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
        if (!id.startsWith("ver:")) return;

        UUID id_;
        try {
            id_ = UUID.fromString(id.substring(4));
        } catch (IllegalArgumentException e) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está."));
            return;
        }
        Player destino = Bukkit.getPlayer(id_);
        if (destino == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("Ese jugador ya no está conectado."));
            return;
        }
        if (destino.equals(staff)) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No puedes ir a ti mismo."));
            return;
        }
        staff.closeInventory();
        plugin.gestor().teletransportar(staff, destino.getLocation());
        plugin.sonidos().exito(staff);
        staff.sendMessage(Text.exito("Has ido con " + ChatColor.WHITE + destino.getName()
                + ChatColor.GREEN + "."));
    }

    /** La cabeza de verdad del jugador. */
    private ItemStack cabezaDe(Player jugador) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            try {
                ((SkullMeta) meta).setOwningPlayer(jugador);
                // Sin volver a aplicar el meta, setOwningPlayer se pierde y la
                // cabeza sale siempre con la skin por defecto (Steve/Alex).
                item.setItemMeta(meta);
            } catch (Throwable t) { /* la API de skins puede cambiar */ }
        }
        return item;
    }

    public void abrir(Player staff) {
        super.abrir(staff);
    }
}