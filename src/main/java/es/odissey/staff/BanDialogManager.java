package es.odissey.staff;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Formularios de diálogo nativos de Paper para el flujo /ban. */
public final class BanDialogManager {
    private static final int POR_PAGINA = 12;
    private static final ClickCallback.Options OPCIONES_CLIC = ClickCallback.Options.builder()
            .uses(1)
            .build();
    private final OdisseyStaff plugin;
    private final SanctionCommand sanciones;

    public BanDialogManager(OdisseyStaff plugin, SanctionCommand sanciones) {
        this.plugin = plugin;
        this.sanciones = sanciones;
    }

    public void abrirLista(Player staff) { abrirLista(staff, 0); }

    private void abrirLista(Player staff, int pagina) {
        List<Player> conectados = new ArrayList<>(Bukkit.getOnlinePlayers());
        conectados.removeIf(p -> p.getUniqueId().equals(staff.getUniqueId()));
        conectados.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        if (conectados.isEmpty()) {
            staff.sendMessage(Text.aviso("No hay otros jugadores conectados para banear."));
            return;
        }
        int paginas = Math.max(1, (conectados.size() + POR_PAGINA - 1) / POR_PAGINA);
        int actual = Math.max(0, Math.min(pagina, paginas - 1));
        int desde = actual * POR_PAGINA;
        int hasta = Math.min(conectados.size(), desde + POR_PAGINA);
        List<ActionButton> botones = new ArrayList<>();
        for (Player objetivo : conectados.subList(desde, hasta)) {
            UUID uuid = objetivo.getUniqueId();
            String nombre = objetivo.getName();
            botones.add(ActionButton.builder(Component.text(nombre))
                    .width(150)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player jugador) abrirFormulario(jugador, uuid, nombre);
                    }, OPCIONES_CLIC))
                    .build());
        }
        if (actual > 0) {
            int anterior = actual - 1;
            botones.add(ActionButton.builder(Component.text("← Anterior"))
                    .width(120).action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player jugador) abrirLista(jugador, anterior);
                    }, OPCIONES_CLIC)).build());
        }
        if (actual + 1 < paginas) {
            int siguiente = actual + 1;
            botones.add(ActionButton.builder(Component.text("Siguiente →"))
                    .width(120).action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player jugador) abrirLista(jugador, siguiente);
                    }, OPCIONES_CLIC)).build());
        }
        ActionButton cerrar = ActionButton.builder(Component.text("Cerrar")).width(100).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Banear jugador"))
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                Component.text("Selecciona una cuenta conectada · página " + (actual + 1) + " de " + paginas))))
                        .build())
                .type(DialogType.multiAction(botones, cerrar, 2)));
        staff.showDialog(dialog);
    }

    private void abrirFormulario(Player staff, UUID objetivoUuid, String nombreElegido) {
        if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_BAN)) {
            staff.sendMessage(Text.error("No tienes permiso para banear."));
            return;
        }
        Player objetivo = Bukkit.getPlayer(objetivoUuid);
        boolean objetivoStaff = objetivo != null && plugin.esStaff(objetivo);
        boolean objetivoAdmin = objetivo != null && objetivo.hasPermission("odissey.admin");
        if (objetivo != null && !sanciones.permitirObjetivoDialogo(staff, objetivo, objetivoStaff, objetivoAdmin)) return;

        List<SingleOptionDialogInput.OptionEntry> unidades = List.of(
                SingleOptionDialogInput.OptionEntry.create("m", Component.text("Minutos"), true),
                SingleOptionDialogInput.OptionEntry.create("h", Component.text("Horas"), false),
                SingleOptionDialogInput.OptionEntry.create("d", Component.text("Días"), false));
        List<PlantillaBan> plantillas = cargarPlantillasBan();
        List<SingleOptionDialogInput.OptionEntry> opcionesPlantilla = new ArrayList<>();
        opcionesPlantilla.add(SingleOptionDialogInput.OptionEntry.create(
                "personalizado", Component.text("Personalizado"), true));
        for (PlantillaBan plantilla : plantillas) {
            opcionesPlantilla.add(SingleOptionDialogInput.OptionEntry.create(
                    plantilla.id(), Component.text(plantilla.nombre() + " · "
                            + plantilla.cantidad() + plantilla.unidad()), false));
        }
        List<DialogInput> entradas = List.of(
                DialogInput.singleOption("ban_plantilla", Component.text("Plantilla rápida (opcional)"), opcionesPlantilla)
                        .width(400).build(),
                DialogInput.text("ban_duracion", Component.text("Duración (número)"))
                        .width(220).maxLength(6).build(),
                DialogInput.singleOption("ban_unidad", Component.text("Unidad de tiempo"), unidades)
                        .width(220).build(),
                DialogInput.text("ban_motivo", Component.text("Motivo"))
                        .width(400).maxLength(256)
                        .multiline(io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions.create(3, 60))
                        .build());
        ActionButton enviar = ActionButton.builder(Component.text("Enviar ban"))
                .width(150)
                .action(DialogAction.customClick((view, audience) -> enviar(
                        view, audience, objetivoUuid, nombreElegido, objetivoStaff, objetivoAdmin), OPCIONES_CLIC))
                .build();
        ActionButton cancelar = ActionButton.builder(Component.text("Cancelar")).width(120).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Banear a " + nombreElegido))
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                Component.text("Elige una plantilla o escribe tus valores. Si dejas tiempo o motivo vacío, se usará el de la plantilla."))))
                        .inputs(entradas)
                        .build())
                .type(DialogType.multiAction(List.of(enviar), cancelar, 1)));
        staff.showDialog(dialog);
    }

    private void enviar(DialogResponseView view, Audience audience, UUID uuid, String nombre,
                        boolean eraStaff, boolean eraAdmin) {
        if (!(audience instanceof Player staff)) return;
        if (!BaseCommand.tienePermisoAccion(staff, BaseCommand.PERMISO_BAN)) {
            staff.sendMessage(Text.error("Ya no tienes permiso para banear."));
            return;
        }
        String duracion = texto(view.getText("ban_duracion"));
        String unidad = texto(view.getText("ban_unidad"));
        String motivo = texto(view.getText("ban_motivo"));
        String plantillaElegida = texto(view.getText("ban_plantilla"));
        PlantillaBan plantilla = cargarPlantillasBan().stream()
                .filter(p -> p.id().equals(plantillaElegida))
                .findFirst().orElse(null);
        if (plantilla != null) {
            if (duracion.isBlank()) {
                duracion = plantilla.cantidad();
                unidad = plantilla.unidad();
            }
            if (motivo.isBlank()) motivo = plantilla.motivo();
        }
        sanciones.banearDesdeDialogo(staff, uuid, nombre, eraStaff, eraAdmin, duracion, unidad, motivo);
    }

    private List<PlantillaBan> cargarPlantillasBan() {
        List<PlantillaBan> resultado = new ArrayList<>();
        for (Map<?, ?> mapa : plugin.config().getMapList("ban-dialog.presets")) {
            if (resultado.size() >= 20) break;
            String nombre = valor(mapa, "nombre");
            String cantidad = valor(mapa, "cantidad");
            String unidad = valor(mapa, "unidad").toLowerCase(java.util.Locale.ROOT);
            String motivo = valor(mapa, "motivo");
            if (nombre.isBlank() || motivo.isBlank() || !cantidad.matches("[1-9][0-9]{0,5}")
                    || !List.of("m", "h", "d").contains(unidad)) continue;
            resultado.add(new PlantillaBan("plantilla_" + resultado.size(), nombre, cantidad, unidad, motivo));
        }
        return resultado;
    }

    private static String valor(Map<?, ?> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private record PlantillaBan(String id, String nombre, String cantidad, String unidad, String motivo) { }
}
