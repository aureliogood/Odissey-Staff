package es.odissey.staff;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda el estado de cada jugador: si esta en modo staff, si esta invisible,
 * si vuela y si esta congelado.
 *
 * Todo lo que se toca al entrar se guarda antes para poder devolverlo al
 * salir. Un /vanish que no se puede desactivar con un clic, o que deja a un
 * admin volando por el mapa porque se le paso, acaba siendo un bug que la
 * gente reporta en dos semanas.
 */
  public final class StaffManager {

      /**
       * Teletransportes que ha pedido el propio plugin.
       *
       * Al bloquear el teletransporte de un congelado hay que dejar pasar los
       * del plugin: al aplicar el congelado, al devolverlo a su sitio y al
       * mandarlo a la celda. Sin esta lista, congelar a alguien le haria
       * perder la posicion en la que se le sanciono, que es justo lo que el
       * castigo debe hacer.
       */
      private final java.util.Set<UUID> teletransporteLibre = java.util.concurrent.ConcurrentHashMap.newKeySet();

      /** Si este teletransporte lo permite el bloqueo de congelado. */
      public boolean teletransportePermitido(Player jugador) {
          return teletransporteLibre.contains(jugador.getUniqueId());
      }

      /**
       * Teletransporte hecho por el propio plugin.
       *
       * Pasa por aqui todo lo que mueve a un jugador desde dentro del plugin:
       * congelar, devolver a su sitio, mandarlo a la celda. Lo de los comandos
       * de staff ya va antes de congelar, pero tambien pasan por aqui por si
       * algun dia se cambia el orden.
       */
      public void teletransportar(Player jugador, Location destino) {
          teletransporteLibre.add(jugador.getUniqueId());
          try {
              jugador.teleport(destino);
          } finally {
              teletransporteLibre.remove(jugador.getUniqueId());
          }
      }


    /** Lo que un jugador tiene antes de que el staff plugin lo toque. */
    public static final class Estado {
        public boolean vanish;
        public boolean staffMode;
        public boolean congelado;
        public boolean staffModePrevioFly;
        public GameMode gameModePrevio;
        public Location origenStaffMode;   // para volver al salir
        public String rango = "";
        public int rangoLongitud;
        // Para devolver al jugador exactamente como estaba. Antes se ponia
        // siempre 0.2f al descongelar, asi que un jugador que llevaba una
        // poción de velocidad se quedaba con la velocidad base para siempre.
        public float walkSpeedPrevio;
        public boolean allowFlightPrevio;
        public boolean flyingPrevio;
        // Congelado: por que y cuando.
        public String congeladoMotivo = "";
        public long congeladoDesde;
        public String congeladoPor = "";
        // Espectador: a quien mira y como estaba antes.
        public boolean espectador;
        public UUID spectatorObjetivo;
        public Location spectatorOrigen;
        public GameMode spectatorGameModePrevio;
        /**
         * El casco que llevaba antes de que le pusiéramos el hielo.
         *
         * Sin esto, congelar a alguien con un casco puesto le costaba el casco:
         * al soltarlo se le ponia un hueco y el equipo entero. Se guarda solo si
         * de verdad lo hemos pisado (helmetCongeladoPuesto), para no devolver
         * un casco fantasma si el jugador ya no lo lleva.
         */
        public ItemStack helmetPrevio;
        public boolean helmetCongeladoPuesto;
    }

    private final OdisseyStaff plugin;
    // ConcurrentHashMap y no HashMap: el chat de Bukkit es asincrono, y el
    // listener del rango (AsyncPlayerChatEvent) llama a estado() desde el hilo
    // del chat mientras el hilo principal anade y quita estados. Con un HashMap
    // normal eso es una corrupcion silenciosa que un dia se come un jugador de
    // la lista de congelados.
    private final Map<UUID, Estado> estados = new java.util.concurrent.ConcurrentHashMap<>();

    public StaffManager(OdisseyStaff plugin) {
        this.plugin = plugin;
    }

    /** Guardar el congelado en disco, para que un reinicio no lo libere. */
    private void persistir(Player jugador) {
        if (plugin.congelados() == null) return;
        plugin.congelados().guardar(estado(jugador), jugador);
    }

    /**
     * El estado de un jugador, o null si no hay ninguno.
     *
     * Antes hacia falta siempre y con un null reventaba con un
     * NullPointerException al pedir el UUID. Ese fallo era real: /staffinfo
     * sobre alguien que no estaba conectado pasaba un null aqui y tumbaba el
     * comando entero.
     */
    public Estado estado(Player jugador) {
        if (jugador == null) return null;
        return estados.computeIfAbsent(jugador.getUniqueId(), k -> new Estado());
    }

    /**
     * El estado si ya existe, o null. No crea uno vacio.
     *
     * Para cuando solo se quiere LEER lo que hay guardado (por ejemplo el casco
     * previo al desconectarse): estado() crearia una entrada vacia y ensuciaria
     * el mapa para alguien que no tiene nada.
     */
    public Estado estadoONull(Player jugador) {
        if (jugador == null) return null;
        return estados.get(jugador.getUniqueId());
    }

    public boolean vanish(Player jugador) {
        return estado(jugador) != null && estado(jugador).vanish;
    }


    public boolean congelado(Player jugador) {
        Estado e = estado(jugador);
        return e != null && e.congelado;
    }

    public int contar() {
        return estados.size();
    }


    // ---------------- Vanish ----------------

    public void cambiarVanish(Player jugador, boolean activar) {
        Estado e = estado(jugador);
        e.vanish = activar;
        // Si esta en modo staff y se desactiva el vanish, ya no tiene sentido
        // seguir invisible: se le devuelve a visible.
        if (activar) {
            for (Player otro : jugador.getServer().getOnlinePlayers()) {
                if (otro.equals(jugador)) continue;
                if (plugin.esStaff(otro)) {
                    otro.showPlayer(plugin, jugador);
                } else {
                    otro.hidePlayer(plugin, jugador);
                }
            }
        } else {
            for (Player otro : jugador.getServer().getOnlinePlayers()) {
                if (otro.equals(jugador)) continue;
                otro.showPlayer(plugin, jugador);
            }
        }
    }

    public void cambiarVanish(Player jugador) {
        cambiarVanish(jugador, !estado(jugador).vanish);
    }

    /** Lo que se aplica al entrar o salir el mundo (nadie debe quedar hidden). */
    public void reaplicar(Player jugador) {
        if (estado(jugador).vanish) {
            for (Player otro : jugador.getServer().getOnlinePlayers()) {
                if (!otro.equals(jugador)) otro.hidePlayer(plugin, jugador);
            }
        }
        for (Player otro : jugador.getServer().getOnlinePlayers()) {
            if (otro.equals(jugador)) continue;
            if (estado(jugador).vanish && !plugin.esStaff(otro)) continue;
            otro.showPlayer(plugin, jugador);
        }
        // Y al reves: el que acaba de entrar ve por defecto a todo el mundo, así
        // que hay que volver a esconderle a quien siga en vanish. Sin esto, con
        // un admin en invisible, cualquier jugador que entra lo ve entero.
        reaplicarContra(jugador);
    }

    /** Vuelve a esconder al jugador que entra a quien este en vanish. */
    private void reaplicarContra(Player entrante) {
        for (Player otro : plugin.getServer().getOnlinePlayers()) {
            if (otro.equals(entrante)) continue;
            if (!estado(otro).vanish) continue;
            if (plugin.esStaff(entrante)) continue;   // el staff si puede ver al vanish
            entrante.hidePlayer(plugin, otro);
        }
    }

    // ---------------- Congelar ----------------

    /**
     * Congela a un jugador y guarda como estaba.
     *
     * Los efectos se aplican aqui y no en el listener, para que congelar por
     * comando (/freeze) y congelar con el bloque de hielo hagan EXACTAMENTE lo
     * mismo. Antes cada camino hacia los estados tinha su propio codigo, que es
     * como uno se queda sin la oscuridad puesta.
     */
    public void congelar(Player jugador) {
        congelar(jugador, "", "");
    }

    /**
     * Congela guardando el motivo y quien lo hizo, para poder consultarlo luego
     * con /staffinfo y /stafffrozen.
     */
    public void congelar(Player jugador, String motivo, String quien) {
        Estado e = estado(jugador);
        if (e.congelado) return;
        e.congelado = true;
        e.congeladoMotivo = motivo == null ? "" : motivo;
        e.congeladoPor = quien == null ? "" : quien;
        e.congeladoDesde = System.currentTimeMillis();
        e.gameModePrevio = jugador.getGameMode();
        e.walkSpeedPrevio = jugador.getWalkSpeed();
        e.allowFlightPrevio = jugador.getAllowFlight();
        e.flyingPrevio = jugador.isFlying();

        jugador.setGameMode(GameMode.ADVENTURE);
        jugador.setWalkSpeed(0);
        jugador.setFlying(false);
        jugador.setAllowFlight(false);

        // El bloque en la cabeza: lo que hace visible el congelado desde lejos
        // sin que el staff tenga que estar al lado.
        //
        // El casco que llevaba se guarda para devolvérselo al soltarlo. Hay dos
        // casos en los que NO se guarda lo que haya ahora mismo:
        //
        //  - Si ya hay uno en memoria, es que vienes de restaurarDesdeDisco() y
        //    el de verdad es el del archivo: pisarlo lo perdería.
        //  - Si lo que lleva puesto es un bloque de hielo, ese hielo lo pusimos
        //    nosotros. Solo pasa al volver de un servidor reiniciado (Bukkit
        //    guarda el inventario con el hielo puesto), y guardarlo como "casco
        //    previo" haría que al soltarle se le devolviera el hielo y su casco
        //    real desapareciera para siempre.
        if (e.helmetPrevio == null) {
            try {
                ItemStack actual = jugador.getInventory().getHelmet();
                if (!esHielo(actual)) e.helmetPrevio = actual;
            } catch (Throwable t) { /* versiones raras sin getHelmet */ }
        }
        e.helmetCongeladoPuesto = false;
        ponerHielo(jugador);

        // A disco. Los datos de "como estaba" ya estan guardados en el Estado de
        // arriba, asi que da igual que este aqui: lo que se copia al archivo es
        // el estado previo, no el actual.
        persistir(jugador);

        // Oscuridad infinita: -1 de duracion en la API es infinito, y el
        // amplificador alto lo hace TOTALmente oscuro. Los dos efectos se
        // quitan de verdad en descongelar(), que es donde estaba el bug.
        jugador.removePotionEffect(PotionEffectType.DARKNESS);
        jugador.removePotionEffect(PotionEffectType.SLOWNESS);
        jugador.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                PotionEffect.INFINITE_DURATION, 255, false, false));
        jugador.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                PotionEffect.INFINITE_DURATION, 255, false, false));

        teletransportar(jugador, jugador.getLocation());
    }

    /**
     * Quita el congelado y devuelve al jugador su estado.
     *
     * Lo importante: se quitan DARKNESS y SLOWNESS. Tenian duracion infinita,
     * asi que sin este removePotionEffect el jugador se quedaba ciego y lento
     * para siempre aunque ya pudiese moverse, que era justo lo que pasaba.
     */
    public void descongelar(Player jugador) {
        Estado e = estado(jugador);
        if (!e.congelado) return;
        e.congelado = false;

        jugador.removePotionEffect(PotionEffectType.DARKNESS);
        jugador.removePotionEffect(PotionEffectType.SLOWNESS);

        jugador.setGameMode(e.gameModePrevio == null ? GameMode.SURVIVAL : e.gameModePrevio);
        jugador.setWalkSpeed(e.walkSpeedPrevio <= 0f ? 0.2f : e.walkSpeedPrevio);
        jugador.setAllowFlight(e.allowFlightPrevio);
        jugador.setFlying(e.allowFlightPrevio && e.flyingPrevio);

        quitarHielo(jugador);

        // Fuera del archivo: si no, al reiniciar volveria a estar congelado.
        if (plugin.congelados() != null) plugin.congelados().quitar(jugador.getUniqueId());
    }

    /**
     * Pone el bloque de hielo en la cabeza.
     *
     * Va con setHelmet() y no con setItem(36, ...): el slot 36 es el del casco,
     * pero setHelmet() es el que ademas ajusta el estado de armadura del
     * jugador, y si no, el juego lo corrige en el siguiente tick y lo tira.
     *
     * Se comprueba antes que no haya ya un casco nuestro puesto, para no
     * pisar dos veces el que tenia guardado.
     */
    private void ponerHielo(Player jugador) {
        try {
            org.bukkit.inventory.PlayerInventory inv = jugador.getInventory();
            ItemStack actual = inv.getHelmet();
            if (esHielo(actual)) {
                Estado e = estado(jugador);
                e.helmetCongeladoPuesto = true;
                return;
            }
            inv.setHelmet(hielo());
            Estado e = estado(jugador);
            e.helmetCongeladoPuesto = true;
            jugador.updateInventory();
        } catch (Throwable t) {
            plugin.getLogger().fine("[congelado] no se pudo poner el hielo: " + t);
        }
    }

    /**
     * Quita el hielo y devuelve lo que llevaba.
     *
     * Solo se toca la cabeza si lo que hay ahora es nuestro hielo. Si el
     * jugador ha conseguido quitarse el bloque a mano (por ejemplo por un
     * comando de otro plugin) o se le ha puesto otra cosa encima, no se pisa:
     * esa persona o plugin ha decidido que lleva otra cosa y no es cosa
     * nuestra quitarselo.
     */
    private void quitarHielo(Player jugador) {
        try {
            org.bukkit.inventory.PlayerInventory inv = jugador.getInventory();
            Estado e = estado(jugador);
            if (!esHielo(inv.getHelmet())) {
                e.helmetCongeladoPuesto = false;
                e.helmetPrevio = null;
                return;
            }
            inv.setHelmet(e.helmetPrevio);
            e.helmetCongeladoPuesto = false;
            e.helmetPrevio = null;
            jugador.updateInventory();
        } catch (Throwable t) {
            plugin.getLogger().fine("[congelado] no se pudo quitar el hielo: " + t);
        }
    }

    /** Un bloque de hielo normal, sin nombre: para que no parezca un item. */
    private ItemStack hielo() {
        return new ItemStack(org.bukkit.Material.ICE);
    }

    private boolean esHielo(ItemStack item) {
        return item != null
                && (item.getType() == org.bukkit.Material.ICE
                || item.getType() == org.bukkit.Material.PACKED_ICE
                || item.getType() == org.bukkit.Material.BLUE_ICE
                || item.getType() == org.bukkit.Material.FROSTED_ICE);
    }

    /**
     * Devuelve el estado congelado guardado a disco. Se llama al entrar el
     * jugador: sin esto, reiniciar el server liberaba a todos los congelados.
     */
    public void restaurarDesdeDisco(Player jugador) {
        if (plugin.congelados() == null) return;
        FreezeStore.Datos d = plugin.congelados().esta(jugador.getUniqueId())
                ? plugin.congelados().datosDe(jugador.getUniqueId()) : null;
        if (d == null) return;
        // Si tiene sancion activa, la sancion manda: ya se encarga ella de
        // devolverlo a su sitio y de congelarlo.
        if (plugin.sanciones().tiene(jugador.getUniqueId())) return;

        Location sitio = plugin.congelados().sitioDe(d);
        if (sitio != null) {
            teletransportar(jugador, sitio);
            jugador.setFallDistance(0f);
        }
        // El casco previo se pone ANTES de congelar(), no despues. congelar()
        // guarda como "casco previo" lo que haya en la cabeza si todavia no hay
        // uno en memoria, y al volver del reinicio lo que hay en la cabeza es
        // el hielo que le pusimos nosotros: guardandolo, persistir() escribia ese
        // hielo en el archivo por encima del casco real y, al soltarle, se le
        // devolvia un bloque de hielo en lugar de su casco.
        if (d.casco != null && !d.casco.isEmpty()) {
            estado(jugador).helmetPrevio = FreezeStore.deserializar(d.casco);
        }
        congelar(jugador, d.motivo, d.por);
        Estado e = estado(jugador);
        e.gameModePrevio = d.gameMode;
        e.walkSpeedPrevio = d.walkSpeed;
        e.allowFlightPrevio = d.allowFlight;
        e.flyingPrevio = d.flying;
        if (d.desde > 0) e.congeladoDesde = d.desde;
        jugador.sendMessage(Text.aviso("Sigues congelado desde antes de que el servidor reiniciara."));
        plugin.getLogger().info("[congelados] " + jugador.getName() + " vuelve y sigue congelado.");
    }

    /**
     * Vuelve a poner el hielo si se ha perdido.
     *
     * El bloque en la cabeza se pierde cuando el jugador muere (cae al suelo
     * como cualquier item) y se puede perder si alguien se lo quita a mano.
     * Sin esto, congelar a alguien y que le maten significaba que volvia
     * "normal": sin el hielo, y con el casco guardado que ya no volveria
     * porque el Estado deja de decir que lo pusiéramos nosotros.
     */
    public void reaplicarHielo(Player jugador) {
        Estado e = estado(jugador);
        if (!e.congelado) return;
        try {
            if (e.helmetCongeladoPuesto && esHielo(jugador.getInventory().getHelmet())) return;
        } catch (Throwable t) { /* seguimos */ }
        ponerHielo(jugador);
    }

    /** Quien esta congelado ahora mismo. */
    public List<Player> congelados() {
        List<Player> lista = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (congelado(p)) lista.add(p);
        }
        return lista;
    }

    // ---------------- Espectador ----------------

    /**
     * Modo espectador: ver el juego desde otro jugador sin que te vea.
     *
     * El vanish esconde el cuerpo, pero sigues caminando, haciendo ruido y
     * puedes romper bloques sin querer. En espectador no puedes tocar nada, y
     * es lo que se usa para molestar a alguien a solas.
     */
    public void entrarEspectador(Player staff, Player objetivo) {
        Estado e = estado(staff);
        if (e.espectador) salirEspectador(staff);
        e.espectador = true;
        e.spectatorObjetivo = objetivo.getUniqueId();
        e.spectatorGameModePrevio = staff.getGameMode();
        e.spectatorOrigen = staff.getLocation().clone();

        staff.setGameMode(GameMode.SPECTATOR);
        cambiarVanish(staff, true);
        staff.setSpectatorTarget(objetivo);
    }

    public void salirEspectador(Player staff) {
        Estado e = estado(staff);
        if (!e.espectador) return;
        e.espectador = false;
        e.spectatorObjetivo = null;
        staff.setSpectatorTarget(null);
        if (e.spectatorGameModePrevio != null) staff.setGameMode(e.spectatorGameModePrevio);
        if (e.spectatorOrigen != null && e.spectatorOrigen.getWorld() != null) {
            staff.teleport(e.spectatorOrigen);
        }
        cambiarVanish(staff, false);
    }

    public boolean esEspectador(Player jugador) {
        return estado(jugador).espectador;
    }

    // ---------------- Limpieza ----------------

    /**
     * Al salir no se deja a nadie escondido ni congelado.
     *
     * Si el staff estaba en espectador hay que sacarlo primero: si no, se
     * desconecta dentro de otro jugador y al volver a entrar sigue en
     * espectador mirando a alguien que ya no esta.
     */
    public void salirForzoso(Player jugador) {
        Estado e = estados.get(jugador.getUniqueId());
        if (e != null && e.espectador) salirEspectador(jugador);
        e = estados.remove(jugador.getUniqueId());
        if (e == null) return;
        if (e.vanish) {
            for (Player otro : plugin.getServer().getOnlinePlayers()) {
                otro.showPlayer(plugin, jugador);
            }
        }
    }

    public void limpiarTodo() {
        estados.clear();
    }
}
