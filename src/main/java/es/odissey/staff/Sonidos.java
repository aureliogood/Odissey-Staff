package es.odissey.staff;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Los sonidos del plugin, todos configurables desde config.yml.
 *
 * Antes cada menu cantaba el suyo a mano (unos un pling, otros un click) y no
 * habia forma de bajarlos: en una ronda con varios staff abriendo menus a la
 * vez, el sonido se vuelve molesto y no se puede apagar. Aqui hay un sitio unico
 * con seis sonidos con nombre, y el "activo" del config los silencia todos.
 *
 * Se usan CLAVES con nombre (minecraft:ui.button.click) en lugar del enum
 * Sound, porque el enum cambia de un version a otra y una clave desconocida
 * simplemente no suena, en vez de romper la compilacion del plugin entero.
 *
 *   abrir     suena al abrir un menu
 *   click     suena al pulsar cualquier boton
 *   exito     accion completada
 *   error     accion que falla
 *   denegado  intento sin permiso (mismo sitio que error, pero se puede afinar)
 *   volver    regresar al menu anterior
 *   aviso     algo que hay que mirar, sin ser error (una lista vacia, por ejemplo)
 */
public final class Sonidos {

    /** Un sonido con su volumen y su tono. */
    private static final class Def {
        final String clave;
        final double volumen;
        final double tono;
        Def(String clave, double volumen, double tono) {
            this.clave = clave;
            this.volumen = volumen;
            this.tono = tono;
        }
    }

    private final OdisseyStaff plugin;
    private final Map<String, Def> sonidos = new HashMap<>();
    private boolean activo = true;

    public Sonidos(OdisseyStaff plugin) {
        this.plugin = plugin;
        recargar();
    }

    /** Lee config.yml. Se puede volver a llamar tras un /reload. */
    public void recargar() {
        sonidos.clear();
        activo = plugin.config().getBoolean("sonidos.activo", true);
        definir("abrir",    "minecraft:block.chest.open",          0.4, 1.4);
        definir("click",    "minecraft:ui.button.click",           0.4, 1.4);
        definir("exito",    "minecraft:entity.player.levelup",     0.5, 1.6);
        definir("error",    "minecraft:entity.villager.no",        0.6, 1.0);
        definir("denegado", "minecraft:block.note_block.bass",     0.7, 0.7);
        definir("volver",   "minecraft:item.book.page_turn",       0.4, 1.0);
        definir("aviso",    "minecraft:block.note_block.pling",    0.6, 1.4);
    }

    private void definir(String clave, String porDefecto, double vol, double tono) {
        String ruta = "sonidos." + clave + ".";
        String nombre = plugin.config().getString(ruta + "clave", porDefecto);
        double volumen = plugin.config().getDouble(ruta + "volumen", vol);
        double tonoReal = plugin.config().getDouble(ruta + "tono", tono);
        sonidos.put(clave, new Def(nombre == null ? "" : nombre.trim(), volumen, tonoReal));
    }

    // ---------------- Atajos ----------------

    public void abrir(Player p)    { tocar(p, "abrir"); }
    public void click(Player p)    { tocar(p, "click"); }
    public void exito(Player p)    { tocar(p, "exito"); }
    public void error(Player p)    { tocar(p, "error"); }
    public void denegado(Player p) { tocar(p, "denegado"); }
    public void volver(Player p)   { tocar(p, "volver"); }
    public void aviso(Player p)    { tocar(p, "aviso"); }

    private void tocar(Player p, String clave) {
        if (!activo || p == null || !p.isOnline()) return;
        Def d = sonidos.get(clave);
        if (d == null || d.clave.isEmpty()) return;
        try {
            p.playSound(p.getLocation(), d.clave, (float) d.volumen, (float) d.tono);
        } catch (Throwable t) {
            // Una clave que no existe en esta version no debe romper nada: se
            // ignora y el menu sigue funcionando igual.
        }
    }
}
