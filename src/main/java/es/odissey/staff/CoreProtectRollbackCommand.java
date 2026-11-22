package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rollback de CoreProtect con vista previa, radio limitado y confirmación. */
public final class CoreProtectRollbackCommand implements CommandExecutor {
    private static final long EXPIRA_MS = 60_000L;
    private final OdisseyStaff plugin;
    private final Map<UUID, Solicitud> pendientes = new ConcurrentHashMap<>();

    private record Solicitud(String jugador, int segundos, int radio, Location centro,
                             long vence, int coincidencias) { }

    public CoreProtectRollbackCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("odissey.admin")) {
            sender.sendMessage(Text.error("Necesitas odissey.admin para revertir bloques."));
            return true;
        }
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(Text.error("Este rollback requiere que un staff jugador esté dentro del mundo afectado."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            confirmar(staff);
            return true;
        }
        if (args.length != 4 || !args[0].equalsIgnoreCase("preview")) {
            staff.sendMessage(Text.error("Uso: /staffrollback preview <jugador-online> <segundos> <radio> | confirm"));
            return true;
        }
        Plugin coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null || !coreProtect.isEnabled()) {
            staff.sendMessage(Text.error("CoreProtect no está instalado o habilitado."));
            return true;
        }
        Player objetivo = Bukkit.getPlayerExact(args[1]);
        if (objetivo == null) {
            staff.sendMessage(Text.error("El jugador objetivo debe estar conectado para fijar el centro del área."));
            return true;
        }
        int segundos;
        int radio;
        try {
            segundos = Integer.parseInt(args[2]);
            radio = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            staff.sendMessage(Text.error("Segundos y radio deben ser números enteros."));
            return true;
        }
        int maxSegundos = Math.max(60, plugin.config().getInt("coreprotect.max-seconds", 86400));
        int maxRadio = Math.max(1, plugin.config().getInt("coreprotect.max-radius", 32));
        if (segundos < 1 || segundos > maxSegundos || radio < 1 || radio > maxRadio) {
            staff.sendMessage(Text.error("Rango permitido: 1-" + maxSegundos + " segundos y radio 1-" + maxRadio + "."));
            return true;
        }
        Location centro = objetivo.getLocation().getBlock().getLocation().clone();
        Object api;
        try { api = api(); }
        catch (ReflectiveOperationException | RuntimeException e) {
            staff.sendMessage(Text.error("No se pudo abrir la API de CoreProtect."));
            return true;
        }
        staff.sendMessage(Text.info("Consultando CoreProtect; el mundo no cambiará hasta confirmar."));
        consultar(api, staff.getUniqueId(), objetivo.getName(), segundos, radio, centro);
        return true;
    }

    private void consultar(Object api, UUID staffId, String jugador, int segundos, int radio, Location centro) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Method lookup = metodo(api, "performLookup");
                Object resultado = lookup.invoke(api, segundos, List.of(jugador), null,
                        null, null, null, radio, centro);
                int cantidad = resultado instanceof List<?> lista ? lista.size() : -1;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player staff = Bukkit.getPlayer(staffId);
                    if (staff == null) return;
                    if (cantidad < 0) {
                        staff.sendMessage(Text.error("CoreProtect no pudo generar la vista previa."));
                        return;
                    }
                    if (cantidad == 0) {
                        staff.sendMessage(Text.info("CoreProtect no encontró cambios para revertir con esos filtros."));
                        return;
                    }
                    Solicitud solicitud = new Solicitud(jugador, segundos, radio, centro,
                            System.currentTimeMillis() + EXPIRA_MS, cantidad);
                    pendientes.put(staffId, solicitud);
                    staff.sendMessage(Text.aviso("Vista previa: " + cantidad + " registro(s) de " + jugador
                            + " en radio " + radio + " durante " + segundos + "s."));
                    staff.sendMessage(Text.error("Si confirmas, el rollback modifica el mundo. Usa /staffrollback confirm en 60 segundos."));
                });
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Falló la consulta de vista previa de CoreProtect.");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player staff = Bukkit.getPlayer(staffId);
                    if (staff != null) staff.sendMessage(Text.error("No se pudo consultar la API de CoreProtect."));
                });
            }
        });
    }

    private void confirmar(Player staff) {
        Solicitud solicitud = pendientes.remove(staff.getUniqueId());
        if (solicitud == null || System.currentTimeMillis() > solicitud.vence) {
            staff.sendMessage(Text.error("No tienes una vista previa vigente. Vuelve a ejecutar preview."));
            return;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("CoreProtect")) {
            staff.sendMessage(Text.error("CoreProtect ya no está habilitado."));
            return;
        }
        staff.sendMessage(Text.aviso("Rollback confirmado. CoreProtect está aplicando los cambios en segundo plano."));
        UUID staffId = staff.getUniqueId();
        String staffName = staff.getName();
        String mundo = solicitud.centro.getWorld().getName();
        Object api;
        try { api = api(); }
        catch (ReflectiveOperationException | RuntimeException e) {
            staff.sendMessage(Text.error("No se pudo abrir la API de CoreProtect."));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Method rollback = metodo(api, "performRollback");
                Object resultado = rollback.invoke(api, solicitud.segundos, List.of(solicitud.jugador), null,
                        null, null, null, solicitud.radio, solicitud.centro);
                int cantidad = resultado instanceof List<?> lista ? lista.size() : -1;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player ejecutor = Bukkit.getPlayer(staffId);
                    if (ejecutor != null) ejecutor.sendMessage(cantidad < 0
                            ? Text.error("CoreProtect no confirmó el rollback; revisa su consola.")
                            : Text.exito("Rollback terminado. CoreProtect reportó " + cantidad + " registro(s)."));
                });
                plugin.getLogger().info("[rollback] " + staffName + " confirmó rollback de "
                        + solicitud.jugador + " en " + mundo
                        + " radio=" + solicitud.radio + " tiempo=" + solicitud.segundos + "s registros=" + cantidad);
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Falló el rollback solicitado a CoreProtect.");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player ejecutor = Bukkit.getPlayer(staffId);
                    if (ejecutor != null) ejecutor.sendMessage(Text.error("CoreProtect no pudo aplicar el rollback."));
                });
            }
        });
    }

    private Object api() throws ReflectiveOperationException {
        Plugin coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null || !coreProtect.isEnabled()) throw new IllegalStateException("CoreProtect inactivo");
        return coreProtect.getClass().getMethod("getAPI").invoke(coreProtect);
    }

    private static Method metodo(Object api, String nombre) throws NoSuchMethodException {
        return api.getClass().getMethod(nombre, int.class, List.class, List.class,
                List.class, List.class, List.class, int.class, Location.class);
    }
}
