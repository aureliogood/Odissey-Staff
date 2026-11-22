package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * El submenú de sanciones, al que se entra desde /staff.
 *
 * No hace sanciones: el trabajo de verdad sigue siendo el de MenuSancion (el
 * recorrido de cuatro pasos) y el de /staffsancion. Aqui se juntan los caminos
 * que antes solo seithsaban escribiendo comandos de memoria, y se ve de un
 * vistazo cuantos hay en curso.
 */
public final class MenuSanciones extends MenuBase {

    /**
 * Las ocho acciones, en dos filas de cuatro centradas.
 *
 * Con siete huecos por fila, cuatro centrados van en 11, 12, 14 y 15 (dejando
 * 13 para la cabecera). Antes estaban en 10, 12, 14 y 16, que se ve descuadrado:
 * sobraba hueco a la izquierda y de menos a la derecha.
 */
private static final int[] FILA_A = {11, 12, 14, 15};
private static final int[] FILA_B = {30, 31, 33, 34};

    public MenuSanciones(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_sanciones"));
    }

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.RED + "" + ChatColor.BOLD + "SANCIONES";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        List<SancionStore.Registro> vivas = plugin.sanciones().todas();
        int silencios = plugin.silencios().todos().size();

        inv.setItem(13, icono(Material.ANVIL, ChatColor.GOLD + "" + ChatColor.BOLD + "Sanciones",
                ChatColor.GRAY + "Todo lo que tiene que ver con",
                ChatColor.GRAY + "sancionar a alguien."));

        inv.setItem(FILA_A[0], boton("sancionar", Material.IRON_AXE,
                ChatColor.RED + "Sancionar a un jugador",
                ChatColor.GRAY + "Motivo, tiempo, destino,",
                ChatColor.GRAY + "congelado y reloj.",
                ChatColor.DARK_GRAY + "Abre el recorrido de cuatro pasos."));

        inv.setItem(FILA_A[1], boton("en-curso", Material.CLOCK,
                ChatColor.YELLOW + "Sanciones en curso",
                ChatColor.GRAY + "Ahora mismo: " + ChatColor.WHITE + vivas.size(),
                vivas.isEmpty()
                        ? ChatColor.DARK_GRAY + "No hay ninguna"
                        : ChatColor.DARK_GRAY + "Pulsa para verlas todas"));

        inv.setItem(FILA_A[2], boton("levantar", Material.SHEARS,
                ChatColor.YELLOW + "Levantar una sanción",
                ChatColor.GRAY + "Quita el congelado y el bloqueo",
                ChatColor.GRAY + "de comandos de una vez."));

        inv.setItem(FILA_A[3], boton("historial", Material.BOOK,
                ChatColor.YELLOW + "Historial de un jugador",
                ChatColor.GRAY + "Todo lo que le ha hecho el equipo,",
                ChatColor.GRAY + "con quién y cuándo.",
                ChatColor.DARK_GRAY + "/history <jugador>"));

        inv.setItem(FILA_B[0], boton("celdas", Material.IRON_BARS,
                ChatColor.RED + "Celdas",
                ChatColor.GRAY + "Manda a una celda con reloj.",
                ChatColor.DARK_GRAY + "Definidas: " + ChatColor.WHITE + celdas()));

        inv.setItem(FILA_B[1], boton("silenciar", Material.ORANGE_DYE,
                ChatColor.GOLD + "Silenciar",
                ChatColor.GRAY + "Le calla el chat, con tiempo",
                ChatColor.GRAY + "o permanente.",
                ChatColor.GRAY + "Activos ahora: " + ChatColor.WHITE + silencios));

        inv.setItem(FILA_B[2], boton("congelar", Material.ICE,
                ChatColor.AQUA + "Congelar",
                ChatColor.GRAY + "Se queda quieto y a oscuras",
                ChatColor.GRAY + "para poder hablar con él."));

        inv.setItem(FILA_B[3], boton("baneo", Material.BEDROCK,
                ChatColor.RED + "Banear",
                ChatColor.GRAY + "Tiempo con botones y motivo",
                ChatColor.GRAY + "escrito. Sin motivo no banea.",
                ChatColor.DARK_GRAY + "/banear <jugador>"));

