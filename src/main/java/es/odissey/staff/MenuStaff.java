package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El menu de /staff: la puerta de entrada a todo.
 *
 * La idea no es meter aqui todo lo que el plugin sabe hacer, sino lo que se usa
 * con la mano en el raton. Las consultas puras (historial, online, snapshot)
 * siguen siendo comandos: un menu lleno de iconos iguales acaba siendo un menu
 * en el que no se encuentra nada.
 *
 * Distribucion (siempre la misma, en cuatro filas):
 *
 *   fila 1   herramientas tuyas   (objetos, vanish, rango, ir a alguien)
 *   fila 2   mirar a alguien       (ficha, espiar, cuentas alternativas, skins)
 *   fila 3   moderar               (sanciones, silenciar, congelar, celdas)
 *   fila 4   registros             (chat, llamar, vigilancia)
 *
 * Cada boton lleva su estado a la vista: se ve de un vistazo si llevas las
 * herramientas puestas, si estas en vanish, cuantos hay silenciados y cuantos
 * sancionados. Un menu que no dice en que estado estas obliga a probar.
 */
public final class MenuStaff extends MenuBase {

    private static final int CABECERA = 4;
    private static final int[] F_HERRAMIENTAS = {10, 12, 14, 16};
    private static final int[] F_MIRAR = {19, 21, 23, 25};
    private static final int[] F_MODERAR = {28, 30, 32, 34};
    private static final int[] F_REGISTROS = {38, 40, 42};

    public MenuStaff(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_staff"));
    }

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.YELLOW + "" + ChatColor.BOLD + "STAFF";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        StaffManager.Estado e = plugin.gestor().estado(staff);

        // ---------------- Cabecera ----------------
        List<String> cabecera = new ArrayList<>();
        cabecera.add(ChatColor.GRAY + "Turno de " + ChatColor.WHITE + staff.getName());
        if (!e.rango.isEmpty()) {
            cabecera.add(ChatColor.GRAY + "Rango: " + ChatColor.RESET + e.rango);
        }
        cabecera.add("");
        cabecera.add(lineaEstado("Vanish", e.vanish));
        cabecera.add(lineaEstado("Herramientas", plugin.items().llevaHerramientas(staff)));
        cabecera.add(lineaCuenta("Conectados", Math.max(0,
                plugin.getServer().getOnlinePlayers().size() - 1)));
        cabecera.add(ChatColor.DARK_GRAY + "OdisseyStaff v" + plugin.getDescription().getVersion());
        inv.setItem(CABECERA, icono(Material.NETHER_STAR,
                ChatColor.GOLD + "OdisseyStaff", cabecera.toArray(new String[0])));

        // ---------------- Fila 1: herramientas tuyas ----------------

        boolean lleva = plugin.items().llevaHerramientas(staff);
        inv.setItem(F_HERRAMIENTAS[0], boton("objetos", Material.CHEST,
                ChatColor.AQUA + "Objetos de staff  " + ChatColor.DARK_GRAY + "· "
                        + (lleva ? ChatColor.GREEN + "PUESTOS" : ChatColor.RED + "NO PUESTOS"),
                ChatColor.GRAY + (lleva ? "Pulsa para guardarlos." : "Pulsa para que te los den."),
                ChatColor.DARK_GRAY + "Perla, cofre, hielo, poción",
                ChatColor.DARK_GRAY + "y vara de blaze."));

        inv.setItem(F_HERRAMIENTAS[1], boton("vanish",
                e.vanish ? Material.ENDER_EYE : Material.GLASS_BOTTLE,
                ChatColor.AQUA + "Vanish  " + ChatColor.DARK_GRAY + "· "
                        + (e.vanish ? ChatColor.GREEN + "ACTIVADO" : ChatColor.GRAY + "desactivado"),
                ChatColor.GRAY + (e.vanish
                        ? "Nadie te ve. Pulsa para aparecer."
                        : "Pulsa para desaparecer del mapa.")));

