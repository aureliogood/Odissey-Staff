package es.odissey.staff;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Minijuego de cálculo para reducir una sanción en curso. */
public final class RedemptionCommand implements CommandExecutor {
    private static final long DURACION_RETO = 120_000L;
    private final OdisseyStaff plugin;
    private final Map<UUID, Reto> retos = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final class Reto {
        final int respuesta;
        final long vence;
        int intentos;
        Reto(int respuesta, int intentos) {
            this.respuesta = respuesta;
            this.intentos = intentos;
            this.vence = System.currentTimeMillis() + DURACION_RETO;
        }
    }

    public RedemptionCommand(OdisseyStaff plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender quien, Command comando, String etiqueta, String[] args) {
        if (!(quien instanceof Player jugador)) {
            quien.sendMessage(Text.error("La redención se juega dentro del servidor."));
            return true;
        }
        if (!plugin.config().getBoolean("redemption.enabled", true)
                || !plugin.config().getBoolean("redemption.captcha-math.enabled", true)) {
            jugador.sendMessage(Text.error("La redención está desactivada."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("start")) iniciar(jugador);
        else if (args[0].equalsIgnoreCase("answer") && args.length == 2) responder(jugador, args[1]);
        else jugador.sendMessage(Text.error("Uso: /redemption start | /redemption answer <resultado>"));
        return true;
    }

    private void iniciar(Player jugador) {
        UUID uuid = jugador.getUniqueId();
        if (!plugin.sanciones().tiene(uuid)) {
            jugador.sendMessage(Text.error("No tienes una sanción activa que puedas reducir."));
            return;
        }
        if (retos.containsKey(uuid)) {
            jugador.sendMessage(Text.info("Ya tienes un reto activo. Responde con /redemption answer <resultado>."));
            return;
        }
        boolean unico = plugin.config().getBoolean("redemption.captcha-math.one-time", false);
        if (unico && plugin.redencion().yaCanjeo(uuid)) {
            jugador.sendMessage(Text.error("Ya usaste tu oportunidad de redención."));
            return;
        }
        long enfriamiento = Math.max(0, plugin.leerTiempoEnMs(plugin.config().getString("redemption.cooldown", "24h")));
        long transcurrido = System.currentTimeMillis() - plugin.redencion().ultimoIntento(uuid);
        if (transcurrido < enfriamiento) {
            jugador.sendMessage(Text.error("Podrás volver a intentarlo en "
                    + SancionStore.enLetras(enfriamiento - transcurrido) + "."));
            return;
        }
        int a = random.nextInt(18) + 2;
        int b = random.nextInt(18) + 2;
        int intentos = Math.max(1, plugin.config().getInt("redemption.captcha-math.max-attempts", 3));
        retos.put(uuid, new Reto(a + b, intentos));
        plugin.redencion().iniciar(uuid);
        jugador.sendMessage(Text.marca() + "Reto de redención: " + a + " + " + b + " = ?");
        jugador.sendMessage(Text.info("Tienes dos minutos y " + intentos + " intento(s). Usa /redemption answer <resultado>."));
    }

    private void responder(Player jugador, String texto) {
        UUID uuid = jugador.getUniqueId();
        Reto reto = retos.get(uuid);
        if (reto == null) {
            jugador.sendMessage(Text.error("No tienes un reto activo. Inícialo con /redemption start."));
            return;
        }
        if (System.currentTimeMillis() >= reto.vence) {
            retos.remove(uuid, reto);
            jugador.sendMessage(Text.error("El reto expiró."));
            return;
        }
        int respuesta;
        try { respuesta = Integer.parseInt(texto); }
        catch (NumberFormatException e) {
            jugador.sendMessage(Text.error("Responde solo con el resultado numérico."));
            return;
        }
        if (respuesta != reto.respuesta) {
            reto.intentos--;
            if (reto.intentos <= 0) {
                retos.remove(uuid, reto);
                jugador.sendMessage(Text.error("Respuesta incorrecta. Se acabaron los intentos."));
            } else {
                jugador.sendMessage(Text.error("Respuesta incorrecta. Te quedan " + reto.intentos + " intento(s)."));
            }
            return;
        }
        retos.remove(uuid, reto);
        long reduccion = Math.max(1, plugin.config().getInt("redemption.captcha-math.reduction", 10)) * 60_000L;
        long reducida = plugin.sanciones().reducir(uuid, reduccion);
        if (reducida <= 0) {
            jugador.sendMessage(Text.error("La sanción ya había terminado."));
            return;
        }
        if (plugin.config().getBoolean("redemption.captcha-math.one-time", false)) plugin.redencion().canjear(uuid);
        jugador.sendMessage(Text.exito("¡Correcto! Redujiste " + SancionStore.enLetras(reducida) + " de tu sanción."));
    }
}
