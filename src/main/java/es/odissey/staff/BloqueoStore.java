package es.odissey.staff;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Comandos bloqueados a un jugador concreto.
 *
 * La sancion bloquea TODO, que es lo correcto pero tambien mucho: para un solo
 * jugador problematico no hace falta dejarle el server entero sin /rtp. Esto es
 * quirurgico: a Rob no le funcionan /rtp ni /home y el resto del mundo no se
 * entera.
 */
public final class BloqueoStore {

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<String, Set<String>> bloqueos = new LinkedHashMap<>();

    public BloqueoStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("bloqueos", "bloqueos.yml");
    }


    private static String clave(String jugador) {
        return jugador == null ? "" : jugador.toLowerCase(Locale.ROOT);
    }

    public void bloquear(String jugador, String comando, String quien) {
        String c = clave(jugador);
        bloqueos.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(comando.toLowerCase(Locale.ROOT));
        guardar();
    }

    /** Quita un comando, o todos si el comando viene vacio. */
    public boolean quitar(String jugador, String comando) {
        String c = clave(jugador);
        Set<String> lista = bloqueos.get(c);
        if (lista == null) return false;
        boolean havia;
        if (comando == null || comando.isEmpty()) {
            havia = !lista.isEmpty();
            bloqueos.remove(c);
        } else {
            havia = lista.remove(comando.toLowerCase(Locale.ROOT));
            if (lista.isEmpty()) bloqueos.remove(c);
        }
        if (havia) guardar();
        return havia;
    }

    public boolean estaBloqueado(String jugador, String comando) {
        Set<String> lista = bloqueos.get(clave(jugador));
        return lista != null && lista.contains(comando.toLowerCase(Locale.ROOT));
    }

    public List<String> de(String jugador) {
        Set<String> lista = bloqueos.get(clave(jugador));
        return lista == null ? new ArrayList<>() : new ArrayList<>(lista);
    }

    public Map<String, Set<String>> todos() {
        return bloqueos;
    }

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        bloqueos.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("bloqueados");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            Set<String> lista = new LinkedHashSet<>(y.getStringList("bloqueados." + clave));
            if (!lista.isEmpty()) bloqueos.put(clave, lista);
        }
        if (!bloqueos.isEmpty()) {
            plugin.getLogger().info("[bloqueos] " + bloqueos.size() + " jugador(es) con comandos bloqueados.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Set<String>> e : bloqueos.entrySet()) {
            y.set("bloqueados." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        almacen.escribir(y);
    }
}
