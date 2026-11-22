package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sancion de espera: el jugador se queda congelado, a oscuras y con un titulo
 * en pantalla hasta que se le pase el tiempo.
 *
 * Lo que la hace distinta a un mute o a un ban es que EL RELOJ SOLO CORRE
 * MIENTRAS EL JUGADOR ESTA CONECTADO. Si se va, se para; el tiempo que le
 * quedaba se guarda y al volver a entrar se le devuelve al mismo sitio y sigue
 * contando desde ahi.
 *
 * Eso es a proposito: es una forma de "castigo en linea" en la que el jugador
 * tiene que estar presente. Un ban normal no necesita que el sancionado este
 * delante, y por eso el reloj de este sistema se para en cuanto se rompe la
 * conexion.
 */
public final class SancionStore {

    /** Una sancion activa. */
    public static final class Registro {
        public UUID uuid;
        public String nombre = "";
        public long total;         // ms que dura en total
        public long restante;      // ms que le quedan (se para al desconectarse)
        public String motivo = "";
        public String staff = "";
        public String mundo = "";
        public double x, y, z, yaw, pitch;
        public long puesto;
        /** Si el staff eligio congelar o no. Si no, el jugador puede moverse
         * pero sigue sin poder usar comandos ni abrir cofres. */
        public boolean congeladoSi = true;
        /**
         * false = reloj PAUSADO: al salir se para, y al volver queda igual.
         * true  = reloj REAL: sigue corriendo aunque no este dentro, y hasta que
         *         se cumple se le mantiene baneado para que no pueda esquivarlo
         *         simplemente desconectandose.
         */
        public boolean relojReal;
        /** Cuando se cumple, en milisegundos. 0 si el reloj esta pausado. */
        public long hasta;
        /**
         * El casco que llevaba antes de que el congelado de la sancion le
         * pusiera el hielo, ya serializado.
         *
         * La sancion es la unica que manda cuando esta activa, asi que
         * FreezeStore no guarda nada para un sancionado y el casco previo
         * (que vive en el Estado, en memoria) se perdia al desconectarse: al
         * volver, congelar() veia hielo en la cabeza, no guardaba casco previo
         * y al cumplir la sancion se le ponia un hueco. Se guarda aqui para que
         * sobreviva a la desconexion.
         */
        public String casco = "";

        public Location sitio() {
            org.bukkit.World w = Bukkit.getWorld(mundo);
            if (w == null) return null;
            return new Location(w, x, y, z, (float) yaw, (float) pitch);
        }
    }

    private final OdisseyStaff plugin;
    private final Almacen almacen;
    private final Map<UUID, Registro> registros = new HashMap<>();

    public SancionStore(OdisseyStaff plugin) {
        this.plugin = plugin;
        this.almacen = plugin.almacen("sanciones", "sanciones.yml");
    }


    public boolean tiene(UUID uuid) {
        return registros.containsKey(uuid);
    }

    public Registro obtener(UUID uuid) {
        return registros.get(uuid);
    }

    public List<Registro> todas() {
        return new ArrayList<>(registros.values());
    }

    /** Pone una sancion nueva, sustituyendo la que hubiera. */
    /**
     * Pone una sancion y deja el reloj como estaba.
     *
     * Para elegir el modo de reloj hay que llamar antes a fijarReloj(), o usar
     * ponerReal(), que hace las dos cosas.
     */
    public Registro poner(Player jugador, long ms, String motivo, String staff) {
        Registro r = new Registro();
        r.uuid = jugador.getUniqueId();
        r.nombre = jugador.getName();
        r.total = ms;
        r.restante = ms;
        r.motivo = motivo == null ? "" : motivo;
        r.staff = staff == null ? "" : staff;
        r.puesto = System.currentTimeMillis();
        copiarSitio(r, jugador.getLocation());
        registros.put(r.uuid, r);
        guardar();
        return r;
    }

    private void copiarSitio(Registro r, Location l) {
        if (l == null || l.getWorld() == null) return;
        r.mundo = l.getWorld().getName();
        r.x = l.getX();
        r.y = l.getY();
        r.z = l.getZ();
        r.yaw = l.getYaw();
        r.pitch = l.getPitch();
    }

