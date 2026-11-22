package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Guarda el cooldown y los canjes únicos del minijuego de redención. */
public final class RedemptionStore {
    private final Almacen almacen;
    private final Map<UUID, Long> ultimoIntento = new HashMap<>();
    private final Set<UUID> canjeados = new HashSet<>();

    public RedemptionStore(OdisseyStaff plugin) {
        almacen = plugin.almacen("redencion", "redencion.yml");
    }

    public synchronized void cargar() {
        YamlConfiguration y = almacen.leer();
        ultimoIntento.clear();
        canjeados.clear();
        var intentos = y.getConfigurationSection("ultimo-intento");
        if (intentos != null) for (String clave : intentos.getKeys(false)) {
            try { ultimoIntento.put(UUID.fromString(clave), y.getLong("ultimo-intento." + clave)); }
            catch (IllegalArgumentException ignorado) { }
        }
        var usados = y.getConfigurationSection("canjeados");
        if (usados != null) for (String clave : usados.getKeys(false)) {
            try { if (y.getBoolean("canjeados." + clave)) canjeados.add(UUID.fromString(clave)); }
            catch (IllegalArgumentException ignorado) { }
        }
    }

    public synchronized long ultimoIntento(UUID uuid) { return ultimoIntento.getOrDefault(uuid, 0L); }
    public synchronized boolean yaCanjeo(UUID uuid) { return canjeados.contains(uuid); }

    public synchronized void iniciar(UUID uuid) {
        ultimoIntento.put(uuid, System.currentTimeMillis());
        guardar();
    }

    public synchronized void canjear(UUID uuid) {
        canjeados.add(uuid);
        guardar();
    }

    public synchronized void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : ultimoIntento.entrySet()) y.set("ultimo-intento." + e.getKey(), e.getValue());
        for (UUID uuid : canjeados) y.set("canjeados." + uuid, true);
        almacen.escribir(y);
    }
}