        inv.setItem(F_HERRAMIENTAS[2], boton("rango", Material.NAME_TAG,
                ChatColor.AQUA + "Rango y color",
                e.rango.isEmpty()
                        ? ChatColor.GRAY + "Ahora escribes normal."
                        : ChatColor.GRAY + "Ahora: " + ChatColor.RESET + e.rango,
                ChatColor.DARK_GRAY + "Lo que va delante de tu nombre en el chat."));

        inv.setItem(F_HERRAMIENTAS[3], boton("tp", Material.ENDER_PEARL,
                ChatColor.AQUA + "Ir a un jugador",
                ChatColor.GRAY + "Elige a alguien conectado y",
                ChatColor.GRAY + "teletranspórtate a su lado."));

        // ---------------- Fila 2: mirar a alguien ----------------

        int conectados = Math.max(0, plugin.getServer().getOnlinePlayers().size() - 1);
        inv.setItem(F_MIRAR[0], boton("ficha", Material.PLAYER_HEAD,
                ChatColor.YELLOW + "Ficha de jugador",
                ChatColor.GRAY + "Todo sobre un jugador y",
                ChatColor.GRAY + "qué hacer con él.",
                lineaCuenta("Conectados sin ti", conectados)));

        UUID espiando = plugin.espia().objetivoDe(staff.getUniqueId());
        Player objetivo = espiando == null ? null : Bukkit.getPlayer(espiando);
        if (objetivo != null) {
            inv.setItem(F_MIRAR[1], boton("espiar", Material.SPYGLASS,
                    ChatColor.YELLOW + "Espiando a " + objetivo.getName(),
                    ChatColor.GRAY + "Pulsa para volver a su panel.",
                    ChatColor.DARK_GRAY + "Mundo: " + ChatColor.WHITE
                            + objetivo.getWorld().getName()));
        } else {
            inv.setItem(F_MIRAR[1], boton("espiar", Material.SPYGLASS,
                    ChatColor.YELLOW + "Espiar a un jugador",
                    ChatColor.GRAY + "Le ves la posición, el inventario,",
                    ChatColor.GRAY + "lo que dice y lo que hace.",
                    ChatColor.DARK_GRAY + "Sin que él se entere."));
        }

        inv.setItem(F_MIRAR[2], boton("alts", Material.SKELETON_SKULL,
                ChatColor.YELLOW + "Cuentas alternativas",
                ChatColor.GRAY + "Misma IP, nombres parecidos",
                ChatColor.GRAY + "y horas jugadas."));

        inv.setItem(F_MIRAR[3], boton("copiar", Material.ZOMBIE_HEAD,
                ChatColor.YELLOW + "Copiar skin",
                ChatColor.GRAY + "Llévate una cabeza con la piel",
                ChatColor.GRAY + "de quien esté conectado.",
                ChatColor.DARK_GRAY + "No cambia tu propia piel."));

        // ---------------- Fila 3: moderar ----------------

        int vivas = plugin.sanciones().todas().size();
        inv.setItem(F_MODERAR[0], boton("sanciones", Material.IRON_AXE,
                ChatColor.RED + "Sanciones",
                ChatColor.GRAY + "Sancionar, levantar, historial,",
                ChatColor.GRAY + "celdas y silencios.",
                vivas == 0
                        ? ChatColor.DARK_GRAY + "En curso ahora: ninguna"
                        : ChatColor.YELLOW + "En curso ahora: " + ChatColor.WHITE + vivas));

        int silencios = plugin.silencios().todos().size();
        inv.setItem(F_MODERAR[1], boton("silenciar", Material.NOTE_BLOCK,
                ChatColor.RED + "Silenciar",
                ChatColor.GRAY + "Le calla el chat, con tiempo",
                ChatColor.GRAY + "o permanente.",
                silencios == 0
                        ? ChatColor.DARK_GRAY + "Silencios activos: ninguno"
                        : ChatColor.YELLOW + "Silencios activos: " + ChatColor.WHITE + silencios));

