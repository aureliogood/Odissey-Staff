package es.odissey.staff;

import org.bukkit.command.CommandSender;

import java.util.List;

/** Límites configurables para reducir sanciones accidentales o en ráfaga. */
public final class ModerationLimits {
    private ModerationLimits() { }

    public static boolean puedeBanear(OdisseyStaff plugin, CommandSender staff, boolean permanente) {
        if (!plugin.config().getBoolean("limites-sanciones.activo", true)) return true;
        long ahora = System.currentTimeMillis();
        List<SanctionStore.Entrada> ultimas = plugin.historial().recientes(
                Math.max(100, plugin.config().getInt("historial.max-registros", 2000)));
        int bansHora = 0;
        int permanentesDia = 0;
        for (SanctionStore.Entrada e : ultimas) {
            if (!e.staff.equalsIgnoreCase(staff.getName())) continue;
            if (!e.tipo.equals("baneo") && !e.tipo.equals("ipban")) continue;
            if (e.cuando >= ahora - 3_600_000L) bansHora++;
            if (e.cuando >= ahora - 86_400_000L && e.extra.toLowerCase(java.util.Locale.ROOT).contains("permanente")) {
                permanentesDia++;
            }
        }
        int maxHora = Math.max(1, plugin.config().getInt("limites-sanciones.bans-por-hora", 20));
        if (bansHora >= maxHora) {
            staff.sendMessage(Text.error("Alcanzaste el límite de " + maxHora + " bans por hora."));
            return false;
        }
        if (permanente) {
            int maxDia = Math.max(1, plugin.config().getInt("limites-sanciones.permanentes-por-dia", 5));
            if (permanentesDia >= maxDia) {
                staff.sendMessage(Text.error("Alcanzaste el límite de " + maxDia + " bans permanentes por día."));
                return false;
            }
        }
        return true;
    }
}
