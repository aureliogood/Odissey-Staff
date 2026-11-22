package es.odissey.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quién está espiando a quién.
 *
 * Esto es solo un diccionario: el espiado de verdad (poner al staff en
 * espectador, el inventario en vivo y el reenvío del chat) lo lleva MenuEspiar y
 * StaffListener. Aquí solo se guarda la pareja, para poder preguntar rápido
 * "este jugador, ¿lo está mirando alguien?".
 *
 * Es un ConcurrentHashMap y no un HashMap porque se consulta desde el hilo del
 * chat de Bukkit, que no es el principal.
 */
public final class EspiaStore {

    private final OdisseyStaff plugin;

    /** A quién espía cada staff. */
    private final java.util.Map<UUID, UUID> espiando =
            new ConcurrentHashMap<>();

    public EspiaStore(OdisseyStaff plugin) {
        this.plugin = plugin;
    }

    public void espiar(UUID staff, UUID objetivo) {
        espiando.put(staff, objetivo);
    }

    /** A quien espía este staff, o null si no espía a nadie. */
    public UUID objetivoDe(UUID staff) {
        return espiando.get(staff);
    }

    public boolean estaEspiando(UUID staff) {
        return espiando.containsKey(staff);
    }

    /** Los staff que estan mirando a este jugador. */
    public List<UUID> espiasDe(UUID objetivo) {
        List<UUID> fuera = new ArrayList<>();
        for (java.util.Map.Entry<UUID, UUID> e : espiando.entrySet()) {
            if (e.getValue().equals(objetivo)) fuera.add(e.getKey());
        }
        return fuera;
    }

    /** El staff deja de espiar (lo usa el propio staff al salir del modo). */
    public void soltar(UUID staff) {
        espiando.remove(staff);
    }

    /**
     * Lo mismo, pero cuando el QUE ESTABA ESPIADO se va.
     *
     * Sin esto, si el objetivo desconecta el staff se queda congelado en modo
     * espectador mirando al vacío y el registro de "quién espía a quién"
     * guardaba un UUID que ya no existe. Es una fuga por cada espiado.
     */
    public void soltarA(UUID objetivo) {
        espiando.values().removeIf(v -> v.equals(objetivo));
    }

    public int total() {
        return espiando.size();
    }

    /** Al apagar el plugin, nadie se queda espiando. */
    public void limpiarTodo() {
        espiando.clear();
    }
}
