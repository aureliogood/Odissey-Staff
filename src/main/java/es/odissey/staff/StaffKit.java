package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Guarda las herramientas de un staff para que sobrevivan a morir y a cerrar el
 * juego.
 *
 * Antes se perdian: al morir o al salir habia que volver a poner /staff. En una
 * ronda de moderacion eso es quedarse medio minuto sin herramientas, que es
 * justo cuando mas hacen falta.
 *
 * No se serializan los items, sino QUE herramienta va en QUE ranura. Es mas
 * simple y no depende de que Bukkit sepa guardar las etiquetas internas de un
 * item: si el formato de serializacion cambiara en una version, las
 * herramientas volverian a perder la marca y dejarian de funcionar.
 *
 * El archivo esta en el disco del servidor, asi que al restaurar se comprueba
 * SIEMPRE que quien entra es staff de verdad. Editar el fichero a mano no sirve
 * para obtener herramientas.
 *
 * Con items.persistentes en false, esto no hace nada.
 */
public final class StaffKit {

    private final OdisseyStaff plugin;
    private final StaffItems items;
    private final Almacen almacen;

    public StaffKit(OdisseyStaff plugin, StaffItems items) {
        this.plugin = plugin;
        this.items = items;
        this.almacen = plugin.almacen("herramientas", "herramientas.yml");
    }

    private boolean activo() {
        return plugin.config().getBoolean("items.persistentes", true);
    }

    /** Copia el inventario de herramientas del jugador al archivo. */
    public void guardar(Player jugador) {
        if (!activo()) return;
        YamlConfiguration y = leer();
        String base = "jugadores." + jugador.getUniqueId() + ".";
        int encontrados = 0;

        for (int ranura = 0; ranura < 9; ranura++) {
            ItemStack item = jugador.getInventory().getItem(ranura);
            StaffItems.Herramienta h = items.de(item);
            y.set(base + "item." + ranura, h == null ? null : h.name());
            if (h != null) encontrados++;
        }

        if (encontrados == 0) {
            y.set(base + "item", null);
            y.set(base + "cuando", null);
        } else {
            y.set(base + "cuando", System.currentTimeMillis());
        }

        escribir(y);
    }

    /** Vuelve a poner las herramientas guardadas de este jugador. */
    public void restaurar(Player jugador) {
        if (!activo()) return;
        YamlConfiguration y = leer();
        String base = "jugadores." + jugador.getUniqueId() + ".";

        if (!y.contains(base + "cuando")) return;
        // Nunca se restaura a quien no sea staff, aunque el fichero diga que si.
        if (!plugin.esStaff(jugador)) {
            y.set(base, null);
            escribir(y);
            return;
        }

        int puestas = 0;
        for (int ranura = 0; ranura < 9; ranura++) {
            String nombre = y.getString(base + "item." + ranura, "");
            if (nombre.isEmpty()) continue;
            try {
                jugador.getInventory().setItem(ranura,
                        items.crear(StaffItems.Herramienta.valueOf(nombre), null));
                puestas++;
            } catch (IllegalArgumentException e) {
                // Herramienta de una version anterior que ya no existe.
                plugin.getLogger().fine("[herramientas] se ignora '" + nombre + "' (ya no existe).");
            }
        }
        if (puestas > 0) {
            jugador.updateInventory();
            jugador.sendMessage(org.bukkit.ChatColor.GRAY + "Tus herramientas de staff te han esperado.");
        }
    }

    /** Forgetcha de un jugador. */
    public void olvidar(Player jugador) {
        if (!activo()) return;
        YamlConfiguration y = leer();
        y.set("jugadores." + jugador.getUniqueId(), null);
        escribir(y);
    }

    public boolean tieneGuardadas(Player jugador) {
        return activo() && leer().contains("jugadores." + jugador.getUniqueId() + ".cuando");
    }

    private YamlConfiguration leer() {
        return almacen.leer();
    }

    private void escribir(YamlConfiguration y) {
        almacen.escribir(y);
    }
}
