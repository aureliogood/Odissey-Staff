package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Borrado manual de datos vanilla del jugador, siempre con vista previa. */
public final class PlayerDataWipeCommand implements CommandExecutor {
    private static final long EXPIRA_MS = 60_000L;
    private final OdisseyStaff plugin;
    private final Map<UUID, Solicitud> pendientes = new ConcurrentHashMap<>();

    private record Solicitud(UUID objetivo, String nombre, List<Path> archivos, long vence) { }

    public PlayerDataWipeCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("odissey.admin")) {
            sender.sendMessage(Text.error("Necesitas odissey.admin para borrar datos."));
            return true;
        }
        if (!plugin.config().getBoolean("data-wipe.enabled", false)) {
            sender.sendMessage(Text.error("El borrado de datos está desactivado en data-wipe.enabled."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            confirmar(sender);
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("preview")) {
            sender.sendMessage(Text.error("Uso: /staffdatawipe preview <jugador> | confirm"));
            return true;
        }
        String nombre = args[1];
        Player online = Bukkit.getPlayerExact(nombre);
        if (online != null) {
            sender.sendMessage(Text.error("El jugador debe estar desconectado antes de borrar sus datos."));
            return true;
        }
        UUID uuid = plugin.conexiones().uuidDe(nombre);
        if (uuid == null) {
            sender.sendMessage(Text.error("No hay UUID registrado para " + nombre + "."));
            return true;
        }
        List<Path> archivos = archivos(uuid);
        pendientes.put(id(sender), new Solicitud(uuid, nombre, archivos, System.currentTimeMillis() + EXPIRA_MS));
        sender.sendMessage(Text.aviso("Vista previa de " + nombre + " (UUID " + uuid + "): "
                + archivos.size() + " archivo(s) vanilla de datos, estadísticas o avances."));
        sender.sendMessage(Text.error("La acción es irreversible. Usa /staffdatawipe confirm en 60 segundos."));
        return true;
    }

    private List<Path> archivos(UUID uuid) {
        List<Path> resultado = new ArrayList<>();
        String id = uuid.toString();
        for (World world : Bukkit.getWorlds()) {
            Path carpeta = world.getWorldFolder().toPath().toAbsolutePath().normalize();
            agregarExistente(resultado, carpeta.resolve("playerdata").resolve(id + ".dat"));
            agregarExistente(resultado, carpeta.resolve("playerdata").resolve(id + ".dat_old"));
            agregarExistente(resultado, carpeta.resolve("stats").resolve(id + ".json"));
            agregarExistente(resultado, carpeta.resolve("advancements").resolve(id + ".json"));
        }
        return resultado;
    }

    private static void agregarExistente(List<Path> archivos, Path candidato) {
        Path normalizado = candidato.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizado) && !Files.isSymbolicLink(normalizado)) archivos.add(normalizado);
    }

    private void confirmar(CommandSender sender) {
        Solicitud solicitud = pendientes.remove(id(sender));
        if (solicitud == null || System.currentTimeMillis() > solicitud.vence) {
            sender.sendMessage(Text.error("No tienes una vista previa vigente. Vuelve a ejecutarla."));
            return;
        }
        if (Bukkit.getPlayer(solicitud.objetivo) != null) {
            sender.sendMessage(Text.error("El jugador volvió a conectarse. No se borró ningún archivo."));
            return;
        }
        int borrados = 0;
        for (Path archivo : solicitud.archivos) {
            try {
                if (Files.isRegularFile(archivo) && !Files.isSymbolicLink(archivo) && Files.deleteIfExists(archivo)) borrados++;
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo borrar un archivo vanilla para UUID " + solicitud.objetivo + ".");
            }
        }
        sender.sendMessage(Text.exito("Borrados " + borrados + " de " + solicitud.archivos.size()
                + " archivos de Minecraft de " + solicitud.nombre + "."));
        plugin.getLogger().warning("[data-wipe] " + sender.getName() + " borró " + borrados
                + " archivos vanilla para " + solicitud.nombre + " (" + solicitud.objetivo + ").");
    }

    private static UUID id(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : UUID.nameUUIDFromBytes(
                ("console:" + sender.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
