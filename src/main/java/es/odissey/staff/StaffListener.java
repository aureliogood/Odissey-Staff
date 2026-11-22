package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Todo lo que pasa cuando un staff usa sus items, mas el estado que se
 * mantiene cuando un jugador entra o sale.
 *
 * Los items se reconocen por la etiqueta que llevan dentro (ver StaffItems), no
 * por el tipo: asi una perla normal de un jugador no activa nada.
 */
public final class StaffListener implements Listener {

    private final OdisseyStaff plugin;
    private StaffManager gestor;
    private StaffItems items;
    private MenuTeleporte menu;

    // Para no que un item se pueda usar 60 veces por segundo.
    private final java.util.Map<String, Long> ultimoUso = new java.util.HashMap<>();

    public StaffListener(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.items = new StaffItems(plugin);
    }

    public void setGestor(StaffManager gestor) {
        this.gestor = gestor;
    }


    public void setItems(StaffItems items) {
        if (items != null) this.items = items;
    }
    public StaffItems items() {
        return items;
    }

    public void setMenu(MenuTeleporte menu) {
        if (menu != null) this.menu = menu;
    }

    private StaffManager.Estado estado(Player jugador) {
        return gestor().estado(jugador);
    }

    private StaffManager gestor() {
        return gestor != null ? gestor : plugin.gestor();
    }

    // ---------------- Arranque y cierre ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alEntrar(PlayerJoinEvent evento) {
        Player jugador = evento.getPlayer();
        // La IP se apunta al entrar, que es de donde sale /staffinfo. En un
        // servidor con proxy puede venir vacia; en ese caso sale "no disponible".
        try {
            if (jugador.getAddress() != null && jugador.getAddress().getAddress() != null) {
                plugin.anotarIp(jugador.getUniqueId(), jugador.getAddress().getAddress().getHostAddress());
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("[staffinfo] no se pudo leer la IP: " + t);
        }

        // Ojo: esto NO se puede hacer con un showPlayer() a todos. Si un staff
        // estaba en vanish y entra alguien, ese showPlayer lo hacia visible para
        // el recien llegado y el vanish se rompia solo. Lo unico que hace falta
        // al entrar es quitar el oculto que Bukkit deja por defecto; reaplicar()
        // mas abajo vuelve a esconder a quien tenga que estar escondido.
        gestor().reaplicar(jugador);

          // Registro de conexiones: lo usan /staffonline y /staffalt.
          plugin.conexiones().entrar(jugador.getName(), jugador.getUniqueId(), plugin.ipDe(jugador));

          // Cuenta nueva, IP de un baneado, sancion puesta o lista de vigilancia.
          // Va despues de lo de arriba porque necesita la IP y las conexiones ya
          // anotadas.
          AvisosEntrada.revisar(jugador, plugin);

        // Si estaba congelado antes de que el servidor reiniciara, se le vuelve a
        // congelar y se le devuelve al mismo punto. Sin esto, reiniciar el server
        // era una forma de salir de un congelado sin penalización.
        gestor().restaurarDesdeDisco(jugador);

        // Si esta en la lista de vigilancia, se avisa al resto del staff. Se
        // avisa al staff, NUNCA al jugador: si se le avisa a el, se cambia de
        // cuenta y la lista no sirve de nada.
        VigilanciaStore.Marca marca = plugin.vigilancia().marca(jugador.getName());
        if (marca != null) {
            plugin.getLogger().info("[vigilancia] " + jugador.getName() + " ha entrado. Esta fichado: " + marca.motivo);
            for (Player otro : jugador.getServer().getOnlinePlayers()) {
                if (otro.equals(jugador) || !plugin.esStaff(otro)) continue;
                otro.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "EN LA LISTA DE VIGILANCIA  "
                        + ChatColor.WHITE + jugador.getName()
                        + ChatColor.YELLOW + " · " + (marca.motivo.isEmpty() ? "sin motivo" : marca.motivo));
                otro.sendMessage(Text.info("Fichado por " + marca.quien + " el "
                        + VigilanciaStore.fecha(marca.cuando)));
                try {
                    otro.playSound(otro.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                } catch (Throwable t) { /* el sonido puede cambiar entre versiones */ }
            }
        }

        // Sancion en curso: se le devuelve a su sitio y se le vuelve a congelar.
        // El reloj estaba parado desde que se fue.
        plugin.sanciones().alEntrar(jugador);
        // Si se le acabo un silencio mientras no estaba conectado, se le avisa
        // al entrar: si no, sigue pensando que esta mudo.
        plugin.silencios().avisarAlEntrar(jugador);
        // Las herramientas vuelven solas si estaban guardadas y el jugador es
        // staff. Antes habia que poner /staff otra vez en cada conexion.
        plugin.kit().restaurar(jugador);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent evento) {
        Player jugador = evento.getPlayer();
        // Si se va el objetivo, se suelta el espiado de quien lo estaba
        // mirando: si no, el staff se queda en espectador dentro del vacio y
        // con un UUID guardado que ya no es de nadie.
        plugin.menuEspiar().alSalirObjetivo(jugador);
        // Y si el que se va es el que espiaba, se le suelta a el.
        plugin.menuEspiar().limpiar(jugador);
        // Se anota la salida y lo que llevaba en el inventario: por si hay que
        // mirar un robo y el jugador ya no esta dentro.
        plugin.conexiones().salir(jugador.getUniqueId());
        plugin.actividad().guardarInventario(jugador);
        // Si tiene sancion en curso se guarda donde estaba: al volver le
        // devuelve a ese mismo punto con el reloj parado.
        plugin.sanciones().alSalir(jugador);
        // Si se fue a media sancion, el menu se queda con un estado a medias.
        plugin.menuSancion().limpiar(jugador);
        plugin.menuJugadores().limpiar(jugador);
        plugin.menuStaff().limpiar(jugador);
        plugin.menuSanciones().limpiar(jugador);
        plugin.menuCopiar().limpiar(jugador);
        plugin.menuBanear().limpiar(jugador);
        plugin.menuHistorial().limpiar(jugador);
        plugin.espera().limpiar(jugador);

        // Se guarda como esta el inventario para poder devolverselo al volver.
        plugin.kit().guardar(jugador);
        gestor().salirForzoso(jugador);
        items.quitar(jugador);
        plugin.olvidarIp(jugador.getUniqueId());
        olvidarUso(jugador);
    }