        /**
         * Levanta la sancion.
         *
         * Tambien levanta el ban temporal si la sancion iba con reloj real y el
         * ban era nuestro. Sin esto, /staffsancion Rob off le quitaba la
         * sancion pero le dejaba el baneo y se podia terminar sin poder entrar.
         */
        public Registro quitar(UUID uuid) {
            Registro r = registros.remove(uuid);
            if (r != null) {
                quitarBan(r.uuid, r.nombre);
                guardar();
            }
            return r;
        }

    /**
     * Comandos que un sancionado SI puede usar.
     *
     * Se separan porque bloquearlos a todos dejaria a alguien encerrado sin
     * salida en dos casos:
     *
     *  - La autenticacion. Si el servidor tiene un plugin de registro, el
     *    jugador tiene que poder escribir su contraseña o se queda dentro del
     *    servidor sin poder hacer absolutamente nada.
     *  - El staff. Si a un admin le meten una sanción por error, tiene que
     *    poder usar /staffsancion <nombre> off para salir. Por eso el staff
     *    pasa entero y esta lista es solo para el resto.
     */
    private static final java.util.Set<String> PERMITIDAS = new java.util.HashSet<>(
            java.util.Arrays.asList("login", "l", "register", "reg", "changepassword", "changepass", "redemption"));

    public static boolean comandoPermitido(String comando) {
        return PERMITIDAS.contains(comando.toLowerCase(java.util.Locale.ROOT));
    }

    /** Si este jugador no puede tocar nada porque tiene la sancion activa. */
    public boolean bloqueado(Player jugador) {
        return tiene(jugador.getUniqueId());
    }

    /**
 * Vuelve a ponerles el hielo a los congelados que se lo hayan quitado.
 *
 * Pasa porque el bloque de la cabeza es un item normal: se puede caer al suelo
 * al morir, y si hay un inventario abierto (un cofre que no se pudo abrir, la
 * ventana de otro menu) se puede mover. Sin esto, un congelado con 10 segundos
 * de vista se queda sin el hielo y el resto del staff ve a un jugador normal.
 *
 * Solo mira a los congelados de verdad: en un servidor normal no hay ninguno y
 * el bucle no hace nada.
 */
private void reaplicarHieloALosConectados() {
    if (plugin.gestor().congelados().isEmpty()) return;
    for (Player p : Bukkit.getOnlinePlayers()) {
        if (!plugin.gestor().congelado(p)) continue;
        plugin.gestor().reaplicarHielo(p);
    }
}

/**
 * Cuanto le queda de verdad, segun el modo de reloj.
     *
     * Con reloj real el "restante" guardado ya no vale: se recalcula con la
     * hora de fin, que es lo unico que no depende de que el jugador este
     * conectado o no.
     */
    public long restanteReal(Registro r) {
        if (r == null || !r.relojReal || r.hasta <= 0) return r == null ? 0 : r.restante;
        return Math.max(0, r.hasta - System.currentTimeMillis());
    }

    /** Reduce el tiempo de una sanción activa desde una redención válida. */
    public long reducir(UUID uuid, long milisegundos) {
        Registro r = registros.get(uuid);
        if (r == null || milisegundos <= 0) return 0;
        long restante = Math.max(0, restanteReal(r));
        long nuevo = Math.max(0, restante - milisegundos);
        long reducido = restante - nuevo;
        if (r.relojReal) {
            r.restante = nuevo;
            r.hasta = System.currentTimeMillis() + nuevo;
        } else {
            r.restante = nuevo;
        }
        guardar();
        Player jugador = Bukkit.getPlayer(uuid);
        if (jugador != null && nuevo > 0) mostrar(jugador, r);
        return reducido;
    }

    /**
     * Pone la sancion con reloj real: corre aunque el jugador salga, y hasta que
     * se cumple no vuelve a poder entrar.
     *
     * Aqui NO se crea todavia el ban temporal: el jugador sigue dentro
     * atendiendola, y banearle en ese momento solo podria terminar en un kick
     * que le mandaria a esperar fuera de la partida entera. El ban se pone
     * cuando de verdad se va, en alSalir(), y ademas se renueva ahi.
     *
     * El ban es lo que cierra el agujero. Sin el, un sancionado de 30m
     * desconecta y no vuelve nunca: el reloj pausado nunca llega a cero y la
     * sancion se queda pendiente indefinidamente. Con el, si sale ya no puede
     * volver hasta cumplirla y el tiempo se le acaba igual.
     */
    public Registro ponerReal(Player jugador, long ms, String motivo, String staff) {
        Registro r = poner(jugador, ms, motivo, staff);
        r.relojReal = true;
        r.hasta = System.currentTimeMillis() + ms;
        guardar();   // poner() ya habia guardado, pero sin esto el reloj real no llegaba al disco
        return r;
    }