        inv.setItem(49, botonVolver("volver", "el panel de staff"));
    }

    @Override
    protected void click(Player staff, String id, ItemStack en) {
        switch (id) {
            case "sancionar":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_MUTE)) return;
                staff.closeInventory();
                plugin.menuSancion().abrirRaiz(staff);
                return;
            case "en-curso":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_DATOS)) return;
                listar(staff);
                return;
            case "levantar":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_UNBAN)) return;
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ Escribe a quién le levantas la sanción."));
                staff.sendMessage(Text.info("'cancelar' para dejarlo."));
                plugin.espera().poner(staff.getUniqueId(), "sanciones-off");
                return;
            case "historial":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_DATOS)) return;
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ Escribe de quién quieres el historial."));
                plugin.espera().poner(staff.getUniqueId(), "history");
                return;
            case "baneo":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_BAN)) return;
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ Escribe a quién quieres banear."));
                plugin.espera().poner(staff.getUniqueId(), "banear");
                return;
            case "celdas":
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ /staffjaula <jug> <celda> <tiempo> [motivo]"));
                staff.sendMessage(Text.info("Celdas: " + ChatColor.WHITE + celdas()));
                return;
            case "silenciar":
                if (!exigirPermiso(staff, BaseCommand.PERMISO_MUTE)) return;
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ /staffmute <jugador> <tiempo> <motivo>"));
                staff.sendMessage(Text.info("Tiempos: 30m, 2h, 7d. O 'perm'."));
                return;
            case "congelar":
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ Escribe el nombre de a quién vas a congelar."));
                plugin.espera().poner(staff.getUniqueId(), "congelar");
                return;
            case "vigilancia":
                staff.closeInventory();
                staff.sendMessage(Text.aviso("✎ Escribe el nombre y el motivo."));
                plugin.espera().poner(staff.getUniqueId(), "vigilar");
                return;
            case "volver":
                // Antes llamaba a abrir(staff), que vuelve a pintar ESTE mismo
                // menu: el boton decia "Menú de staff" y no iba a ninguna parte.
                plugin.sonidos().volver(staff);
                staff.closeInventory();
                plugin.menuStaff().abrir(staff);
                return;
            default:
        }
    }

    /** Las sanciones vivas, por chat: el menu se queda como estaba. */
    private void listar(Player staff) {
        List<SancionStore.Registro> vivas = plugin.sanciones().todas();
        if (vivas.isEmpty()) {
            plugin.sonidos().aviso(staff);
            staff.sendMessage(Text.exito("No hay ninguna sanción en curso."));
            return;
        }
        staff.sendMessage(Text.destacado("══ Sanciones en curso (" + vivas.size() + ") ══"));
        for (SancionStore.Registro r : vivas) {
            staff.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + r.nombre
                    + ChatColor.YELLOW + " · " + SancionStore.enLetras(r.restante)
                    + (r.congeladoSi ? ChatColor.GRAY + " · congelado" : "")
                    + (r.relojReal ? ChatColor.GRAY + " · reloj real"
                    : ChatColor.GRAY + " · reloj en pausa")
                    + (r.motivo.isEmpty() ? "" : ChatColor.GRAY + " · " + r.motivo));
        }
        staff.sendMessage(Text.info("Para levantar una, vuelve a pulsar el botón rojo."));
    }

    /** Lo que el staff escribió como respuesta a "levantar una sanción". */
    void resolverEspera(Player staff, String texto) {
        if (!exigirPermiso(staff, BaseCommand.PERMISO_UNBAN)) return;
        if (texto.equalsIgnoreCase("cancelar") || texto.equalsIgnoreCase("cancela")) {
            staff.sendMessage(Text.info("Cancelado."));
            return;
        }
        Player objetivo = buscar(staff, texto);
        if (objetivo == null) {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No encuentro a nadie con ese nombre."));
            return;
        }
        // El filtro de "no te tocan a ti ni a otro staff" es el MISMO que usan los
        // comandos. Si aqui fuera mas blando, levantar la sancion de un admin
        // seria posible desde el menu y no desde la consola.
        if (!BaseCommand.puedeTocarDesdeMenu(plugin, staff, objetivo, "levantar la sanción de")) {
            plugin.sonidos().denegado(staff);
            return;
        }
        if (!plugin.sanciones().tiene(objetivo.getUniqueId())) {
            plugin.sonidos().aviso(staff);
            staff.sendMessage(Text.aviso(objetivo.getName() + " no tiene ninguna sanción."));
            return;
        }
        plugin.sanciones().quitar(objetivo.getUniqueId());
        plugin.gestor().descongelar(objetivo);
        objetivo.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        objetivo.sendTitle(null, null, 0, 0, 0);
        objetivo.sendMessage(Text.exito("Tu sanción se ha levantado antes de tiempo."));
        plugin.sonidos().exito(staff);
        staff.sendMessage(Text.exito("Sanción levantada a " + ChatColor.WHITE
                + objetivo.getName() + ChatColor.GREEN + "."));
        plugin.getLogger().info("[sanciones] " + staff.getName()
                + " levanto la sancion de " + objetivo.getName() + " desde el menu");
    }

    private boolean exigirPermiso(Player staff, String permiso) {
        if (BaseCommand.tienePermisoAccion(staff, permiso)) return true;
        staff.sendMessage(Text.error("No tienes permiso para esa acción (" + permiso + ")."));
        return false;
    }

    private Player buscar(Player staff, String texto) {
        Player exacto = plugin.getServer().getPlayerExact(texto);
        if (exacto != null) return exacto;
        String bajo = texto.toLowerCase(Locale.ROOT);
        Player encontrado = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.getName().toLowerCase(Locale.ROOT).startsWith(bajo)) continue;
            // Con dos nombres parecidos no se elige uno al azar: se avisa.
            if (encontrado != null) {
                staff.sendMessage(Text.error("Más de uno empieza por \"" + texto + "\": "
                        + encontrado.getName() + " y " + p.getName() + ChatColor.GRAY
                        + "  (usa el nombre entero)"));
                return null;
            }
            encontrado = p;
        }
        return encontrado;
    }

    private String celdas() {
        org.bukkit.configuration.ConfigurationSection s =
                plugin.config().getConfigurationSection("jaulas");
        if (s == null || s.getKeys(false).isEmpty()) return "ninguna (configura jaulas en config.yml)";
        return String.join(", ", s.getKeys(false));
    }
}