        int congelados = plugin.gestor().congelados().size();
        inv.setItem(F_MODERAR[2], boton("congelar", Material.ICE,
                ChatColor.RED + "Congelar",
                ChatColor.GRAY + "Se queda quieto y a oscuras",
                ChatColor.GRAY + "mientras hablas con él.",
                congelados == 0
                        ? ChatColor.DARK_GRAY + "Congelados ahora: ninguno"
                        : ChatColor.YELLOW + "Congelados ahora: " + ChatColor.WHITE + congelados));

        inv.setItem(F_MODERAR[3], boton("celdas", Material.IRON_BARS,
                ChatColor.RED + "Celdas",
                ChatColor.GRAY + "Manda a una celda con reloj",
                ChatColor.GRAY + "y le quita lo que sobra.",
                ChatColor.DARK_GRAY + "Definidas: " + ChatColor.WHITE + celdas()));

        // ---------------- Fila 4: registros ----------------

        inv.setItem(F_REGISTROS[0], boton("chat", Material.WRITABLE_BOOK,
                ChatColor.GOLD + "Chat de un jugador",
                ChatColor.GRAY + "Lo que ha dicho, para ver si",
                ChatColor.GRAY + "insultaba o se defendía."));

        inv.setItem(F_REGISTROS[1], boton("llamar", Material.BELL,
                ChatColor.GOLD + "Llamar al staff",
                ChatColor.GRAY + "Pide ayuda al resto del equipo",
                ChatColor.GRAY + "que esté conectado."));

        int vigilar = plugin.vigilancia().todas().size();
        inv.setItem(F_REGISTROS[2], boton("vigilar", Material.ENDER_EYE,
                ChatColor.GOLD + "Lista de vigilancia",
                ChatColor.GRAY + "Los que el equipo tiene fichados.",
                vigilar == 0
                        ? ChatColor.DARK_GRAY + "No hay nadie fichado."
                        : ChatColor.RED + "Fichados: " + ChatColor.WHITE + vigilar));