    /**
     * Cambia el modo de reloj de una sancion ya puesta.
     *
     * Al pasar a pausa se levanta el ban por si quedara alguno puesto; al
     * pasar a real el ban no se pone aqui, sino cuando el jugador se vaya.
     */
    public void cambiarReloj(Registro r, boolean real) {
        if (real) {
            r.relojReal = true;
            r.hasta = System.currentTimeMillis() + Math.max(1000L, r.restante);
        } else {
            // Al pasar a pausa hay que quedarse con lo que le queda DE VERDAD, no
            // con lo que le quedaba cuando se puso. Antes no se tocaba
            // r.restante, asi que de una sancion de 30 minutos a la que ya le
            // habian pasado 20, el menu le regalaba los 30 enteros.
            r.restante = Math.max(1000L, restanteReal(r));
            r.relojReal = false;
            r.hasta = 0;
            quitarBan(r.uuid, r.nombre);
        }
        guardar();   // sin esto, cambiar el modo desde el menu no llegaba al archivo
    }

    /**
     * Ban temporal del propio plugin, que Paper levanta solo al cumplirse.
     *
     * Se usa la lista de profiles y no la de nombres: con el tipo NAME los
     * sobrecargar de addBan y getBanEntry quedan ambiguos (T es String igual
     * que el primer parametro) y el javac no deja compilar. Con profiles no
     * hay esa colision, y ademas el perfil lleva el UUID, asi que el ban no
     * depende de que el jugador cambie de nombre para ser el mismo.
     */
    private void banearHasta(UUID uuid, String nombre, long hasta, String motivo) {
        if (nombre == null || nombre.isEmpty()) return;
        org.bukkit.BanList<org.bukkit.profile.PlayerProfile> lista =
                plugin.getServer().getBanList(org.bukkit.BanList.Type.PROFILE);
        org.bukkit.profile.PlayerProfile perfil = perfil(uuid, nombre);
        lista.addBan(perfil, MARCA + (motivo == null ? "" : motivo),
                new java.util.Date(hasta), "OdisseyStaff");
        plugin.getLogger().info("[sanciones] ban temporal de " + nombre + " hasta "
                + ChatJugadoresLog.fecha(hasta) + " (sancion con reloj real).");
    }

    /**
     * Levanta el ban temporal SOLO si es nuestro.
     *
     * Si el baneo lo puso otro plugin, o lo puso un admin a mano, no se toca.
     * Por eso el ban lleva una marca al principio del motivo: sin ella no hay
     * forma de saber de quien es.
     */
    public void quitarBan(UUID uuid, String nombre) {
        if (nombre == null || nombre.isEmpty()) return;
        org.bukkit.BanList<org.bukkit.profile.PlayerProfile> lista =
                plugin.getServer().getBanList(org.bukkit.BanList.Type.PROFILE);
        org.bukkit.BanEntry<org.bukkit.profile.PlayerProfile> ban =
                lista.getBanEntry(perfil(uuid, nombre));
        if (ban == null) return;
        if (ban.getReason() == null || !ban.getReason().startsWith(MARCA)) return;
        // remove() y no pardon(nombre): con el tipo PROFILE la sobrecarga de
        // pardon tambien queda ambigua, y ademas remove() actua directamente
        // sobre la entrada que ya tenemos comprobada.
        ban.remove();
        plugin.getLogger().info("[sanciones] ban temporal de " + nombre + " levantado.");
    }

    /** El perfil de un jugador por nombre; con UUID si lo sabemos. */
    private org.bukkit.profile.PlayerProfile perfil(UUID uuid, String nombre) {
        return uuid == null
                ? plugin.getServer().createProfile(nombre)
                : plugin.getServer().createProfile(uuid, nombre);
    }

    /** La marca que deja el plugin en sus baneos temporales. */
    private static final String MARCA = "[OdisseyStaff] ";

    // ---------- El reloj ----------

