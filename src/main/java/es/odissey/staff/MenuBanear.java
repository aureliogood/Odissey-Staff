package es.odissey.staff;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baneo limpio: elegir tiempo, escribir el motivo, banear.
 *
 * El problema de banear escribiendo el comando es que hay que acordarse de
 * como se escribe un tiempo (7d, 2h, perm) y de que el permanente hay que
 * confirmar con PERM al final. Son dos cosas que se olvidan, y equivocarse en
 * un ban significa que a un jugador que solo iba a estar dos horas fuera se le
 * queda fuera tres meses. Este menu convierte el tiempo en botones, que es
 * donde no se puede equivocar.
 *
 * El motivo se escribe en el chat y se queda como respuesta: si no se escribe,
 * el boton avisa y no banea. Un ban sin motivo es un ban que dentro de un mes
 * nadie sabe por que se puso.
 *
 * Confirma y ejecuta. No hay "aplicar" hasta el final.
 */
public final class MenuBanear extends MenuBase {

    /** Cada staff tiene su borrador: tiempo y motivo mientras lo monta. */
    private static final class Borrador {
        String tiempo = "7d";
        String motivo = "";
        UUID objetivo;
        String nombreObjetivo = "";
    }

    private final java.util.Map<UUID, Borrador> borradores = new ConcurrentHashMap<>();

    public MenuBanear(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_banear"));
    }

    public void abrir(Player staff, Player objetivo) {
        Borrador b = new Borrador();
        b.objetivo = objetivo == null ? null : objetivo.getUniqueId();
        b.nombreObjetivo = objetivo == null ? "" : objetivo.getName();
        borradores.put(staff.getUniqueId(), b);
        super.abrir(staff);
    }