      @EventHandler(priority = EventPriority.HIGHEST)
      public void alRespawn(PlayerRespawnEvent evento) {
          final Player jugador = evento.getPlayer();

          // Si tiene sancion, reaparece en el sitio donde se le sano, no en su
          // cama. Con la cama bloqueada al tumbarse, aun asi puede tener una
          // puesta de antes de la sancion, y si no, cada muerte era una puerta
          // de salida.
          SancionStore.Registro r = plugin.sanciones().obtener(jugador.getUniqueId());
          if (r != null) {
              org.bukkit.Location sitio = r.sitio();
              if (sitio != null) evento.setRespawnLocation(sitio);
          }

          Bukkit.getScheduler().runTask(plugin, () -> {
              if (!jugador.isOnline()) return;
              gestor().reaplicar(jugador);
              items.quitar(jugador);
              if (gestor().congelado(jugador)) {
                  // Al morir y reaparecer se le vuelven a poner la oscuridad,
                  // la lentitud y el bloque en la cabeza. Sin esto, un jugador
                  // congelado al que le matan sale del sitio sin el hielo y el
                  // staff ve a un jugador normal donde deberia ver un retenido.
                  gestor().reaplicarHielo(jugador);
              }
              plugin.kit().restaurar(jugador);
          });
      }

    /** Si el plugin se apaga con gente en vanish, se les quita a todos. */
    @EventHandler
    public void alApagar(PluginDisableEvent evento) {
        if (!plugin.getName().equals(evento.getPlugin().getName())) return;
        for (Player jugador : plugin.getServer().getOnlinePlayers()) {
            for (Player otro : plugin.getServer().getOnlinePlayers()) {
                if (!otro.equals(jugador)) otro.showPlayer(plugin, jugador);
            }
        }
    }

// ---------------- Items: click en el aire o bloque ----------------

    /**
     * Cualquier accion con uno de nuestros items se cancela, no solo el click
     * derecho.
     *
     * Esto importa con la perla de ender: si el evento no llega a cancelarse,
     * Minecraft la lanza igual y el jugador acaba en un sitio al azar a tres
     * bloques de donde estaba, que es justo lo que se queria evitar. Con solo
     * mirar RIGHT_CLICK_* habiajugadores mirando a una entidad o a un bloque
     * en los que la perla si se lanzaba.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alUsarItem(PlayerInteractEvent evento) {
        ItemStack mano = evento.getItem();
        if (mano == null) return;
        StaffItems.Herramienta h = items.de(mano);
        if (h == null) return;
        if (!plugin.esStaff(evento.getPlayer())) return;

        // Se cancela siempre: nada de lanzar la perla, nada de colocar el hielo.
        evento.setCancelled(true);
        evento.setUseInteractedBlock(Event.Result.DENY);
        evento.setUseItemInHand(Event.Result.DENY);
        if (evento.getAction() != Action.RIGHT_CLICK_AIR && evento.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (evento.getHand() != null && !evento.getHand().equals(org.bukkit.inventory.EquipmentSlot.HAND)) {
            return;   // la otra mano duplicaria el efecto
        }

        if (h == StaffItems.Herramienta.IR_A_JUGADOR) {
            // Agachado + click derecho abre el menu de jugadores. Sin agacharse
            // va a uno al azar, que es lo rapido para saltar por el mapa.
            //
            // No se usa "shift + click" porque PlayerInteractEvent no dice si
            // se mantuvo shift: ese dato solo existe en InventoryClickEvent.
            // Agacharse es el idioma habitual en Minecraft para la accion
            // alternativa de un item.
            if (evento.getPlayer().isSneaking()) {
                menu.abrir(evento.getPlayer());
            } else {
                irAJugador(evento.getPlayer());
            }
        } else if (h == StaffItems.Herramienta.VANISH) {
            alternarVanish(evento.getPlayer());
        }
        // CONGELAR, INVENTARIO y MANDAR_A_CASA necesitan mirar a un jugador, y
        // se gestionan en alClickJugador().
    }

    /**
     * La perla: teletransporte a un jugador conectado al azar.
     *
     * No se lanza nunca. Cancelar el PlayerInteractEvent no basta en Paper
     * 1.21 (el disparo se procesa aparte), asi que el corte de verdad esta en
     * alLanzarProyectil(), que salta cuando la perla ya iba a salir volando.
     *
     * Si no hay nadie conectado no hace nada y avisa, porque sin objetivo no hay
     * a donde ir.
     */
    private void irAJugador(Player jugador) {
        if (!puedeUsar(jugador, "perla")) return;
        Player destino = jugadorAleatorio(jugador);
        if (destino == null) {
            jugador.sendMessage(Text.error("No hay ningun jugador conectado."));
            return;
        }
        jugador.teleport(destino.getLocation());
        jugador.sendMessage(Text.exito("Has ido con " + destino.getName() + "."));
    }

    private Player jugadorAleatorio(Player quien) {
        java.util.List<Player> otros = new java.util.ArrayList<>();
        for (Player p : quien.getServer().getOnlinePlayers()) {
            if (p.equals(quien)) continue;
            otros.add(p);
        }
        if (otros.isEmpty()) return null;
        return otros.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(otros.size()));
    }

    /** La pocion: vanish. */
    private void alternarVanish(Player jugador) {
        if (!puedeUsar(jugador, "vanish")) return;
        boolean ahoraVisible = !gestor().vanish(jugador);
        gestor().cambiarVanish(jugador, ahoraVisible);
        if (ahoraVisible) {
            jugador.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "VANISH");
            jugador.sendMessage(Text.aviso("Ahora eres invisible."));
        } else {
            jugador.sendMessage(Text.exito("Ya te ven."));
        }
    }