    /**
     * Resta un segundo a quien tenga sancion y este conectado.
     *
     * Se recorre SOLO la lista de conectados: por eso el reloj se para solo
     * cuando alguien se va, sin ningun codigo extra.
     */
    public void tick() {
        // El hielo se revisa aqui y no en un reloj aparte: ya se recorre la
        // lista una vez por segundo y no tiene sentido pagar un segundo timer
        // para lo mismo. Se comprueba solo si hay alguien congelado, que es lo
        // que pasa en un servidor normal con el staff de turno.
        reaplicarHieloALosConectados();

        if (registros.isEmpty()) return;
        List<Registro> terminadas = new ArrayList<>();

        for (Registro r : new ArrayList<>(registros.values())) {
            // Con reloj real el restante se recalcula con la hora de fin, y se
            // revisan tambien las sanciones de quien NO esta dentro: su reloj
            // corre igual. Si solo se mirara la lista de conectados, la de un
            // sancionado desconectado se quedaria ahi indefinidamente.
            if (r.relojReal) {
                r.restante = restanteReal(r);
                if (r.restante <= 0) {
                    terminadas.add(r);
                    continue;
                }
            }

            Player p = Bukkit.getPlayer(r.uuid);
            if (p == null) continue;
            if (!r.relojReal) r.restante -= 1000L;
            if (r.restante <= 0) {
                terminadas.add(r);
                continue;
            }
            mostrar(p, r);
        }

        for (Registro r : terminadas) {
            registros.remove(r.uuid);
            Player p = Bukkit.getPlayer(r.uuid);
            if (p != null) {
                terminar(p, r);
            } else {
                // Se cumplio estando fuera: se levanta el ban y queda en el historial.
                quitarBan(r.uuid, r.nombre);
                if (!r.motivo.isEmpty()) {
                    plugin.historial().anotar("sancion", r.nombre, "", r.staff, r.motivo, "cumplida");
                    plugin.historial().guardar();
                }
                plugin.getLogger().info("[sanciones] la sancion de " + r.nombre
                        + " se cumplio estando desconectado.");
            }
        }
        if (!terminadas.isEmpty()) guardar();
    }