    /** Espera la razon escrita en el chat. El texto NO sale al global. */
    public void pedirMotivo(Player staff) {
        Borrador b = borradores.get(staff.getUniqueId());
        if (b == null) {
            staff.sendMessage(Text.error("El ban se ha cancelado."));
            return;
        }
        if (b.nombreObjetivo.isEmpty()) {
            staff.sendMessage(Text.error("Primero tienes que elegir a quién baneas."));
            return;
        }
        if (b.tiempo.equals("perm")) {
            // El permanente es el unico que no tiene vuelta atras. Se avisa
            // aqui, con el nombre delante, no despues de haber banado.
            staff.closeInventory();
            staff.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "BAN PERMANENTE");
            staff.sendMessage(Text.info("A " + b.nombreObjetivo));
            staff.sendMessage(Text.error("No hay vuelta atrás."));
            staff.sendMessage(Text.aviso("Escribe el motivo y luego 'PERM' para confirmar."));
            staff.sendMessage(Text.info("O 'cancelar' para dejarlo."));
            plugin.espera().poner(staff.getUniqueId(), "ban-motivo");
            return;
        }
        staff.closeInventory();
        staff.sendMessage(Text.info("✎ Escribe el motivo del ban de " + b.nombreObjetivo + "."));
        staff.sendMessage(Text.aviso("Sin motivo no se banea. 'cancelar' para dejarlo."));
        plugin.espera().poner(staff.getUniqueId(), "ban-motivo");
    }

    /** Lo que el staff escribio como motivo. */
    public void resolverMotivo(Player staff, String texto) {
        Borrador b = borradores.get(staff.getUniqueId());
        if (b == null) return;
        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            borradores.remove(staff.getUniqueId());
            staff.sendMessage(Text.info("Ban cancelado."));
            return;
        }
        if (texto.equalsIgnoreCase("perm")) {
            staff.sendMessage(Text.error("Un ban permanente necesita un motivo."));
            rearmar(staff, "ban-motivo");
            return;
        }
        if (texto.length() < 3) {
            staff.sendMessage(Text.error("Ese motivo es demasiado corto."));
            rearmar(staff, "ban-motivo");
            return;
        }
        if (b.tiempo.equals("perm")) {
            // Confirmacion en dos pasos, por escrito.
            b.motivo = texto;
            staff.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + texto));
            staff.sendMessage(Text.aviso("Escribe " + ChatColor.WHITE + "PERM"
                    + ChatColor.YELLOW + " para aplicarlo, o 'cancelar'."));
            plugin.espera().poner(staff.getUniqueId(), "ban-confirmar");
            return;
        }
        aplicar(staff, b, texto);
    }

    /** Confirmacion del permanente: el staff tiene que escribir PERM. */
    public void resolverConfirmacion(Player staff, String texto) {
        Borrador b = borradores.get(staff.getUniqueId());
        if (b == null) return;
        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            borradores.remove(staff.getUniqueId());
            staff.sendMessage(Text.info("Ban cancelado."));
            return;
        }
        if (!texto.equalsIgnoreCase("perm")) {
            staff.sendMessage(Text.error("Escribe PERM para confirmar el ban permanente."));
            rearmar(staff, "ban-confirmar");
            return;
        }
        aplicar(staff, b, b.motivo);
    }

    /**
     * Vuelve a pedir el texto despues de un aviso.
     *
     * El que escucha el chat borra la espera ANTES de llamar a resolverMotivo, asi
     * que un simple return dejaba el flujo muerto: el staff perdia el borrador y
     * tenia que abrir /banear otra vez y repetir todo.
     */
    private void rearmar(Player staff, String que) {
        plugin.espera().poner(staff.getUniqueId(), que);
    }

    private void aplicar(Player staff, Borrador b, String motivo) {
        if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_BAN)) {
            staff.sendMessage(Text.error("Ya no tienes permiso para banear."));
            borradores.remove(staff.getUniqueId());
            return;
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            staff.sendMessage(Text.error("Falta el motivo: sin motivo no se banea."));
            return;
        }
        if (!BaseCommand.puedeTocarDesdeMenu(plugin, staff,
                b.objetivo == null ? null : Bukkit.getPlayer(b.objetivo), "banear")) {
            return;
        }
        long ms = plugin.leerTiempoEnMs(b.tiempo);
        // Antes la comprobacion era "!permanente && ms <= 0", que no puede
        // ocurrir nunca (si ms <= 0, permanente ya es true): un tiempo que no se
        // entendia salia como ban PERMANENTE en silencio, sin avisar a nadie.
        // Ahora un tiempo invalido es un error, y lo unico permanente es "perm".
        boolean permanente = b.tiempo.equals("perm");
        if (!permanente && ms <= 0) {
            staff.sendMessage(Text.error("El tiempo '" + b.tiempo + "' no vale."));
            return;
        }
        // El motivo que se guarda lleva la marca del plugin al principio: es lo
        // unico que permite saber luego que el ban lo puso esto y no un admin a
        // mano, y quitarselo sin tocar el de nadie mas.
        String texto = "[OdisseyStaff] " + motivo;
        java.util.Date hasta = permanente ? null : new java.util.Date(System.currentTimeMillis() + ms);

        try {
            Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(b.nombreObjetivo, texto, hasta, staff.getName());
        } catch (Exception e) {
            staff.sendMessage(Text.error("No se pudo aplicar el ban: " + e.getMessage()));
            return;
        }

        Player online = Bukkit.getPlayer(b.nombreObjetivo);
        if (online != null) {
            online.kickPlayer(ChatColor.RED + "Has sido expulsado: " + ChatColor.GRAY + motivo);
        }

        String dur = permanente ? "permanente" : SancionStore.enLetras(ms);
        plugin.historial().anotar("baneo", b.nombreObjetivo, "", staff.getName(),
                motivo, dur);
        plugin.historial().guardar();

        staff.sendMessage(Text.exito(b.nombreObjetivo + " baneado " + dur + "."));
        staff.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
        if (!permanente) {
            staff.sendMessage(Text.info("Se levanta solo el "
                    + Fechas.diaHora(System.currentTimeMillis() + ms) + "."));
        }
        plugin.getLogger().info("[baneo] " + staff.getName() + " -> " + b.nombreObjetivo
                + " (" + dur + ") " + motivo);

        plugin.avisarEquipo(ChatColor.RED + b.nombreObjetivo + ChatColor.YELLOW
                + " baneado " + dur + ChatColor.GRAY + " por " + ChatColor.WHITE + staff.getName()
                + ChatColor.GRAY + ": " + motivo);
        plugin.anunciarSancion("ban", b.nombreObjetivo, staff.getName(), motivo, dur);

        plugin.sonidos().error(staff);
        borradores.remove(staff.getUniqueId());
        staff.closeInventory();
    }

    // ---------------- El menu ----------------

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "BANEAR";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        Borrador b = borradores.computeIfAbsent(staff.getUniqueId(), k -> new Borrador());

// La cabecera real es la ficha del objetivo (mas abajo). Este icono de
        // aqui es solo un titulo: si no hay nadie elegido, hace de cabecera.
        if (b.nombreObjetivo.isEmpty()) {
            inv.setItem(13, icono(Material.ANVIL, ChatColor.GOLD + "" + ChatColor.BOLD + "Baneo",
                    ChatColor.GRAY + "Elige a alguien y un tiempo.",
                    ChatColor.DARK_GRAY + "Sin motivo no se banea."));
            inv.setItem(22, icono(Material.GRAY_DYE,
                    ChatColor.YELLOW + "No hay nadie seleccionado",
                    ChatColor.GRAY + "Este menu se abre desde la ficha",
                    ChatColor.GRAY + "de un jugador (/staff) o con /banear <jugador>"));
            inv.setItem(49, botonCerrar("cerrar"));
            return;
        }

inv.setItem(4, icono(Material.PAPER,
                ChatColor.WHITE + b.nombreObjetivo,
                ChatColor.GRAY + "Tiempo: " + ChatColor.YELLOW
                        + (b.tiempo.equals("perm") ? "permanente"
                        : SancionStore.enLetras(plugin.leerTiempoEnMs(b.tiempo))),
                ChatColor.GRAY + "Motivo: "
                        + (b.motivo.isEmpty() ? ChatColor.RED + "(sin escribir)"
                        : ChatColor.WHITE + b.motivo),
                ChatColor.DARK_GRAY + "Pulsa abajo para escribir el motivo"));