        // ---------------- Pie ----------------
        inv.setItem(49, botonCerrar("cerrar"));
    }

    @Override
    protected void click(Player staff, String id, ItemStack en) {
        switch (id) {
            case "objetos":
                darObjetos(staff);
                return;
            case "vanish":
                alternarVanish(staff);
                return;
            case "rango":
                pedirRango(staff);
                return;
            case "tp":
                staff.closeInventory();
                plugin.menuTeleporte().abrir(staff);
                return;

            case "ficha":
                staff.closeInventory();
                plugin.menuJugadores().abrirRaiz(staff);
                return;
            case "espiar":
                abrirEspiar(staff);
                return;
            case "alts":
                // Antes pedia el nombre con la espera "espiar", asi que pulsar
                // "Cuentas alternativas" en realidad empezaba a espiar al
                // jugador en vez de listar sus cuentas. Ahora tiene su propia
                // espera, que lanza /staffalt.
                pedir(staff, "alts", "Escribe el nombre para ver sus cuentas alternativas.");
                return;
            case "copiar":
                staff.closeInventory();
                plugin.menuCopiar().abrir(staff);
                return;

            case "sanciones":
                staff.closeInventory();
                plugin.menuSanciones().abrir(staff);
                return;
            case "silenciar":
                uso(staff, "✎ /staffmute <jugador> <tiempo> <motivo>",
                        "Tiempos: 30m, 2h, 7d. O 'perm' sin límite.");
                return;
            case "congelar":
                pedir(staff, "congelar",
                        "Escribe a quién vas a congelar. El bloque de hielo del kit también vale.");
                return;
            case "celdas":
                uso(staff, "✎ /staffjaula <jug> <celda> <tiempo> [motivo]",
                        "Celdas: " + celdas());
                return;

            case "chat":
                pedir(staff, "chatlog", "Escribe de quién quieres ver lo que ha dicho.");
                return;
            case "llamar":
                staff.closeInventory();
                Bukkit.dispatchCommand(staff, "staffcall");
                return;
            case "vigilar":
                pedir(staff, "vigilar", "Escribe el nombre y el motivo de la vigilancia.");
                return;

            case "cerrar":
                staff.closeInventory();
                return;
            default:
        }
    }

    /** Cierra el menu y pide un dato por el chat. */
    private void pedir(Player staff, String que, String mensaje) {
        staff.closeInventory();
        staff.sendMessage(Text.aviso("✎ " + mensaje));
        staff.sendMessage(Text.info("Escribe 'cancelar' para dejarlo."));
        plugin.espera().poner(staff.getUniqueId(), que);
    }

    /** Muestra la forma de usar un comando sin cerrar nada pendiente. */
    private void uso(Player staff, String linea, String ayuda) {
        staff.closeInventory();
        staff.sendMessage(Text.aviso(linea));
        staff.sendMessage(Text.info(ayuda));
    }

    private void pedirRango(Player staff) {
        staff.closeInventory();
        staff.sendMessage(Text.aviso("✎ Escribe tu rango de staff."));
        staff.sendMessage(Text.info("Ejemplo: &b[Admin] &7· o 'quita' para dejarlo."));
        plugin.espera().poner(staff.getUniqueId(), "rango");
    }

    /** El boton de los items: toggle, como estaba. */
    private void darObjetos(Player staff) {
        boolean llevaba = plugin.items().llevaHerramientas(staff);
        if (llevaba) {
            plugin.items().quitar(staff);
            staff.sendMessage(Text.aviso("Te has guardado las herramientas."));
        } else {
            plugin.items().repartir(staff);
            staff.sendMessage(Text.exito("Tienes las cinco herramientas en la barra rápida."));
        }
        plugin.sonidos().exito(staff);
        refrescar(staff);
    }

    private void alternarVanish(Player staff) {
        boolean ahoraVisible = !plugin.gestor().vanish(staff);
        plugin.gestor().cambiarVanish(staff, ahoraVisible);
        if (ahoraVisible) {
            staff.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "VANISH");
            staff.sendMessage(Text.aviso("Ahora eres invisible."));
        } else {
            staff.sendMessage(Text.exito("Ya te ven."));
        }
        plugin.sonidos().exito(staff);
        refrescar(staff);
    }

    /** Selector de a quien espiar. */
    private void abrirEspiar(Player staff) {
        // Si ya esta espiando a alguien, este boton devuelve a su panel en vez
        // de abrir el selector otra vez: el staff viene de "he mirado un
        // segundo y quiero volver", no de "quiero cambiar de objetivo".
        if (plugin.espia().estaEspiando(staff.getUniqueId())) {
            plugin.menuEspiar().abrirPanel(staff);
            return;
        }
        List<Player> otros = new ArrayList<>();
        for (Player p : staff.getServer().getOnlinePlayers()) {
            if (!p.equals(staff)) otros.add(p);
        }
        if (otros.isEmpty()) {
            plugin.sonidos().aviso(staff);
            staff.sendMessage(Text.aviso("No hay nadie más conectado."));
            return;
        }
        // Con uno solo se entra directo; con varios, un menu con las cabezas.
        // Elegir un nombre de memoria es una fuente de errores que se paga
        // caras cuando hay dos jugadores parecidos.
        if (otros.size() == 1) {
            plugin.menuEspiar().entrar(staff, otros.get(0));
            return;
        }
        staff.closeInventory();
        plugin.menuEspiar().abrirSelector(staff, otros);
    }

    private String celdas() {
        org.bukkit.configuration.ConfigurationSection s =
                plugin.config().getConfigurationSection("jaulas");
        if (s == null || s.getKeys(false).isEmpty()) return "ninguna (míralas en config.yml)";
        return String.join(", ", s.getKeys(false));
    }
}