    /**
     * Cuando el jugador entra, si tenia sancion, se le devuelve a su sitio.
     *
     * Con reloj real primero se mira si ya se habia cumplido mientras no
     * estaba: si es asi, la sancion se borra y se le levanta el ban, sin
     * congelarle ni devolverle a ningun sitio.
     */
    public void alEntrar(Player jugador) {
        Registro r = registros.get(jugador.getUniqueId());
        if (r == null) return;

        if (r.relojReal) {
            r.restante = restanteReal(r);
            if (r.restante <= 0) {
                registros.remove(r.uuid);
                quitarBan(r.uuid, r.nombre);
                guardar();
                if (!r.motivo.isEmpty()) {
                    plugin.historial().anotar("sancion", r.nombre, "", r.staff, r.motivo, "cumplida");
                    plugin.historial().guardar();
                }
                jugador.sendMessage(Text.exito("Tu sanción ya se había cumplido."));
                return;
            }
        }

        Location sitio = r.sitio();
        if (sitio != null) {
            // Al mismo sitio donde se le sano: si se va y vuelve, no puede
            // reaparecer en su base y salir andando.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!jugador.isOnline()) return;
                plugin.gestor().teletransportar(jugador, sitio);
                jugador.setFallDistance(0f);
            });
        }
        congelar(jugador, r);
        // El aviso va igual aunque no este congelado: la sancion sigue en pie
        // y el jugador tiene que saber cuanto le queda.
        jugador.sendMessage(Text.aviso("Tu sanción sigue vigente: te quedan "
                + legible(r.restante) + "."));
    }

    /**
     * El jugador se va: se guarda donde estaba.
     *
     * El reloj solo se para si esta en modo pausado. Con reloj real sigue
     * corriendo, y ademas se le renueva el ban temporal para que no pueda
     * volver a entrar hasta haber cumplido.
     */
    public void alSalir(Player jugador) {
        Registro r = registros.get(jugador.getUniqueId());
        if (r == null) return;
        copiarSitio(r, jugador.getLocation());
        // Se guarda el casco que tenia antes del hielo ANTES de que
        // salirForzoso() se lleve el Estado, que es donde vive. Si no, al
        // reconectar veia el hielo puesto, no sabia que debajo habia un casco y
        // al cumplirse la sancion se le ponia un hueco en la cabeza.
        StaffManager.Estado e = plugin.gestor().estadoONull(jugador);
        if (e != null && e.helmetPrevio != null) {
            r.casco = FreezeStore.serializar(e.helmetPrevio);
        }
        if (r.relojReal) {
            r.restante = restanteReal(r);
            banearHasta(r.uuid, r.nombre, r.hasta, r.motivo);
        }
        guardar();
        plugin.getLogger().info("[sanciones] " + r.nombre + " se ha desconectado. Quedan "
                + legible(r.restante) + (r.relojReal
                ? " (el reloj sigue corriendo y no podra volver hasta cumplirla)."
                : " (el reloj se para hasta que vuelva)."));
    }

    public void congelar(Player jugador, Registro r) {
        if (!r.congeladoSi) {
            // El staff eligió "congelar: no". Se le deja andar y hablar, pero el
            // reloj sigue corriendo y los comandos siguen bloqueados.
            plugin.gestor().descongelar(jugador);
            jugador.removePotionEffect(PotionEffectType.DARKNESS);
            return;
        }
        // Devuelve a memoria el casco guardado en el archivo para que
        // quitarHielo() lo restaure al cumplirse la sancion. Se pone ANTES de
        // congelar(): congelar() guarda como "casco previo" lo que ve en la
        // cabeza si no hay nada en memoria, y al volver de una desconexion eso
        // es el hielo, no el casco real.
        if (r.casco != null && !r.casco.isEmpty()) {
            StaffManager.Estado e = plugin.gestor().estado(jugador);
            if (e != null && e.helmetPrevio == null) {
                e.helmetPrevio = FreezeStore.deserializar(r.casco);
            }
        }
        plugin.gestor().congelar(jugador, r.motivo, r.staff.isEmpty() ? "sancion" : r.staff);
        jugador.removePotionEffect(PotionEffectType.DARKNESS);
        jugador.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                PotionEffect.INFINITE_DURATION, 255, false, false));
    }

    private void terminar(Player jugador, Registro r) {
        plugin.gestor().descongelar(jugador);
        jugador.removePotionEffect(PotionEffectType.DARKNESS);
        jugador.sendTitle(null, null, 0, 0, 0);
        jugador.sendMessage(Text.exito("Tu sanción ha terminado. Ya puedes moverte."));
        plugin.getLogger().info("[sanciones] se cumplio la sancion de " + r.nombre + ".");
        // Por si quedara algun ban temporal nuestro encima, aqui se levanta.
        quitarBan(r.uuid, r.nombre);
        if (!r.motivo.isEmpty()) {
            plugin.historial().anotar("sancion", r.nombre, "", r.staff, r.motivo, "cumplida");
            plugin.historial().guardar();
        }
    }

    /**
     * Pinta el titulo: una linea de titulo y una de subtitulo, sin mas.
     *
     * Antes se metian saltos de linea con "\n" para poner el tiempo en una linea
     * y el motivo en otra, y en pantalla NO aparecian: solo se veia "Estas
     * sancionado" y "Permanece conectado para salir de la sancion". El texto
     * heredado que usa Bukkit no convierte el "\n" en salto de linea dentro del
     * titulo, asi que se perdia lo que venia despues.
     *
     * Ahora todo va en una sola linea de subtitulo, que es lo que si funciona:
     *
     *   Estás sancionado
     *   Motivo: Making spam    T. Restante: 1 minuto 59 segundos
     *
     * El motivo se recorta: si el staff escribe un motivo largo, el subtitulo se
     * sale de la pantalla y ya no se lee ni el tiempo, que es lo importante.
     */
        public void mostrar(Player jugador, Registro r) {
            // Siempre el restante real: con reloj pausado el guardado va bien,
            // pero si el reloj es real hay que recalcularlo o el titulo
            // mostraria el tiempo congelado del ultimo tick guardado.
            long restante = restanteReal(r);
            String titulo = ChatColor.RED + "" + ChatColor.BOLD + "Estás sancionado";
            String subtitulo = ChatColor.GRAY + "Motivo: " + ChatColor.WHITE + corto(r.motivo)
                    + ChatColor.DARK_GRAY + "   T. Restante: "
                    + ChatColor.YELLOW + ChatColor.BOLD + enLetras(restante)
                    + (r.relojReal ? ChatColor.DARK_GRAY + "   (reloj real)" : "");
            // Sin fundidos y con un stay enorme: el titulo se queda fijo y solo
            // cambia el texto, en vez de parpadear una vez por segundo.
            jugador.sendTitle(titulo, subtitulo, 0, 20 * 60 * 20, 0);
        }

    /** Recorta el motivo para que el subtitulo quepa en pantalla. */
    public static String corto(String motivo) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.isEmpty()) return "sin motivo";
        if (m.length() <= 24) return m;
        return m.substring(0, 23) + "…";
    }

    /**
     * El tiempo en palabras: "2 minutos", "1 minuto 59 segundos", "30 segundos".
     *
     * Se escribe asi a proposito. Con "1:59" hay que descifrar que son minutos y
     * que segundos; escrito se lee de un vistazo, que es justo lo que necesita
     * alguien que esta congelado mirando la pantalla.
     */
    public static String enLetras(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;

        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append(h == 1 ? " hora" : " horas");
            if (m > 0 || s > 0) sb.append(' ');
        }
        if (m > 0) {
            sb.append(m).append(m == 1 ? " minuto" : " minutos");
            if (s > 0) sb.append(' ');
        }
        // Los segundos solo se callan cuando hay algo mas antes. Si es el unico
        // numero, se pone aunque sea cero ("0 segundos"), para que nunca quede
        // un titulo en blanco.
        if (s > 0 || sb.length() == 0) {
            sb.append(s).append(s == 1 ? " segundo" : " segundos");
        }
        return sb.toString();
    }

    /** 14:32 o 2h 05m 12s, para que se entienda de un vistazo. */
    public static String legible(long ms) {
        long s = Math.max(0, ms / 1000L);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long seg = s % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, seg);
        return String.format("%02d:%02d", m, seg);
    }

    // ---------- Disco ----------

    public void cargar() {
        YamlConfiguration y = almacen.leer();
        registros.clear();
        org.bukkit.configuration.ConfigurationSection s = y.getConfigurationSection("sanciones");
        if (s == null) return;
        for (String clave : s.getKeys(false)) {
            String p = "sanciones." + clave + ".";
            try {
                Registro r = new Registro();
                r.uuid = UUID.fromString(clave);
                r.nombre = y.getString(p + "nombre", "");
                r.total = y.getLong(p + "total", 0);
                r.restante = y.getLong(p + "restante", 0);
                r.motivo = y.getString(p + "motivo", "");
                r.staff = y.getString(p + "staff", "");
                r.mundo = y.getString(p + "mundo", "");
                r.x = y.getDouble(p + "x", 0);
                r.y = y.getDouble(p + "y", 0);
                r.z = y.getDouble(p + "z", 0);
                r.yaw = y.getDouble(p + "yaw", 0);
                r.pitch = y.getDouble(p + "pitch", 0);
                r.puesto = y.getLong(p + "puesto", 0);
                r.congeladoSi = y.getBoolean(p + "congeladoSi", true);
                r.relojReal = y.getBoolean(p + "relojReal", false);
                r.hasta = y.getLong(p + "hasta", 0L);
                r.casco = y.getString(p + "casco", "");
                if (r.restante > 0) registros.put(r.uuid, r);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[sanciones] entrada invalida ignorada: " + clave);
            }
        }
        if (!registros.isEmpty()) {
            plugin.getLogger().info("[sanciones] " + registros.size() + " sancion(es) en curso cargadas.");
        }
    }

    public void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Registro r : registros.values()) {
            String p = "sanciones." + r.uuid + ".";
            y.set(p + "nombre", r.nombre);
            y.set(p + "total", r.total);
            y.set(p + "restante", r.restante);
            y.set(p + "motivo", r.motivo);
            y.set(p + "staff", r.staff);
            y.set(p + "mundo", r.mundo);
            y.set(p + "x", r.x);
            y.set(p + "y", r.y);
            y.set(p + "z", r.z);
            y.set(p + "yaw", r.yaw);
            y.set(p + "pitch", r.pitch);
            y.set(p + "puesto", r.puesto);
            y.set(p + "congeladoSi", r.congeladoSi);
            y.set(p + "relojReal", r.relojReal);
            y.set(p + "hasta", r.hasta);
            y.set(p + "casco", r.casco == null ? "" : r.casco);
        }
        almacen.escribir(y);
    }
}