// --- Tiempos cortos: cuatro centrados en la fila 1 ---
        tiempo(inv, 11, Material.LIME_DYE, "30 minutos", "30m", b);
        tiempo(inv, 12, Material.YELLOW_DYE, "1 hora", "1h", b);
        tiempo(inv, 14, Material.ORANGE_DYE, "6 horas", "6h", b);
        tiempo(inv, 15, Material.BROWN_DYE, "12 horas", "12h", b);
        // --- Dias: tres centrados en la fila 2 ---
        tiempo(inv, 20, Material.REDSTONE, "1 día", "1d", b);
        tiempo(inv, 22, Material.COAL, "7 días", "7d", b);
        tiempo(inv, 24, Material.EMERALD_BLOCK, "30 días", "30d", b);
        // --- Definitivo: al centro de la fila 3, solo ---
        inv.setItem(31, boton("perm", Material.BEDROCK,
                b.tiempo.equals("perm")
                        ? ChatColor.RED + "Permanente · ELEGIDO"
                        : ChatColor.DARK_RED + "Permanente",
                ChatColor.GRAY + "Sin vuelta atrás.",
                ChatColor.RED + "Pide escribir PERM para confirmar.",
                b.tiempo.equals("perm") ? ChatColor.GREEN + "Es el tiempo elegido."
                        : ChatColor.DARK_GRAY + "Pulsa para elegirlo"));

        // --- Motivo y aplicar ---
inv.setItem(30, boton("motivo", Material.WRITABLE_BOOK,
                ChatColor.YELLOW + "Escribir el motivo",
                b.motivo.isEmpty()
                        ? ChatColor.RED + "Sin motivo todavía"
                        : ChatColor.GRAY + "Ahora: " + ChatColor.WHITE + b.motivo,
                ChatColor.DARK_GRAY + "Se escribe en el chat y no sale"));

        boolean listo = !b.motivo.isEmpty();
        inv.setItem(32, boton("aplicar", listo ? Material.LIME_CONCRETE : Material.REDSTONE_BLOCK,
                listo ? (b.tiempo.equals("perm")
                        ? ChatColor.RED + "⚠ Aplicar ban PERMANENTE"
                        : ChatColor.GREEN + "Aplicar ban")
                        : ChatColor.GRAY + "Falta el motivo",
                ChatColor.WHITE + b.nombreObjetivo,
                ChatColor.GRAY + "Tiempo: " + ChatColor.YELLOW
                        + (b.tiempo.equals("perm") ? "permanente"
                        : SancionStore.enLetras(plugin.leerTiempoEnMs(b.tiempo))),
                listo ? ChatColor.DARK_GRAY + "Pulsa para aplicarlo."
                        : ChatColor.DARK_GRAY + "Escribe el motivo primero"));

        inv.setItem(49, boton("cerrar", Material.BARRIER,
                ChatColor.RED + "Cerrar",
                ChatColor.DARK_GRAY + "Se borra el borrador."));
    }

    private void tiempo(Inventory inv, int slot, Material m, String nombre, String valor, Borrador b) {
        boolean elegido = b.tiempo.equals(valor);
        List<String> lore = new ArrayList<>();
        lore.add(elegido ? ChatColor.GREEN + "Es el tiempo elegido."
                : ChatColor.GRAY + "Pulsa para elegirlo.");
        if (valor.equals("perm")) lore.add(ChatColor.RED + "Sin vuelta atrás.");
        inv.setItem(slot, boton("tiempo:" + valor, m,
                (elegido ? ChatColor.GREEN : ChatColor.YELLOW) + nombre, lore.toArray(new String[0])));
    }

    @Override
    protected void click(Player staff, String id, ItemStack en) {
        Borrador b = borradores.get(staff.getUniqueId());
        if (id.equals("cerrar")) {
            borradores.remove(staff.getUniqueId());
            staff.closeInventory();
            return;
        }
        if (b == null) return;
        if (id.startsWith("tiempo:")) {
            b.tiempo = id.substring(7);
            refrescar(staff);
            return;
        }
        if (id.equals("perm")) {
            b.tiempo = "perm";
            refrescar(staff);
            return;
        }
        if (id.equals("motivo")) {
            pedirMotivo(staff);
            return;
        }
        if (id.equals("aplicar")) {
            if (b.motivo.isEmpty()) {
                plugin.sonidos().error(staff);
                staff.sendMessage(Text.error("Falta el motivo: sin motivo no se banea."));
                return;
            }
            // El permanente se pide por escrito al escribir el motivo; aqui solo
            // se ejecuta si el tiempo es normal.
            if (b.tiempo.equals("perm")) {
                pedirMotivo(staff);
                return;
            }
            aplicar(staff, b, b.motivo);
        }
    }

    /** Para cuando alguien desconecta a media cuenta. */
    public void limpiar(Player jugador) {
        borradores.remove(jugador.getUniqueId());
    }
}
