package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /history &lt;jugador&gt;: el historial de uno, en un menu en vez de en el chat.
 *
 * El comando de texto sigue estando (/staffhistory), porque para copiar un
 * pantallazo a Discord un menu no vale. Pero para lo que mas se usa esto, que es
 * "mirar rapido quien hizo que y quien lo sanctiono", un menu gana: cabe todo,
 * se navega con flechas y no llena el chat de veinte lineas que se van.
 *
 * Cada fila lleva el color de su tipo (aviso amarillo, silencio aqua, baneo
 * rojo) y el nombre de quien lo hizo. La ultima columna es lo que mas se
 * pregunta siempre: no es lo mismo que a Rob le avise un-owner que si le avisa
 * otro que lleva seis meses aqui.
 */
public final class MenuHistorial extends MenuBase {

    private static final int[] HUECOS = {
            10, 11, 12, 14, 15, 16,   // el 13 es la cabecera
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** A quien se le esta mirando, y en que pagina va cada staff. */
    private final java.util.Map<UUID, String> de_quien = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<UUID, Integer> paginas = new java.util.concurrent.ConcurrentHashMap<>();

    public MenuHistorial(OdisseyStaff plugin) {
        super(plugin, new org.bukkit.NamespacedKey(plugin, "menu_historial"));
    }

    public void abrir(Player staff, String jugador) {
        de_quien.put(staff.getUniqueId(), jugador);
        paginas.put(staff.getUniqueId(), 0);
        super.abrir(staff);
    }

    /** A quien le esta viendo este staff ahora mismo. */
    public String deQuien(Player staff) {
        return de_quien.get(staff.getUniqueId());
    }

    @Override
    protected String titulo() {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "ODISSEY "
                + ChatColor.YELLOW + "" + ChatColor.BOLD + "HISTORIAL";
    }

    @Override
    protected void contents(Inventory inv, Player staff) {
        String nombre = deQuien(staff);
        if (nombre == null || nombre.isEmpty()) {
            inv.setItem(22, icono(Material.GRAY_DYE,
                    ChatColor.YELLOW + "No hay nadie seleccionado",
                    ChatColor.GRAY + "/history <jugador>"));
            inv.setItem(49, botonCerrar("cerrar"));
            return;
        }

        List<SanctionStore.Entrada> historial = plugin.historial().de(nombre);
        List<WarnStore.Aviso> warns = plugin.warns().todosDe(nombre);

        // Cuantas veces de cada cosa, de todo el historico. Es la primera
        // pregunta que hace cualquiera que abre esto.
        int avisos = 0, silencios = 0, baneos = 0, expulsiones = 0, sanciones = 0;
        for (SanctionStore.Entrada e : historial) {
            switch (e.tipo) {
                case "aviso": avisos++; break;
                case "silencio": silencios++; break;
                case "baneo": baneos++; break;
                case "expulsion": expulsiones++; break;
                case "sancion": sanciones++; break;
                default: break;
            }
        }

        inv.setItem(13, cabezaDe(nombre,
                ChatColor.WHITE + nombre,
                ChatColor.GRAY + "Todo lo que le ha hecho el equipo",
                ChatColor.GRAY + "Avisos: " + ChatColor.WHITE + avisos
                        + ChatColor.GRAY + "  (avisos vivos: " + ChatColor.YELLOW
                        + plugin.warns().contar(nombre) + ChatColor.GRAY + ")",
                ChatColor.GRAY + "Silencios: " + ChatColor.WHITE + silencios,
                ChatColor.GRAY + "Baneos: " + ChatColor.WHITE + baneos,
                ChatColor.GRAY + "Expulsiones: " + ChatColor.WHITE + expulsiones,
                ChatColor.GRAY + "Sanciones (celda): " + ChatColor.WHITE + sanciones));

        List<SanctionStore.Entrada> pagina = historial;
        int total = historial.size();
        if (total == 0 && warns.isEmpty()) {
            inv.setItem(22, icono(Material.LIME_STAINED_GLASS_PANE,
                    ChatColor.GREEN + "Sin historial",
                    ChatColor.GRAY + nombre + " no tiene nada registrado."));
        }

        int pag = paginas.getOrDefault(staff.getUniqueId(), 0);
        int paginasTotal = Math.max(1, (int) Math.ceil(total / (double) HUECOS.length));
        if (pag >= paginasTotal) pag = 0;
        paginas.put(staff.getUniqueId(), pag);

        int desde = pag * HUECOS.length;
        int pintado = 0;
        for (int i = desde; i < total && pintado < HUECOS.length; i++, pintado++) {
            SanctionStore.Entrada e = historial.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Por: " + ChatColor.WHITE + (e.staff.isEmpty() ? "?" : e.staff));
            if (!e.motivo.isEmpty()) lore.add(ChatColor.GRAY + "Motivo: " + ChatColor.YELLOW + e.motivo);
            if (!e.extra.isEmpty()) lore.add(ChatColor.GRAY + "Extra: " + ChatColor.WHITE + e.extra);
            if (e.levantada) lore.add(ChatColor.GREEN + "Levantada");
            lore.add(ChatColor.DARK_GRAY + "ID " + e.id + " · " + Fechas.diaHora(e.cuando));
            lore.add(ChatColor.DARK_GRAY + "Pulsa para devolverla (si es un aviso)");
            String titulo = colorDe(e.tipo) + e.tipo.toUpperCase(java.util.Locale.ROOT);
            String[] conLore = lore.toArray(new String[0]);
            ItemStack item = boton("entrada:" + e.id, icono(Material.PAPER, titulo, conLore),
                    titulo, conLore);
            inv.setItem(HUECOS[pintado], item);
        }

        if (paginasTotal > 1) {
            if (pag > 0) {
                inv.setItem(48, boton("atras", Material.ARROW, ChatColor.YELLOW + "◀ Anterior"));
            }
            if (pag < paginasTotal - 1) {
                inv.setItem(50, boton("siguiente", Material.ARROW, ChatColor.YELLOW + "Siguiente ▶"));
            }
            inv.setItem(53, icono(Material.PAPER, ChatColor.GRAY
                    + "Página " + (pag + 1) + "/" + paginasTotal
                    + ChatColor.DARK_GRAY + "  (" + total + ")"));
        }

        // Los avisos van aparte del historial, porque no son sanciones: son la
        // escalera que lleva a ellas, y hay que verlos antes de decidir.
        if (!warns.isEmpty()) {
            WarnStore.Aviso a = warns.get(0);
            List<String> loreW = new ArrayList<>();
            loreW.add(ChatColor.GRAY + "Último: " + ChatColor.DARK_GRAY + Fechas.diaHora(a.cuando));
            loreW.add(ChatColor.GRAY + "Por: " + ChatColor.WHITE + (a.staff.isEmpty() ? "?" : a.staff));
            loreW.add(ChatColor.GRAY + "Motivo: " + ChatColor.YELLOW + a.motivo);
            loreW.add(ChatColor.DARK_GRAY + "Vivos: " + plugin.warns().contar(nombre));
            loreW.add(ChatColor.DARK_GRAY + "Pulsa para verlos todos");
            String[] conLore = loreW.toArray(new String[0]);
            String titulo = ChatColor.YELLOW + "Avisos (" + warns.size() + ")";
            inv.setItem(45, boton("warns", icono(Material.ENDER_EYE, titulo, conLore),
                    titulo, conLore));
        }

        inv.setItem(49, botonCerrar("cerrar"));
    }

    @Override
    protected void click(Player staff, String id, ItemStack en) {
        if (id.equals("cerrar")) {
            staff.closeInventory();
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
        if (id.equals("warns")) {
            mostrarAvisos(staff, deQuien(staff));
            return;
        }
        if (id.startsWith("entrada:")) {
            devolverEntrada(staff, id.substring(8));
            return;
        }
        if (id.startsWith("warn:")) {
            devolverAviso(staff, id.substring(5));
        }
    }

    /** Los avisos de uno, como texto: son pocos y caben. */
    private void mostrarAvisos(Player staff, String nombre) {
        List<WarnStore.Aviso> lista = plugin.warns().todosDe(nombre);
        staff.closeInventory();
        staff.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Avisos de "
                + ChatColor.WHITE + nombre + ChatColor.GOLD + " (" + lista.size() + ") ══");
        if (lista.isEmpty()) {
            staff.sendMessage(Text.info("No tiene ninguno."));
            return;
        }
        for (WarnStore.Aviso a : lista) {
            boolean vivo = !a.retirado && a.cuando >= System.currentTimeMillis()
                    - Math.max(1, plugin.config().getInt("warns.caducan-en-dias", 90))
                    * 24L * 3600_000L;
            staff.sendMessage(ChatColor.GRAY + " · " + ChatColor.YELLOW
                    + Fechas.diaHora(a.cuando)
                    + ChatColor.GRAY + " · " + ChatColor.WHITE + (a.staff.isEmpty() ? "?" : a.staff)
                    + (a.motivo.isEmpty() ? "" : ChatColor.GRAY + " · " + a.motivo)
                    + (a.retirado ? ChatColor.RED + " [retirado por " + a.retiradoPor + "]"
                    : vivo ? ChatColor.GREEN + " [vivo]" : ChatColor.DARK_GRAY + " [caducado]"));
        }
        WarnStore.Nivel siguiente = plugin.warns().siguienteNivel(nombre);
        if (siguiente != null) {
            staff.sendMessage(Text.aviso("Le faltan "
                    + (siguiente.avisos - plugin.warns().contar(nombre))
                    + " avisos para " + siguiente.accion + " automático."));
        }
        staff.sendMessage(Text.info("Devolver uno: /staffwarn <jugador> quitar <id>"));
    }

    private void devolverEntrada(Player staff, String idTexto) {
        long id;
        try {
            id = Long.parseLong(idTexto);
        } catch (NumberFormatException e) {
            return;
        }
        String nombre = deQuien(staff);
        for (SanctionStore.Entrada e : plugin.historial().de(nombre)) {
            if (e.id == id && e.tipo.equals("warn")) {
                // Un warn en el historial viene del sistema progresivo: se
                // devuelve en el store de avisos, no marcando el historial.
                staff.sendMessage(Text.aviso("Ese aviso viene del sistema progresivo."));
                staff.sendMessage(Text.info("Se devuelve con /staffwarn "
                        + nombre + " quitar <id del aviso>"));
                return;
            }
        }
        staff.sendMessage(Text.aviso("Una sanción no se devuelve desde aquí."));
        staff.sendMessage(Text.info("Se levanta con su comando (/staffunmute, "
                + "/staffsancion " + nombre + " off) para que se anote quién lo hizo."));
    }

    private void devolverAviso(Player staff, String idTexto) {
        long id;
        try {
            id = Long.parseLong(idTexto);
        } catch (NumberFormatException e) {
            return;
        }
        if (plugin.warns().retirar(id, staff.getName())) {
            staff.sendMessage(Text.exito("Aviso #" + id + " devuelto."));
            plugin.getLogger().info("[warns] " + staff.getName() + " devolvio el aviso #" + id);
            plugin.sonidos().exito(staff);
            refrescar(staff);
        } else {
            plugin.sonidos().error(staff);
            staff.sendMessage(Text.error("No hay ningún aviso #" + id + "."));
        }
    }

    private static ChatColor colorDe(String tipo) {
        switch (tipo) {
            case "aviso": return ChatColor.YELLOW;
            case "silencio": return ChatColor.AQUA;
            case "baneo": return ChatColor.RED;
            case "expulsion": return ChatColor.LIGHT_PURPLE;
            case "sancion": return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }

    /** La cabeza de un jugador, o un icono si no esta en el servidor. */
    private ItemStack cabezaDe(String nombre, String... lore) {
        Player p = Bukkit.getPlayerExact(nombre);
        if (p == null) return icono(Material.BOOK, lore[0], copia(lore, 1));
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.ItemMeta metaCabeza = item.getItemMeta();
        if (metaCabeza instanceof SkullMeta) {
            ((SkullMeta) metaCabeza).setOwningPlayer(p);
            // Sin reaplicar el meta, la skin se pierde y la cabeza sale con la
            // textura por defecto.
            item.setItemMeta(metaCabeza);
        }
        ItemStack base = boton("info", item, lore[0], copia(lore, 1));
        // El boton informational no debe hacer nada al pulsarlo, asi que se le
        // quita la etiqueta de accion.
        ItemStack limpio = base.clone();
        org.bukkit.inventory.meta.ItemMeta meta = limpio.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(
                    new org.bukkit.NamespacedKey(plugin, "menu_historial"));
            limpio.setItemMeta(meta);
        }
        return limpio;
    }

    private static String[] copia(String[] a, int desde) {
        if (desde >= a.length) return new String[0];
        String[] f = new String[a.length - desde];
        System.arraycopy(a, desde, f, 0, f.length);
        return f;
    }
}