// ---------------- Items: click en un jugador ----------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickJugador(PlayerInteractEntityEvent evento) {
        if (!(evento.getRightClicked() instanceof Player)) return;
        ItemStack mano = evento.getPlayer().getInventory().getItemInMainHand();
        if (mano == null) return;
        StaffItems.Herramienta h = items.de(mano);
        if (h == null) return;
        if (!plugin.esStaff(evento.getPlayer())) return;

        Player objetivo = (Player) evento.getRightClicked();
        // Se cancela siempre que lleve un item nuestro en la mano, para que
        // ninguno de los dos items se quede a medio usar.
        evento.setCancelled(true);
        if (evento.getHand() != null && !evento.getHand().equals(org.bukkit.inventory.EquipmentSlot.HAND)) return;
        if (objetivo.equals(evento.getPlayer())) {
            evento.getPlayer().sendMessage(Text.error("Eso no puedes hacertelo a ti."));
            return;
        }

        if (h == StaffItems.Herramienta.CONGELAR) {
            congelar(evento.getPlayer(), objetivo);
        } else if (h == StaffItems.Herramienta.INVENTARIO) {
            inventario(evento.getPlayer(), objetivo);
        } else if (h == StaffItems.Herramienta.MANDAR_A_CASA) {
            mandarACasa(evento.getPlayer(), objetivo);
        }
    }

    /**
     * Las herramientas no se lanzan como proyectiles.
     *
     * Cancelar el PlayerInteractEvent NO basta para que una perla de ender deje
     * de salir disparada: en Paper 1.21 el disparo se procesa aparte y la perla
     * se lanza igualmente. Por eso habia una perla que se tiraba de verdad
     * aunque el click estuviera cancelado.
     *
     * Este es el evento que se dispara en el momento de lanzar, ya con el
     * proyectil creado, y se cancela sin mas: la perla no sale. Es de Paper
     * (com.destroystokyo.paper...), no de Bukkit, asi que se comprueba que la
     * clase exista antes de usarla: en un servidor Bukkit puro el plugin
     * seguiria arrancando, solo que sin esta proteccion.
     *
     * Se comprueba la ETIQUETA del item, no el material. Asi solo se corta si
     * es una herramienta nuestra, y una perla normal de un cajon sigue
     * funcionando como tiene que ser.
     */
      @EventHandler(priority = EventPriority.HIGHEST)
      public void alLanzarProyectil(com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent evento) {
          Player jugador = evento.getPlayer();

          // Un sancionado no lanza nada. Aqui esta el bug que se notifico: la perla
          // de ender teletransporta, y el bloqueo de movimiento no la para,
          // porque el teletransporte le cambia la posicion de golpe y no hay ningun
          // PlayerMoveEvent que cortar. Es decir: el sancionado se escapaba
          // tirando una perla.
          if (plugin.inmovilizado(jugador)) {
              evento.setCancelled(true);
              evento.setShouldConsume(false);
              avisarCongelado(jugador, "No puedes lanzar nada.");
              return;
          }

          if (!plugin.esStaff(jugador)) return;
          if (items.de(evento.getItemStack()) == null) return;
          evento.setCancelled(true);
          evento.setShouldConsume(false);
          jugador.sendActionBar(ChatColor.GRAY + "Esta herramienta no se lanza.");
      }

      /**
       * Red de seguridad por si el proyectil llega a existir pese a lo de arriba.
       *
       * ProjectileLaunchEvent es de Bukkit y salta una vez mas tarde, con la
       * entidad ya creada. Cancelarlo aqui evita que salga volando.
       */
      @EventHandler(priority = EventPriority.HIGHEST)
      public void alCrearProyectil(org.bukkit.event.entity.ProjectileLaunchEvent evento) {
          if (!(evento.getEntity().getShooter() instanceof Player)) return;
          Player jugador = (Player) evento.getEntity().getShooter();

          if (plugin.inmovilizado(jugador)) {
              evento.setCancelled(true);
              avisarCongelado(jugador, "No puedes lanzar nada.");
              return;
          }

          if (!plugin.esStaff(jugador)) return;
          if (items.de(jugador.getInventory().getItemInMainHand()) == null) return;
          evento.setCancelled(true);
      }

      /**
       * La fruta de chorus: otro teletransporte que se salta el congelado.
       *
       * Se corta en PlayerInteractEvent, que es donde el jugador decide
       * usarla. El teletransporte en si tambien lo corta alTeletransportarse().
       */
      @EventHandler(priority = EventPriority.HIGHEST)
      public void alUsarObjetoQueMueve(org.bukkit.event.player.PlayerInteractEvent evento) {
          ItemStack mano = evento.getItem();
          if (mano == null) return;
          if (mano.getType() != Material.CHORUS_FRUIT) return;
          if (!plugin.inmovilizado(evento.getPlayer())) return;

          evento.setCancelled(true);
          evento.setUseItemInHand(Event.Result.DENY);
          evento.setUseInteractedBlock(Event.Result.DENY);
          avisarCongelado(evento.getPlayer(), "No puedes usar eso.");
      }

      /**
       * La cama: si se puede tumbarse en ella, puede reaparecer en su casa.
       */
      @EventHandler(priority = EventPriority.HIGHEST)
      public void alTumbarseEnCama(org.bukkit.event.player.PlayerBedEnterEvent evento) {
          if (!plugin.inmovilizado(evento.getPlayer())) return;
          evento.setCancelled(true);
          avisarCongelado(evento.getPlayer(), "No puedes dormir.");
      }

      /**
       * El cierre de la puerta de los teletransportes.
       *
       * Todo lo de arriba (perla, chorus, camas, portales de otro plugin)
       * acaba en un PlayerTeleportEvent. Cortando aqui se tapa el caso general,
          * no solo los que se conocen hoy.
       *
       * Se dejan pasar dos cosas:
       *   - los teletransportes que hace este propio plugin, que se marcan en
       *     StaffManager.teletransportar() (congelar, devolver a su sitio, celda),
       *   - los de un admin, que si no el staff no podria mover a un sancionado.
       */
      @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
      public void alTeletransportarse(org.bukkit.event.player.PlayerTeleportEvent evento) {
          Player jugador = evento.getPlayer();
          if (!gestor().congelado(jugador)) return;
          if (gestor().teletransportePermitido(jugador)) return;
          if (jugador.hasPermission("odissey.admin")) return;

          evento.setCancelled(true);
          avisarCongelado(jugador, "No te puedes mover de aqui.");
      }

      /**
       * El aviso va por action bar y no por chat a proposito: un sancionado
       * puede pulsar la perla cuarenta veces seguidas, y cuarenta mensajes en
       * el chat solo tapa el resto.
       */
      private void avisarCongelado(Player jugador, String texto) {
          jugador.sendActionBar(ChatColor.RED + texto);
      }


    /**
     * Las herramientas no hacen daño.
     *
     * Esta versión de Paper no tiene PlayerEntityInteractEvent, que es lo que
     * permitiría pillar el clic izquierdo sobre un jugador. Sin esto, clicar
     * con el bloque de hielo o con la vara de blaze no solo no hacia nada: el
     * staff pegaba al jugador de verdad, porque el clic izquierdo es un golpe.
     *
     * Se corta en el damage porque ese evento salta siempre, se accione como se
     * accione el item.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alPegar(org.bukkit.event.entity.EntityDamageByEntityEvent evento) {
        if (!(evento.getDamager() instanceof Player)) return;
        Player staff = (Player) evento.getDamager();
        if (items.de(staff.getInventory().getItemInMainHand()) == null) return;
        evento.setCancelled(true);
        staff.sendActionBar(ChatColor.GRAY + "Tus herramientas no pegan.");
    }

    /**
     * Paper lanza este evento en vez del anterior cuando el objeto esta dentro
     * de una entidad (armadura, por ejemplo) y en algunos casos de click
     * sobre jugador. Sin este, mirar a un jugador y clicar con el bloque hacia
     * que el bloque se colocara en el suelo en vez de congelar a alguien.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alPulsarEntidad(org.bukkit.event.player.PlayerInteractAtEntityEvent evento) {
        if (!(evento.getRightClicked() instanceof Player)) return;
        ItemStack mano = evento.getPlayer().getInventory().getItemInMainHand();
        if (mano == null) return;
        StaffItems.Herramienta h = items.de(mano);
        if (h == null) return;
        evento.setCancelled(true);
    }

    /**
     * El bloque de hielo: congela y, si ya estaba congelado, lo suelta.
     *
     * Antes hacia falta /freeze para quitarlo, y ademas /freeze no le quitaba
     * la oscuridad, asi que el jugador se quedaba ciego y lento para siempre.
     * Ahora los dos caminos pasan por el mismo sitio: gestor().congelar() y
     * gestor().descongelar(), que es donde se quitan los efectos.
     */
    private void congelar(Player staff, Player objetivo) {
        if (!puedeUsar(staff, "freeze")) return;
        if (gestor().congelado(objetivo)) {
            // Si tiene sancion, el congelado NO se puede soltar con el bloque de
            // hielo: si no, un clic le quita la sancion entera y el reloj se
            // para. Para levantarla hay que usar /staffsancion <nombre> off.
            if (plugin.sanciones().tiene(objetivo.getUniqueId())) {
                staff.sendMessage(Text.error(objetivo.getName() + " tiene una sanción activa."));
                staff.sendMessage(Text.info("Para levantarla: /staffsancion " + objetivo.getName() + " off"));
                return;
            }
            gestor().descongelar(objetivo);
            objetivo.sendMessage(Text.exito("Ya puedes moverte."));
            staff.sendMessage(Text.exito("Has soltado a " + objetivo.getName() + "."));
            return;
        }
        gestor().congelar(objetivo);
        objetivo.sendMessage(Text.aviso("Fuiste congelado por un Staff"));
        objetivo.sendMessage(Text.info("No te puedes mover ni hacer nada hasta que te suelten."));
        staff.sendMessage(Text.exito("Has congelado a " + objetivo.getName() + ". Clic otra vez para soltarlo."));
    }

    /**
     * La vara de blaze: manda al jugador a su cama, y si no tiene, al spawn.
     *
     * "Su ultima ubicacion" se interpreta como el spawn del mundo, que es donde
     * aparece un jugador que no ha marcado ninguna cama. Si mas adelante se
     * instala EssentialsX, sus /home estan aparte y se podrian enganchar aqui.
     */
    private void mandarACasa(Player staff, Player objetivo) {
        if (!puedeUsar(staff, "blaze")) return;

        org.bukkit.Location destino = objetivo.getRespawnLocation();
        boolean hayCama = destino != null;

        if (!hayCama) {
            destino = staff.getServer().getWorlds().get(0).getSpawnLocation();
        }
        if (destino == null) {
            staff.sendMessage(Text.error("No hay ningun sitio al que mandarlo."));
            return;
        }

        // Si esta congelado, se le suelta antes: si no, al teletransportarse se
        // queda quieto y ciego en el sitio al que le mandamos, sin poder salir.
        boolean estabaCongelado = gestor().congelado(objetivo);
        if (estabaCongelado) gestor().descongelar(objetivo);

        gestor().teletransportar(objetivo, destino);
        // La blindness del Nether es un efecto del mundo, no nuestro.
        objetivo.removePotionEffect(PotionEffectType.BLINDNESS);

        if (hayCama) {
            objetivo.sendMessage(Text.info("Un miembro del staff te ha enviado a tu cama."));
            staff.sendMessage(Text.exito("Has mandado a " + objetivo.getName() + " a su cama."));
        } else {
            objetivo.sendMessage(Text.info("Un miembro del staff te ha enviado al spawn (no tienes cama)."));
            staff.sendMessage(Text.aviso(objetivo.getName() + " no tiene cama: lo has mandado al spawn."));
        }
        if (estabaCongelado) {
            staff.sendMessage(Text.info("Tambien se le ha quitado el congelado."));
        }
    }

    /** El cofre: ver el inventario de otro jugador. */
    private void inventario(Player staff, Player objetivo) {
        if (!puedeUsar(staff, "invsee")) return;
        staff.openInventory(objetivo.getInventory());
        staff.sendMessage(Text.info("Viendo el inventario de " + objetivo.getName() + "."));
    }

    // ---------------- Utilidades ----------------

    /** Evita que un item se dispare mil veces por segundo. */
    private boolean puedeUsar(Player jugador, String que) {
        String clave = jugador.getUniqueId() + ":" + que;
        long ahora = System.currentTimeMillis();
        long antes = ultimoUso.getOrDefault(clave, 0L);
        if (ahora - antes < 600L) return false;
        ultimoUso.put(clave, ahora);
        return true;
    }

    /**
     * Olvida el anti-spam de uso de un jugador al salir.
     *
     * Sin esto la clave se queda en el mapa para siempre: el servidor acumula
     * una entrada por cada jugador que ha tocado una herramienta alguna vez, y
     * eso no se va solo ni aunque se borre la lista deStaffManager.
     */
    private void olvidarUso(Player jugador) {
        String prefijo = jugador.getUniqueId() + ":";
        ultimoUso.keySet().removeIf(k -> k.startsWith(prefijo));
    }

    /**
     * Los items no se mueven de su ranura.
     *
     * Sin esto el staff podia arrastrar el cofre a la ranura 20 y usar el
     * bloque de hielo desde la 30, con lo que ya no se sabe cual herramienta
     * toca en cada hueco. Se bloquean mientras esten en su sitio; si alguien se
     * los guarda en un cofre, ya no estan en su ranura y se mueven libres.
     *
     * Se corta de cuatro maneras porque hay cuatro formas de mover un item:
     * click normal, arrastre con varios huecos a la vez, cambio con la tecla de
     * numero y cambio entre las dos manos.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickEnInventario(org.bukkit.event.inventory.InventoryClickEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player jugador = (Player) evento.getWhoClicked();
        if (!plugin.esStaff(jugador)) return;

        org.bukkit.inventory.Inventory invClicado = evento.getClickedInventory();
        boolean propio = invClicado != null && invClicado.equals(jugador.getInventory());

        // getSlot() es la ranura DENTRO del inventario que se ha pulsado (0-8 =
        // barra rapida en el inventario del propio jugador). getRawSlot() seria
        // el indice dentro de TODA la vista (con el cofre delante la barra son
        // 81-89), que no vale para mirar getInventory().getItem(). Antes se
        // comparaba getRawSlot() con 0-8, asi que estas reglas nunca saltaban
        // para la barra rapida y las herramientas se podian mover igual.
        int ranura = evento.getSlot();

        // 1) El item que se coge viene de una ranura bloqueada.
        if (propio && esRanuraBloqueada(jugador, ranura)) {
            evento.setCancelled(true);
            return;
        }

        // 2) Se va a soltar en una ranura bloqueada.
        if (propio && ranura >= 0 && ranura < 9) {
            ItemStack nuevo = evento.getCursor();
            if (nuevo != null && items.de(nuevo) != null
                    && esRanuraBloqueada(jugador, ranura)) {
                evento.setCancelled(true);
                return;
            }
        }

        // 3) Mayus+click: mueve el item de golpe entre inventarios, y el evento
        //    de destino no siempre salta. Se corta aqui directamente.
        if (evento.isShiftClick() && propio && ranura >= 0 && ranura < 9) {
            if (items.de(jugador.getInventory().getItem(ranura)) != null) {
                evento.setCancelled(true);
                return;
            }
        }

        // 4) Tecla de numero: intercambia la ranura pulsada con la del item.
        if (evento.getHotbarButton() >= 0) {
            ItemStack mano = jugador.getInventory().getItem(evento.getHotbarButton());
            if (items.de(mano) != null) {
                evento.setCancelled(true);
                return;
            }
        }
    }

    /** Si esa ranura de la barra rapida tiene una herramienta nuestra en su sitio. */
    private boolean esRanuraBloqueada(Player jugador, int ranura) {
        if (ranura < 0 || ranura > 8) return false;
        ItemStack item = jugador.getInventory().getItem(ranura);
        if (items.de(item) == null) return false;
        // Solo se bloquea si la herramienta corresponde a ESA ranura.
        return items.ranuraDe(items.de(item)) == ranura;
    }

    /** Arrastrar varios items a la vez, que puede incluir una ranura bloqueada. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrar(org.bukkit.event.inventory.InventoryDragEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player jugador = (Player) evento.getWhoClicked();
        if (!plugin.esStaff(jugador)) return;
        // getRawSlots() son indices de TODA la vista, asi que no valen para
        // getInventory().getItem(). Se mira el TIPO de cada ranura en la vista:
        // QUICKBAR es la barra rapida de un jugador, sin importar donde este
        // (barra del propio inventario o la de abajo con un cofre delante).
        org.bukkit.inventory.InventoryView vista = evento.getView();
        for (int cruda : evento.getRawSlots()) {
            if (vista.getSlotType(cruda) != org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR) {
                continue;
            }
            ItemStack actual = vista.getItem(cruda);
            ItemStack nuevo = evento.getNewItems().get(cruda);
            if ((actual != null && items.de(actual) != null)
                    || (nuevo != null && items.de(nuevo) != null)) {
                evento.setCancelled(true);
                return;
            }
        }
    }

    /** Intercambiar la mano principal con la secundaria. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alCambiarManos(org.bukkit.event.player.PlayerSwapHandItemsEvent evento) {
        Player jugador = evento.getPlayer();
        if (!plugin.esStaff(jugador)) return;
        if (items.de(jugador.getInventory().getItemInMainHand()) != null
                || items.de(jugador.getInventory().getItemInOffHand()) != null) {
            evento.setCancelled(true);
        }
    }

    /** Ni tirar las herramientas al suelo. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alTirar(org.bukkit.event.player.PlayerDropItemEvent evento) {
        Player jugador = evento.getPlayer();
        if (!plugin.esStaff(jugador)) return;
        if (items.de(evento.getItemDrop().getItemStack()) != null) {
            evento.setCancelled(true);
            jugador.sendActionBar(ChatColor.GRAY + "Esta herramienta no se tira.");
        }
    }

    // ---------------- Rango del staff ----------------

    /**
     * El rango que pone /stafftools, delante del nombre en el chat.
     *
     * Antes /stafftools guardaba el rango en un sitio y NADA lo leia: se
     * escribia el rango y no pasaba nada. Este es el sitio donde se usa.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alEscribirConRango(org.bukkit.event.player.AsyncPlayerChatEvent evento) {
        String rango = gestor().estado(evento.getPlayer()).rango;
        if (rango == null || rango.isEmpty()) return;
        // Antes se recortaba del mensaje todo lo que ocupara el nombre del
        // jugador, por si otro plugin lo habia puesto delante. Este listener es
        // LOWEST, o sea el PRIMERO: cuando corre, el mensaje es exactamente lo
        // que el jugador acaba de escribir y nunca lleva el nombre. El recorte
        // se comia los primeros caracteres de cada mensaje ("Ana" escribiendo
        // "hola" publicaba "a"), y en este plugin no hay ningun sitio que
        // anteponga el nombre. Solo se quita si de verdad empieza por el.
        String sinNombre = evento.getMessage();
        String nombre = evento.getPlayer().getName();
        if (sinNombre.length() > nombre.length()
                && sinNombre.substring(0, nombre.length()).equalsIgnoreCase(nombre)) {
            sinNombre = sinNombre.substring(nombre.length()).trim();
        }
        evento.setMessage(rango + ChatColor.WHITE + sinNombre);
    }

    // ---------------- Registro de actividad ----------------

    /** Roto y colocado: para /staffsnapshot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alRomper(org.bukkit.event.block.BlockBreakEvent evento) {
        plugin.actividad().anotar(evento.getPlayer(), "rompio",
                evento.getBlock().getType().name().toLowerCase(java.util.Locale.ROOT)
                        + " en " + mundoDe(evento.getBlock().getWorld().getName())
                        + " " + evento.getBlock().getX() + "," + evento.getBlock().getY() + "," + evento.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alColocar(org.bukkit.event.block.BlockPlaceEvent evento) {
        plugin.actividad().anotar(evento.getPlayer(), "coloco",
                evento.getBlock().getType().name().toLowerCase(java.util.Locale.ROOT)
                        + " en " + mundoDe(evento.getBlock().getWorld().getName())
                        + " " + evento.getBlock().getX() + "," + evento.getBlock().getY() + "," + evento.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alHacerFuego(org.bukkit.event.block.BlockBurnEvent evento) {
        plugin.getLogger().fine("[actividad] un bloque ardió en " + evento.getBlock().getWorld().getName());
    }

    private static String mundoDe(String mundo) {
        return mundo == null ? "?" : mundo;
    }

    /** Recorta un texto largo para el registro de actividad. */
    static String corta(String texto, int max) {
        if (texto == null) return "";
        String t = texto.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    // ---------------- Bloqueo de comandos ----------------

    /**
     * Un jugador inmovilizado no puede usar NINGUN comando.
     *
     * "Inmovilizado" es estar congelado con el bloque de hielo o tener una
     * sancion de celda. Antes el congelado solo bloqueaba el movimiento, asi
     * que un staff podia "congelar" a alguien y este seguia usando /rtp, /home
     * y el resto de comandos de otros plugins: la congelacion era un aviso
     * con pasos.
     *
     * Sin esto, la sancion se esquivaba en un segundo: /rtp, /home, /spawn,
     * /warp... son todos de otros plugins y saltaban igual. El jugador estaba
     * "congelado" pero se teletransportaba donde queria, que deja de ser una
     * sancion y pasa a ser un aviso.
     *
     * Se dejan fuera solo tres cosas, y por necesidad:
     *   - la autenticacion, para que quien tenga plugin de registro pueda
     *     entrar y no se quede encerrado sin poder hacer nada,
     *   - el staff, para que si a un admin le pisan la sancion pueda usar
     *     /staffsancion off y sacarla.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alComandoBloqueado(org.bukkit.event.player.PlayerCommandPreprocessEvent evento) {
        Player jugador = evento.getPlayer();
        if (plugin.esStaff(jugador)) return;

        String mensaje = evento.getMessage();
        String cmd = mensaje.startsWith("/")
                ? mensaje.substring(1).split("\\s+")[0].toLowerCase(java.util.Locale.ROOT)
                : mensaje.toLowerCase(java.util.Locale.ROOT);

// Bloqueo puntual: a este jugador y solo a este comando.
        if (!SancionStore.comandoPermitido(cmd)
                && plugin.bloqueos().estaBloqueado(jugador.getName(), cmd)) {
              evento.setCancelled(true);
              jugador.sendMessage(Text.error("El comando /" + cmd + " no lo puedes usar."));
              AvisosEntrada.comandoBloqueado(jugador, cmd, plugin);
              return;
        }

        if (!plugin.inmovilizado(jugador)) {
            // Lo que va haciendo, para poder mirarlo luego con /staffsnapshot.
            plugin.actividad().anotar(jugador, "comando", "/" + cmd);
            return;
        }

        // Inmovilizado (congelado o sancionado): no puede usar nada.
        if (SancionStore.comandoPermitido(cmd)) return;

        evento.setCancelled(true);
        jugador.sendMessage(Text.error(plugin.motivoInmovilizado(jugador)));
    }

    /**
 * Al morir, el hielo de la cabeza no cae al suelo.
 *
 * El bloque que le ponemos al congelado es un item normal, asi que al morir se
 * cae al suelo como todo lo demas. Y eso es un problema doble: deja un bloque
 * de hielo tirado en medio del mapa, que alguien lo recoge, y ademas el
 * jugador al reaparecer se queda sin el hasta que el reloj se lo vuelve a
 * poner.
 *
 * Se quita del loot y ya. El casco que llevaba antes se conserva solo: esta en
 * memoria (el Estado), no en el item, asi que al reaparecer le vuelve.
 */
@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
public void alMorir(org.bukkit.event.entity.PlayerDeathEvent evento) {
    if (!gestor().congelado(evento.getEntity())) return;
    java.util.List<org.bukkit.inventory.ItemStack> drops = evento.getDrops();
    if (drops == null || drops.isEmpty()) return;
    drops.removeIf(item -> {
        if (item == null) return false;
        // Las herramientas de staff tampoco caen al morir. Sin esto, al morir
        // el staff las soltaba, su inventario quedaba vacio y al reaparecer
        // items.quitar() + kit.restaurar() le volvian a poner las herramientas:
        // una copia en el suelo y otra encima, o sea duplicadas.
        if (items.de(item) != null) return true;
        org.bukkit.Material t = item.getType();
        return t == org.bukkit.Material.ICE
                || t == org.bukkit.Material.PACKED_ICE
                || t == org.bukkit.Material.BLUE_ICE
                || t == org.bukkit.Material.FROSTED_ICE;
    });
}

/**
 * Tampoco puede abrir cofres ni coger cosas del suelo.
 *
 * Congelado ya no puede moverse, pero un jugador quieto puede abrir un cofre
 * que tenga al lado y llevárselo. Con la sancion eso tiene que ser
 * imposible tambien.
 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alAbrirContenedor(org.bukkit.event.inventory.InventoryOpenEvent evento) {
        if (!(evento.getPlayer() instanceof Player)) return;
        Player jugador = (Player) evento.getPlayer();
        if (!plugin.inmovilizado(jugador)) return;
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alClicarContenedor(org.bukkit.event.inventory.InventoryClickEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player)) return;
        Player jugador = (Player) evento.getWhoClicked();
        if (!plugin.inmovilizado(jugador)) return;
        evento.setCancelled(true);
    }

    /** Ni recoger items del suelo. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alCoger(org.bukkit.event.player.PlayerAttemptPickupItemEvent evento) {
        Player jugador = evento.getPlayer();
        if (!plugin.inmovilizado(jugador)) return;
        evento.setCancelled(true);
    }

    /**
     * Ni romper ni colocar bloques.
     *
     * Un jugador congelado al lado de una base puede seguir demoliéndola: no se
     * mueve, pero los bloques que tiene delante se rompen igual. Y un sancionado
     * igual. Es el mismo agujero que el de los cofres, con mas dano.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alRomperBloque(org.bukkit.event.block.BlockBreakEvent evento) {
        if (!plugin.inmovilizado(evento.getPlayer())) return;
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alColocarBloque(org.bukkit.event.block.BlockPlaceEvent evento) {
        if (!plugin.inmovilizado(evento.getPlayer())) return;
        evento.setCancelled(true);
    }

    /**
     * Ni romper entitys: una hacha frozen sigue partiendo arboles y un snowball
     * sigue tirandose. Y ni dano: recibe, pero no hace.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alDanar(org.bukkit.event.entity.EntityDamageByEntityEvent evento) {
        if (evento.getDamager() instanceof Player
                && plugin.inmovilizado((Player) evento.getDamager())) {
            evento.setCancelled(true);
        }
    }

    /**
     * Ni interactuar con nada: puertas, botones, vallas, hitos.
     *
     * Sin esto un congelado puede abrir y cerrar puertas a los demás mientras
     * espera, que es molestar y ademas le permite pasar de habitacion sin
     * moverse.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alInteractuar(org.bukkit.event.player.PlayerInteractEvent evento) {
        Player jugador = evento.getPlayer();
        if (!plugin.inmovilizado(jugador)) return;
        // Las herramientas de staff del propio staff no se cortan aqui: si
        // esta inmovilizado, es un jugador normal y no lleva ninguna.
        evento.setCancelled(true);
        evento.setUseInteractedBlock(Event.Result.DENY);
        evento.setUseItemInHand(Event.Result.DENY);
        avisarCongelado(jugador, "No puedes tocar nada.");
    }

    /** Ni usar /staffsay y cosas asi para pasar el rato: tampoco puede. */
    // (lo cubre alEscribirComando)

    // ---------------- Silencios ----------------

    /**
     * Un jugador silenciado no puede escribir en el chat.
     *
     * /staffmute guardaba el silencio en un archivo, pero NADA consultaba esa
     * lista: el jugador silenciado seguía hablando igual. Aqui es donde se
     * comprueba de verdad.
     *
     * El staff sí puede hablar por /staffchat, porque ese es otro comando y no
     * pasa por aqui.
     */
            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
      public void alEscribir(org.bukkit.event.player.AsyncPlayerChatEvent evento) {
          final Player jugador = evento.getPlayer();
          final String texto = evento.getMessage();

          // El espiado va PRIMERO, antes de mirar silencios ni antid-spam: si
          // el staff esta vigilando a alguien, quiere ver exactamente lo que
          // ha dicho, tambien lo que el antid-spam va a cortar.
          reenviarALosEspias(jugador, texto);

          // Se anota antes de nada. Asi el mensaje queda guardado aunque luego se
          // corte por un silencio o por el antid-spam, que es justo lo que hay
          // que poder revisar despues.
          if (!plugin.esStaff(jugador)) plugin.chatJugadores().anotar(jugador.getName(), texto);
          plugin.actividad().anotar(jugador, "chat", corta(texto, 60));

          if (plugin.esStaff(jugador)) return;

          if (plugin.config().getBoolean("chat-control.muted", false)) {
              evento.setCancelled(true);
              Bukkit.getScheduler().runTask(plugin, () -> {
                  if (jugador.isOnline()) jugador.sendMessage(Text.error("El chat público está pausado por el staff."));
              });
              return;
          }

          MuteStore.Registro registroSilencio = plugin.silencios().registro(jugador.getUniqueId());
          boolean silenciado = plugin.silencios().silenciado(jugador.getUniqueId());
          if (!silenciado) {
              String ip = plugin.ipDe(jugador);
              if ((ip == null || ip.isBlank()) && jugador.getAddress() != null) {
                  ip = jugador.getAddress().getAddress().getHostAddress();
              }
              if (plugin.silencios().silenciadoIp(ip)) {
                  silenciado = true;
                  registroSilencio = plugin.silencios().registroIp(ip);
              }
          }
          if (silenciado) {
              evento.setCancelled(true);
              MuteStore.Registro r = registroSilencio;
              String motivo = (r != null && !r.motivo.isEmpty()) ? r.motivo : "sin motivo indicado";
              // El aviso va al hilo principal: este evento salta en el hilo del
              // chat y enviar un mensaje a un jugador desde ahi no es seguro.
              Bukkit.getScheduler().runTask(plugin, () -> {
                  if (r != null && r.fantasma && plugin.config().getBoolean("ghost-mute.enabled", true)) {
                      String prefijo = ChatColor.translateAlternateColorCodes('&',
                              plugin.config().getString("ghost-mute.staff-prefix", "&8[&cGhost&8] "));
                      String sombra = prefijo + ChatColor.GRAY + jugador.getName() + ChatColor.WHITE + ": " + texto;
                      for (Player staff : Bukkit.getOnlinePlayers()) {
                          if (plugin.esStaff(staff)) staff.sendMessage(sombra);
                      }
                      if (plugin.config().getBoolean("ghost-mute.echo-to-player", true) && jugador.isOnline()) {
                          jugador.sendMessage(ChatColor.GRAY + "<" + jugador.getName() + "> " + texto);
                      }
                  } else if (jugador.isOnline()) {
                      jugador.sendMessage(Text.error("Estas silenciado. Motivo: "
                              + ChatColor.GRAY + motivo));
                  }
              });
              return;
          }

          // Lo de abajo escribe en disco (el silencio del antid-spam) y recorre
          // a los jugadores conectados para avisarlos. Este evento salta en el
          // hilo del chat, que NO es el principal, asi que aqui solo se decide si
          // el mensaje se corta y el resto se hace en el hilo principal.
          if (!antispam(jugador, texto)) return;
          evento.setCancelled(true);
          Bukkit.getScheduler().runTask(plugin, () -> {
              if (!jugador.isOnline()) return;
              aplicarAntispam(jugador);
          });
      }

    /**
     * Lo que dice un jugador le llega a quien lo esta espiando.
     *
     * Se manda aqui y no en el panel para que no se pierda aunque el staff tenga
     * el menu cerrado: espiar es mirar, y mirar no obliga a tener un cofre
     * abierto delante. El prefijo va en rojo para que en un servidor con mucho
     * chat no se confunda con un mensaje normal.
     *
     * Se encola al hilo principal porque enviar un mensaje a otro jugador desde
     * el hilo del chat no es seguro.
     */
    private void reenviarALosEspias(Player jugador, String texto) {
        java.util.List<java.util.UUID> espias = plugin.espia().espiasDe(jugador.getUniqueId());
        if (espias.isEmpty()) return;
        String recorte = corta(texto, 120);
        for (java.util.UUID id : espias) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player staff = Bukkit.getPlayer(id);
                if (staff == null || !staff.isOnline()) return;
                staff.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "[espía] "
                        + ChatColor.GRAY + jugador.getName() + ChatColor.WHITE + ": " + recorte);
            });
        }
    }

    /**
     * Lo que hace el antid-spam cuando alguien se pasa de los limites.
       *
       * Va aparte porque toca la API de Bukkit y el disco, y no se puede llamar
       * desde el hilo del chat.
       */
      private void aplicarAntispam(Player jugador) {
          long ms = plugin.leerTiempoEnMs(plugin.config().getString("antispam.silencio", "5m"));
          if (ms <= 0) ms = 5 * 60_000L;
          long ahora = System.currentTimeMillis();
          plugin.silencios().poner(jugador.getUniqueId(), jugador.getName(),
                  "Spam automatico: mensaje repetido", ahora + ms);
          plugin.silencios().guardar();
          plugin.historial().anotar("silencio", jugador.getName(), "", "antid-spam",
                  "spam automatico", "silencio de " + SancionStore.enLetras(ms));

          jugador.sendMessage(Text.error("Te han silenciado "
                  + SancionStore.enLetras(ms) + " por spam."));
          plugin.avisarEquipo(ChatColor.RED + jugador.getName() + ChatColor.YELLOW
                  + " silenciado " + SancionStore.enLetras(ms) + " por spam automatico");
          plugin.anunciarSancion("mute", jugador.getName(), "Auto-Mod", "Spam", SancionStore.enLetras(ms));
          // Se olvida la ventana para que no le salte otro silencio al mismo.
          plugin.chatJugadores().olvidar(jugador.getName());
      }

      /**
       * El antid-spam: decide si este mensaje es spam.
       *
       * Tres cosas que cuentan como spam: el mismo mensaje repetido, muchos
       * mensajes seguidos aunque sean distintos, y escribir a gritos en
       * mayusculas.
       *
       * @return true si este mensaje se ha bloqueado
       */
      private boolean antispam(Player jugador, String texto) {
          if (!plugin.config().getBoolean("antispam.activo", true)) return false;

          String clave = texto.trim().toLowerCase(java.util.Locale.ROOT);
          // Un "a" de prueba al abrir el chat no es spam de nadie.
          if (clave.length() < 2) return false;

          int repeticiones = plugin.config().getInt("antispam.mismo-mensaje", 3);
          int mayusculas = plugin.config().getInt("antispam.mayusculas", 6);
          int rafagaMax = Math.max(2, plugin.config().getInt("antispam.rafaga-mensajes", 5));
          int rafagaSeg = Math.max(1, plugin.config().getInt("antispam.rafaga-segundos", 5));

          java.util.List<String> ultimos = plugin.chatJugadores().ultimos(jugador.getName());
          if (ultimos.isEmpty()) return false;

          // Repeticion SEGUIDA: cuenta los mensajes iguales desde el ultimo hacia
          // atras, no cuantos "hola" sueltos hay en toda la ventana. Antes un
          // "hola" cada cinco mensajes tambien contaba como spam.
          int igualesSeguidos = 0;
          for (int i = ultimos.size() - 1; i >= 0; i--) {
              if (!ultimos.get(i).equals(clave)) break;
              igualesSeguidos++;
          }
          int soloMayusculas = 0;
          int letras = 0;
          for (char c : texto.toCharArray()) {
              if (!Character.isLetter(c)) continue;
              letras++;
              if (Character.isUpperCase(c)) soloMayusculas++;
          }
          boolean gritando = letras >= 4 && soloMayusculas >= mayusculas;

          // La rafaga se mide por TIEMPO, no por cuantos mensajes hay guardados.
          // Antes la condicion era "ventana llena" y, como la ventana nunca se
          // vacia, a partir del mensaje numero "ventana-mensajes" todo el mundo
          // salia silenciado por spam para siempre.
          int seguidos = plugin.chatJugadores().enRafaga(jugador.getName(), rafagaSeg);

          return igualesSeguidos >= repeticiones
                  || seguidos >= rafagaMax
                  || (gritando && ultimos.size() >= 4);
      }

    // ---------------- Congelado: bloquea el movimiento ----------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void alMover(PlayerMoveEvent evento) {
        if (!gestor().congelado(evento.getPlayer())) return;
        if (evento.getTo() == null) return;
        boolean mismoSitio = evento.getFrom().getBlockX() == evento.getTo().getBlockX()
                && evento.getFrom().getBlockY() == evento.getTo().getBlockY()
                && evento.getFrom().getBlockZ() == evento.getTo().getBlockZ();
        if (mismoSitio) return;
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void alCambiarVuelo(PlayerToggleFlightEvent evento) {
        if (!gestor().congelado(evento.getPlayer())) return;
        evento.setCancelled(true);
    }
}
