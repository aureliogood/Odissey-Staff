package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OdisseyStaff - herramientas de staff para el servidor de Odissey.
 *
 * Modo staff, vanish, congelar, sanciones, teletransporte y las cinco
 * herramientas que reparte /staff por la barra rapida.
 *
 * Este plugin NO lleva nada de la web. El acceso al panel de administracion se
 * decide en el config.json del bot (la lista webAdmins): asi, para dar de
 * admin a alguien se edita un fichero y no hace falta tocar el servidor de
 * Minecraft. Antes el plugin firmaba tokens contra la web, lo que obligaba a
 * tener un secreto compartido en los dos lados y una clave por jugador.
 */
public final class OdisseyStaff extends JavaPlugin {

    private StaffManager staff;
  private StaffItems items;
    private MuteStore silencios;
    private SanctionStore historial;
    private StaffKit kit;
    private MenuTeleporte menuTeleporte;
    private SancionStore sanciones;
    private NotaStore notas;
    private VigilanciaStore vigilancia;
    private StaffChatLog chatLog;
    private FreezeStore congelados;
    private ConexionStore conexiones;
    private BloqueoStore bloqueos;
    private ActividadStore actividad;
    private MenuSancion menuSancion;
    private MenuJugador menuJugadores;
    private ChatJugadoresLog chatJugadores;
    private StaffSayCommand comandoAvisos;
    /** Los almacenes: uno por tienda, con la base de datos por debajo si hay. */
    private AlmacenSeguro.Fabrica almacen;
    private ConexionBd conexionBd;

    /** Los menus de /staff. */
    private Sonidos sonidos;
    private MenuStaff menuStaff;
    private MenuEspiar menuEspiar;
    private MenuSanciones menuSanciones;
    private MenuCopiarSkin menuCopiar;
    private WarnStore warns;
    private ReportStore reportes;
    private EvidenceStore evidencias;
    private RedemptionStore redencion;
    private ModerationWebhook notificacionesExternas;
    private MenuHistorial menuHistorial;
    private MenuBanear menuBanear;
    private EspiaStore espia;
    private EsperaStore espera;
    private BanDialogManager banDialogos;
    private WebBanSync webBans;


    /** IP de la ultima conexion de cada jugador, para /staffinfo. */
    private final java.util.Map<java.util.UUID, String> ips = new java.util.concurrent.ConcurrentHashMap<>();

    public void anotarIp(java.util.UUID uuid, String ip) {
        if (ip != null && !ip.isEmpty()) ips.put(uuid, ip);
    }

    /**
     * Olvida la IP de un jugador al salir.
     *
     * Este mapa solo sirve para los que estan dentro (/staffinfo, el aviso de
     * entrada, la ficha del menu): sin esto guardaba una entrada por cada UUID
     * que hubiera entrado en toda la vida del servidor y crecia sin parar. La
     * IP historica para /staffalt sale de ConexionStore, que si se guarda.
     */
    public void olvidarIp(java.util.UUID uuid) {
        if (uuid != null) ips.remove(uuid);
    }

    /**
     * La IP con la que entro por ultima vez ese jugador, o "" si no se sabe.
     *
     * Antes devolvia null cuando no habia IP anotada, y tres sitios la
     * trataban como cadena: con el servidor detras de un proxy, o con
     * online-mode desactivado, getAddress() puede venir sin resolver y la IP
     * no se anotaba. Ahi, AvisosEntrada reventaba con NullPointerException DENTRO
     * del PlayerJoinEvent, y como el aviso va antes de reaplicar la sancion, el
     * congelado y el kit, un solo fallo dejaba al sancionado sin cumplir nada.
     */
    public String ipDe(org.bukkit.entity.Player jugador) {
        if (jugador == null) return "";
        String ip = ips.get(jugador.getUniqueId());
        return ip == null ? "" : ip;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        asegurarDefaultsConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Text.cargar(config().getString("mensajes.prefijo", Text.PREFIJO_POR_DEFECTO));
        iniciarAlmacen();
        this.sonidos = new Sonidos(this);
        this.notificacionesExternas = new ModerationWebhook(this);
        this.staff = new StaffManager(this);
        this.items = new StaffItems(this);
        this.silencios = new MuteStore(this);
        this.silencios.cargar();
        this.historial = new SanctionStore(this);
        this.historial.cargar();
        this.kit = new StaffKit(this, items);
        this.sanciones = new SancionStore(this);
        this.sanciones.cargar();
        this.notas = new NotaStore(this);
        this.notas.cargar();
        this.vigilancia = new VigilanciaStore(this);
        this.vigilancia.cargar();
        this.chatLog = new StaffChatLog(this);
        this.chatLog.cargar();
        this.chatJugadores = new ChatJugadoresLog(this);
        this.chatJugadores.cargar();
        this.congelados = new FreezeStore(this);
        this.congelados.cargar();
        this.conexiones = new ConexionStore(this);
        this.conexiones.cargar();
        this.bloqueos = new BloqueoStore(this);
        this.bloqueos.cargar();
        this.actividad = new ActividadStore(this);
        this.actividad.cargar();
        // Antes que el StaffListener a proposito: los dos escuchan el chat en
        // LOWEST, y si el StaffListener cancela el mensaje primero (mute, chat de
        // staff) este menu se quedaria sin leer el "30m" que le tocaba.
        this.menuSancion = new MenuSancion(this);
        this.menuSancion.registrar();
        this.menuJugadores = new MenuJugador(this);
        this.menuJugadores.registrar();
        // Los menus de /staff. Este va despues de los dos de arriba a proposito:
        // los tres escuchan el chat a LOWEST y, si este cogiera el mensaje
        // primero, se quedaria sin el texto que letocaba.
        this.espia = new EspiaStore(this);
        this.espera = new EsperaStore(this);
        this.espera.registrar();
        this.menuEspiar = new MenuEspiar(this);
        this.menuEspiar.registrar();
        this.menuSanciones = new MenuSanciones(this);
        this.menuSanciones.registrar();
        this.menuStaff = new MenuStaff(this);
        this.menuStaff.registrar();
        this.menuCopiar = new MenuCopiarSkin(this);
        this.menuCopiar.registrar();
        this.warns = new WarnStore(this);
        this.warns.cargar();
        this.warns.avisarSiEstaMalConfigurado();
        this.reportes = new ReportStore(this);
        this.reportes.cargar();
        this.evidencias = new EvidenceStore(this);
        this.evidencias.cargar();
        this.redencion = new RedemptionStore(this);
        this.redencion.cargar();
        this.menuHistorial = new MenuHistorial(this);
        this.menuHistorial.registrar();
        this.menuBanear = new MenuBanear(this);
        this.menuBanear.registrar();

        // El reloj de las sanciones va una vez por segundo. Recorre solo los
        // conectados, asi que se para solo cuando alguien se va.
        getServer().getScheduler().runTaskTimer(this, () -> sanciones.tick(), 20L, 20L);
        // El chat se escribe desde un hilo aparte, asi que no se guarda cada
        // mensaje: se guarda de minuto en minuto, y al apagar.
        getServer().getScheduler().runTaskTimer(this, () -> chatJugadores.guardar(), 1200L, 1200L);

        StaffListener listener = new StaffListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.setGestor(staff);
        listener.setItems(items);

        // El menu de teletransporte necesita su propio listener porque escucha
        // InventoryClickEvent, que no va en el listener de los items.
        this.menuTeleporte = new MenuTeleporte(this);
        this.menuTeleporte.registrar();
        listener.setMenu(menuTeleporte);

        // Cada comando se engancha a su nombre. SanctionCommand atiende a cuatro
        // comandos distintos, asi que se registra cuatro veces con el mismo
        // objeto: el comando decide que hacer mirando como se llama.
        SanctionCommand sanciones = new SanctionCommand(this);
        this.banDialogos = new BanDialogManager(this, sanciones);
        this.webBans = new WebBanSync(this);
        StaffUtilCommands utiles = new StaffUtilCommands(this);
        StaffSayCommand avisos = new StaffSayCommand(this);
        this.comandoAvisos = avisos;

        registrar("staff", new StaffCommand(this));
        registrar("staffitems", new StaffItemsCommand(this));
        registrar("staffespiar", new StaffEspiarCommand(this));
        registrar("vanish", new VanishCommand(this));
        registrar("staffchat", new StaffChatCommand(this));
        registrar("freeze", new FreezeCommand(this));
        registrar("staffwarn", sanciones);
        registrar("staffmute", sanciones);
        registrar("tempmute", sanciones);
        IpSanctionCommand sancionesIp = new IpSanctionCommand(this);
        registrar("ipban", sancionesIp);
        registrar("ipmute", sancionesIp);
        registrar("ghostmute", sanciones);
        ChatControlCommand controlChat = new ChatControlCommand(this);
        registrar("clearchat", controlChat);
        registrar("mutechat", controlChat);
        registrar("staffban", sanciones);
        registrar("tempban", sanciones);
        registrar("staffkick", sanciones);
        registrar("stafftools", new StaffToolsCommand(this));
        registrar("staffinfo", new StaffInfoCommand(this));
        registrar("staffunmute", utiles);
        registrar("unban", utiles);
        registrar("unwarn", utiles);
        registrar("checkban", utiles);
        registrar("checkmute", utiles);
        registrar("warnings", utiles);
        registrar("banlist", utiles);
        registrar("mutelist", utiles);
        ReportTicketCommand reportes = new ReportTicketCommand(this);
        registrar("report", reportes);
        registrar("ticket", reportes);
        registrar("evidence", new EvidenceCommand(this));
        registrar("redemption", new RedemptionCommand(this));
        registrar("lbr", new LbrCommand(this));
        registrar("ai", new AiModerationCommand(this));
        GeoIpCommand geoip = new GeoIpCommand(this);
        registrar("geoip", geoip);
        registrar("vpncheck", geoip);
        registrar("staffrollback", new CoreProtectRollbackCommand(this));
        registrar("staffdatawipe", new PlayerDataWipeCommand(this));
        registrar("staffhistory", utiles);
        registrar("stafffrozen", utiles);
        registrar("staffrespawn", utiles);
        registrar("staffspectate", utiles);
        registrar("staffnota", utiles);
        registrar("staffvigilar", utiles);
        registrar("staffchatlog", utiles);
        registrar("staffonline", utiles);
        registrar("staffbloquear", utiles);
        registrar("staffalt", utiles);
        registrar("network", utiles);
        registrar("staffinvsee", utiles);
        registrar("staffsnapshot", utiles);
        registrar("staffjaula", utiles);
        registrar("staffsay", avisos);
        registrar("staffactionbar", avisos);
        registrar("staffsancion", new StaffSancionCommand(this));
        registrar("staffcall", new StaffCallCommand(this));
        registrar("staffrestaurar", new StaffRestaurarCommand(this));
        registrar("history", new StaffMenuExtraCommands(this));
        registrar("banear", new StaffMenuExtraCommands(this));

        // El reloj del panel de espiado. Medio segundo es suficiente para ver
        // moverse a alguien y no desperdicia ni CPU ni ancho de banda: el menu
        // solo envia al cliente los items que han cambiado de verdad.
        getServer().getScheduler().runTaskTimer(this, () -> menuEspiar.tick(), 10L, 10L);


        for (Player p : getServer().getOnlinePlayers()) {
            items.quitar(p);
        }
getLogger().info("=========================================");
        getLogger().info(" OdisseyStaff v" + getDescription().getVersion() + " ACTIVADO");
        getLogger().info(" /staff abre el panel: herramientas, espiar, sanciones, celdas y registros.");
        getLogger().info(" /staffitems da o quita solo las 5 herramientas de la barra rapida.");
        getLogger().info(" Sanciones: /staffsancion abre el menu (motivo, jugador, tiempo, destino y congelar).");
        getLogger().info("            /staffsancion <jugador> off la levanta. /staffsancion lista las muestra.");
        getLogger().info("            El reloj de cada sancion corre solo mientras el sancionado esta conectado.");
        getLogger().info("=========================================");

        if (!config().getBoolean("usar-op-como-staff", true)) {
            getLogger().info("El acceso por op esta desactivado: usa permisos (odissey.staff).");
        }
    }

    @Override
    public void onDisable() {
        // Nadie se queda espiando con el servidor parado. El salirEspectador de
        // mas abajo ya devuelve al staff a su sitio y a su modo de juego.
        if (espia != null) espia.limpiarTodo();
        if (staff != null) {

            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                // El casco previo de un sancionado congelado vive en el Estado y
                // salirForzoso() se lo lleva: se guarda antes para que no se
                // pierda si el apagado no paso por PlayerQuitEvent.
                if (sanciones != null) sanciones.alSalir(p);
                staff.salirForzoso(p);
            }
        }
        if (silencios != null) silencios.guardar();
        if (historial != null) historial.guardar();
        if (comandoAvisos != null) comandoAvisos.apagarTodo();
        if (sanciones != null) sanciones.guardar();
        if (chatJugadores != null) chatJugadores.guardar();
        if (notas != null) notas.guardar();
        if (vigilancia != null) vigilancia.guardar();
        if (chatLog != null) chatLog.guardar();
        if (congelados != null) congelados.escribir();
        if (conexiones != null) conexiones.guardar();
        if (bloqueos != null) bloqueos.guardar();
        if (actividad != null) actividad.guardar();
        if (warns != null) warns.guardar();
        if (reportes != null) reportes.guardar();
        if (evidencias != null) evidencias.guardar();
        if (redencion != null) redencion.guardar();
        // Las herramientas se guardan ANTES de cerrar la conexión: con la base
        // de datos puesta, kit.guardar() escribe por el almacén y, cerrada ya la
        // conexión, reabría una nueva que se quedaba sin cerrar.
        if (kit != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (items != null) kit.guardar(p);
            }
        }
        // La conexión se cierra al final de todo: los stores de arriba escriben
        // todavia al apagar y la necesitan.
        if (conexionBd != null) conexionBd.cerrar();
        getLogger().info("Apagado.");
    }


    /**
     * Engancha un comando.
     *
     * El nombre se pasa A MANO, no se deduce del nombre de la clase. Antes se
     * hacia al reves y, cuando la clase no se llamaba como el comando (que es el
     * caso de SanctionCommand, que maneja cuatro comandos), se caia en un bucle
     * que asignaba el ejecutor al PRIMER comando que estuviera libre. El
     * resultado era que /staffban, /staffkick y /stafftp se quedaban sin
     * ejecutor (no hacian nada) y TeleportCommand se colaba en /staffmute, que
     * teletransportaba en vez de silenciar.
     *
     * Con el nombre explicito no hay nada que adivinar.
     */
    private void registrar(String nombreComando, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = getCommand(nombreComando);
        if (cmd == null) {
            getLogger().warning("El comando /" + nombreComando + " esta en plugin.yml pero no se ha podido registrar.");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(new StaffTabCompleter(this, executor));
    }

    public FileConfiguration config() {
        return getConfig();
    }

    public BanDialogManager banDialogos() { return banDialogos; }
    public WebBanSync webBans() { return webBans; }

    public StaffItems items() {
        return items;
    }

    /** Los sonidos configurables del plugin. */
    public Sonidos sonidos() {
        return sonidos;
    }

    public StaffManager gestor() {
        return staff;
    }

    public MuteStore silencios() {
        return silencios;
    }

    public SanctionStore historial() {
        return historial;
    }

public StaffKit kit() {
    return kit;
    }

public MenuTeleporte menuTeleporte() {
    return menuTeleporte;
    }

public SancionStore sanciones() {
    return sanciones;
    }

    public NotaStore notas() {
        return notas;
    }

    public VigilanciaStore vigilancia() {
        return vigilancia;
    }

    public StaffChatLog chatLog() {
        return chatLog;
    }

    public FreezeStore congelados() {
        return congelados;
    }

    public ConexionStore conexiones() {
        return conexiones;
    }

    public BloqueoStore bloqueos() {
        return bloqueos;
    }

    public ActividadStore actividad() {
        return actividad;
    }

    /** El menu de sanciones. */
    public MenuSancion menuSancion() {
        return menuSancion;
    }

    /** La ficha de jugador, a la que se entra desde el panel de /staff. */
    public MenuJugador menuJugadores() {
        return menuJugadores;
    }

    /** El menu que abre /staff. */
    public MenuStaff menuStaff() {
        return menuStaff;
    }

    public MenuEspiar menuEspiar() {
        return menuEspiar;
    }

    public MenuSanciones menuSanciones() {
        return menuSanciones;
    }

    public MenuCopiarSkin menuCopiar() {
        return menuCopiar;
    }

    public WarnStore warns() {
        return warns;
    }

    public ReportStore reportes() {
        return reportes;
    }

    public EvidenceStore evidencias() {
        return evidencias;
    }

    public RedemptionStore redencion() {
        return redencion;
    }

    public MenuHistorial menuHistorial() {
        return menuHistorial;
    }

    public MenuBanear menuBanear() {
        return menuBanear;
    }

    public EspiaStore espia() {
        return espia;
    }

    /** Lo que un staff tiene pendiente de escribir en el chat. */
    public EsperaStore espera() {
        return espera;
    }

    /**
     * El almacén de una tienda concreta ("silencios", "historial"...).
     *
     * Los stores no tocan disco: piden su almacén y ya está, sea base de datos
     * o sea archivos .yml.
     */
    public Almacen almacen(String tienda, String archivo) {
        return fabricas().de(tienda, archivo);
    }

    private AlmacenSeguro.Fabrica fabricas() {
        return almacen;
    }

    /** Si los datos se estan guardando en base de datos y no en archivos. */
    public boolean usandoBaseDeDatos() {
        return almacen != null && almacen.usandoBase();
    }

    /**
     * Vuelca un .yml restaurado por /staffrestaurar en la base de datos.
     *
     * Devuelve las filas escritas, o -1 si no hay base de datos o ese archivo
     * no corresponde a ningun store.
     */
    public int importarEnBase(String base) {
        if (almacen == null || !almacen.usandoBase()) return -1;
        return almacen.importarArchivo(base);
    }

    /**
     * Prepara el acceso a los datos antes de construir ningún store.
     *
     * Si la base de datos falla (MySQL no esta, contrasena mal, el driver no
     * baja) NO se para el arranque: se avisa por consola y se sigue con los
     * .yml de siempre. Un problema de base de datos no puede dejar el servidor
     * sin silencios ni sin sanciones.
     */
    private void iniciarAlmacen() {
        String tipo = config().getString("database.type", "sqlite").trim().toLowerCase(java.util.Locale.ROOT);
        if (tipo.equals("yaml")) {
            almacen = new AlmacenSeguro.Fabrica(this, null);
            getLogger().info("[bd] guardando en archivos .yml (database.type: yaml).");
            return;
        }
        if (!List.of("sqlite", "mysql", "mariadb", "postgresql", "h2").contains(tipo)) {
            getLogger().warning("[bd] tipo de base de datos '" + tipo + "' no valido; se usaran archivos .yml.");
            almacen = new AlmacenSeguro.Fabrica(this, null);
            return;
        }
        String seccionBd = tipo.equals("mariadb") ? "mysql" : tipo;
        String archivoDefecto = tipo.equals("h2") ? "odisseystaff-h2" : "odisseystaff.db";
        String archivo = config().getString("database." + seccionBd + ".file", archivoDefecto);
        File sqlite = new File(archivo);
        if (!sqlite.isAbsolute()) sqlite = new File(getDataFolder(), archivo);
        ConexionBd bd = new ConexionBd(this, tipo, sqlite,
                config().getString("database." + seccionBd + ".host", "localhost"),
                config().getInt("database." + seccionBd + ".port", tipo.equals("postgresql") ? 5432 : 3306),
                config().getString("database." + seccionBd + ".database", "odisseystaff"),
                config().getString("database." + seccionBd + ".user", tipo.equals("h2") ? "sa" : "root"),
                config().getString("database." + seccionBd + ".password", ""));
        try {
            bd.iniciar();
            conexionBd = bd;
            almacen = new AlmacenSeguro.Fabrica(this, bd);
        } catch (Exception e) {
            getLogger().warning("[bd] no se pudo usar " + tipo + " (" + e.getMessage()
                    + "); se guardara en archivos .yml como hasta ahora.");
            bd.cerrar();
            almacen = new AlmacenSeguro.Fabrica(this, null);
        }
    }

    /**
     * Avisa al equipo de una sola vez a todos los que estan dentro.
     *
     * Se usa para lo que no es decision de un staff concreto: el antid-spam que
     * ha saltado solo, una cuenta nueva que ha entrado, alguien con la IP de un
     * baneado. Esos avisos se repiten a todo el mundo a proposito, porque la
     * persona que tiene que enterarse puede ser cualquiera del turno.
     */
    public void avisarEquipo(String texto) {
        List<Player> staff = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (esStaff(p)) staff.add(p);
        }
        if (staff.isEmpty()) return;
        for (Player p : staff) {
            p.sendMessage(Text.prefijo() + ChatColor.GRAY + texto);
        }
        if (config().getBoolean("avisos.avisar-en-consola", true)) {
            getLogger().info("[aviso] " + ChatColor.stripColor(texto));
        }
    }

    /** Lo que ha dicho cada jugador, guardado en disco. */
    public ChatJugadoresLog chatJugadores() {
        return chatJugadores;
    }

    /**
     * Si este staff puede aplicar una sancion a ese jugador.
     *
     * El menu la necesita desde fuera del comando, asi que el filtro vive aqui
     * y no en la clase del comando. Mismas reglas que en /staffsancion: nada de
     * tocarse a uno mismo, y para tocar a otro staff hace falta odissey.admin.
     */
    public boolean puedeSancionar(org.bukkit.command.CommandSender quien, Player objetivo) {
        if (objetivo == null) return true;
        if (objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No te puedes sancionar a ti mismo."));
            return false;
        }
        if (!esStaff(objetivo)) return true;
        if (!quien.hasPermission("odissey.admin")) {
            quien.sendMessage(Text.error(objetivo.getName() + " es parte del staff."));
            quien.sendMessage(Text.info("Necesitas el permiso odissey.admin para sancionarlo."));
            return false;
        }
        if (objetivo.hasPermission("odissey.admin")) {
            quien.sendMessage(Text.error(objetivo.getName() + " es admin. No puedes sancionarlo."));
            return false;
        }
        return true;
    }

    /** Aviso al equipo cuando alguien toca a otro staff. */
    public void avisarStaffDeObjetivo(org.bukkit.command.CommandSender quien, Player objetivo, String que) {
        if (!esStaff(objetivo)) return;
        getLogger().info("[staff] " + quien.getName() + " aplico " + que + " a " + objetivo.getName() + " (staff).");
        for (Player otro : Bukkit.getOnlinePlayers()) {
            if (otro.equals(quien) || !esStaff(otro)) continue;
            otro.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "STAFF  "
                    + ChatColor.GRAY + quien.getName() + ChatColor.WHITE
                    + " aplico " + que + " a " + objetivo.getName()
                    + ChatColor.GRAY + ", que es del equipo.");
        }
    }

    /**
     * Convierte "30m", "2h", "7d" en milisegundos, o 0 si no vale.
     *
     * Lo usan el sistema de sanciones y las celdas, para no repetir el mismo
     * codigo de leer el tiempo en dos sitios distintos.
     */
    public long leerTiempoEnMs(String texto) {
        if (texto == null) return 0;
        String t = texto.trim().toLowerCase(java.util.Locale.ROOT);
        long tope = 30L * 86400_000L;
        try {
            if (t.endsWith("s")) return duracionLimitada(t, 1_000L, tope);
            if (t.endsWith("m")) return duracionLimitada(t, 60_000L, tope);
            if (t.endsWith("h")) return duracionLimitada(t, 3_600_000L, tope);
            if (t.endsWith("d")) return duracionLimitada(t, 86_400_000L, tope);
        } catch (NumberFormatException e) {
            return 0;
        }
        return 0;
    }

    private long duracionLimitada(String texto, long factor, long tope) {
        try {
            long cantidad = Long.parseLong(texto.substring(0, texto.length() - 1));
            if (cantidad <= 0) return 0;
            return cantidad > tope / factor ? tope : cantidad * factor;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
/**
     * El jugador esta inmovilizado: congelado o sancionado.
     *
     * Antes cada listener comprobaba una cosa distinta: el congelado para el
     * movimiento y el sancionado para los comandos. Salia un estado rarísimo
     * que no era ni de uno ni de otro: congelado con el bloque de hielo no
     * podia moverse, pero sí tirar perlas, abrir cofres ni usar comandos de
     * teletransporte. Es decir, "congelado" solo queria decir quieto, y el
     * staff que lo creiaAttributesRead a una celda.
     *
     * Ahora congelar es lo mismo que sancionar en esto: no puede hacer nada.
     * Lo unico que cambia es el texto que ve y quien lo puede levantar.
     *
     * El staff NUNCA cuenta como inmovilizado: si a un admin le pisan una
     * sancion tiene que poder usar /staffsancion off para sacarsela, o se
     * queda encerrado sin salida.
     */
    public boolean inmovilizado(Player jugador) {
        if (jugador == null) return false;
        if (esStaff(jugador)) return false;
        return staff.congelado(jugador) || sanciones.bloqueado(jugador);
    }

    /** Como inmovilizado(), pero para el staff: no hay excepciones. */
    public boolean inmovilizadoSinExcepcion(Player jugador) {
        if (jugador == null) return false;
        return staff.congelado(jugador) || sanciones.bloqueado(jugador);
    }

    /** El texto que le toca ver al jugador segun por que esta quieto. */
    public String motivoInmovilizado(Player jugador) {
        if (staff.congelado(jugador)) {
            StaffManager.Estado e = staff.estado(jugador);
            return e != null && !e.congeladoPor.isEmpty()
                    ? ChatColor.GRAY + "Congelado por " + ChatColor.WHITE + e.congeladoPor
                    + ChatColor.GRAY + ". "
                    : ChatColor.GRAY + "Estás congelado. ";
        }
        SancionStore.Registro r = sanciones.obtener(jugador.getUniqueId());
        return ChatColor.GRAY + "Estás sancionado. Te queda: "
                + ChatColor.YELLOW + SancionStore.enLetras(r == null ? 0 : r.restante);
    }

    /** El jugador es staff si tiene el permiso o, si se pidio asi, si es op. */
    public boolean esStaff(org.bukkit.command.CommandSender quien) {
        if (quien.hasPermission("odissey.staff")) return true;
        if (!config().getBoolean("usar-op-como-staff", true)) return false;
        return quien.isOp();
    }

    /**
     * Emite un anuncio publico en el chat para todo el servidor informando
     * de una sancion, muteo o baneo. La plantilla se toma de config.yml (broadcasts).
     */
    /**
     * Asegura que las opciones nuevas (como broadcasts) se agreguen al archivo config.yml
     * existente en el servidor sin necesidad de borrar la carpeta o perder datos previos.
     */
    private void asegurarDefaultsConfig() {
        boolean cambios = false;

        if (!getConfig().isSet("web-ban-sync.enabled")) {
            getConfig().set("web-ban-sync.enabled", false);
            getConfig().set("web-ban-sync.supabase-url", "https://cffpnxmtwfaublkvrobt.supabase.co");
            getConfig().set("web-ban-sync.service-key", "");
            getConfig().set("web-ban-sync.server-id", "odissey-main");
            cambios = true;
        }

        if (!getConfig().isSet("chat-control.muted")) {
            getConfig().set("chat-control.muted", false);
            cambios = true;
        }
        if (!getConfig().isSet("limites-sanciones")) {
            getConfig().set("limites-sanciones.activo", true);
            getConfig().set("limites-sanciones.bans-por-hora", 20);
            getConfig().set("limites-sanciones.permanentes-por-dia", 5);
            cambios = true;
        }
        if (!getConfig().isSet("evidencias.capturar-al-banear")) {
            getConfig().set("evidencias.capturar-al-banear", true);
            cambios = true;
        }
        if (!getConfig().isSet("evidencias.max-registros")) {
            getConfig().set("evidencias.max-registros", 5000);
            cambios = true;
        }
        if (!getConfig().isSet("redemption.enabled")) {
            getConfig().set("redemption.enabled", true);
            getConfig().set("redemption.cooldown", "24h");
            getConfig().set("redemption.captcha-math.enabled", true);
            getConfig().set("redemption.captcha-math.max-attempts", 3);
            getConfig().set("redemption.captcha-math.reduction", 10);
            getConfig().set("redemption.captcha-math.one-time", false);
            cambios = true;
        }
        if (!getConfig().isSet("integraciones.discord-activo")) {
            getConfig().set("integraciones.discord-activo", false);
            getConfig().set("integraciones.discord-webhook", "");
            cambios = true;
        }

        if (!getConfig().isSet("integraciones.telegram.activo")) {
            getConfig().set("integraciones.telegram.activo", false);
            getConfig().set("integraciones.telegram.token", "");
            getConfig().set("integraciones.telegram.chat-id", "");
            cambios = true;
        }
        if (!getConfig().isSet("geoip.enabled")) {
            getConfig().set("geoip.enabled", false);
            getConfig().set("geoip.cache-minutes", 360);
            cambios = true;
        }
        if (!getConfig().isSet("chat-snapshots.enabled")) {
            getConfig().set("chat-snapshots.enabled", true);
            getConfig().set("chat-snapshots.window-minutes", 20);
            getConfig().set("chat-snapshots.max-messages", 20);
            cambios = true;
        }
        if (!getConfig().isSet("ai.enabled")) {
            getConfig().set("ai.enabled", false);
            getConfig().set("ai.api-key", "");
            getConfig().set("ai.model", "gpt-6-luna");
            getConfig().set("ai.window-minutes", 60);
            getConfig().set("ai.max-messages", 20);
            cambios = true;
        }
        if (!getConfig().isSet("data-wipe.enabled")) {
            getConfig().set("data-wipe.enabled", false);
            cambios = true;
        }
        if (!getConfig().isSet("proxy-kick.enabled")) {
            getConfig().set("proxy-kick.enabled", true);
            cambios = true;
        }
        if (!getConfig().isSet("mensajes.servidor")) {
            getConfig().set("mensajes.servidor", "Odissey");
            cambios = true;
        }
        if (!getConfig().isSet("mensajes.desconexion-baneo")) {
            getConfig().set("mensajes.desconexion-baneo", List.of(
                    "&cHas sido baneado de %servidor%",
                    "",
                    "&6Motivo: &f%motivo%",
                    "&6Duración: &f%duracion%",
                    "&6Staff: &f%staff%"
            ));
            cambios = true;
        }
        if (!getConfig().isSet("ban-dialog.presets")) {
            getConfig().set("ban-dialog.presets", List.of(
                    java.util.Map.of("nombre", "Spam", "cantidad", 1, "unidad", "h", "motivo", "Spam reiterado"),
                    java.util.Map.of("nombre", "Insultos", "cantidad", 1, "unidad", "d", "motivo", "Insultos o faltas de respeto"),
                    java.util.Map.of("nombre", "Uso de hacks", "cantidad", 7, "unidad", "d", "motivo", "Uso de modificaciones no permitidas"),
                    java.util.Map.of("nombre", "Abuso de exploits", "cantidad", 30, "unidad", "d", "motivo", "Abuso de exploits")
            ));
            cambios = true;
        }
        if (!getConfig().isSet("coreprotect.max-seconds")) {
            getConfig().set("coreprotect.max-seconds", 86400);
            getConfig().set("coreprotect.max-radius", 32);
            cambios = true;
        }

        if (!getConfig().isSet("ghost-mute")) {
            getConfig().set("ghost-mute.enabled", true);
            getConfig().set("ghost-mute.echo-to-player", true);
            getConfig().set("ghost-mute.staff-prefix", "&8[&cGhost&8] ");
            cambios = true;
        }

        if (!getConfig().isSet("broadcasts.activo")) {
            getConfig().set("broadcasts.activo", true);
            cambios = true;
        }

        if (!getConfig().isSet("broadcasts.mute")) {
            getConfig().set("broadcasts.mute.enabled", true);
            getConfig().set("broadcasts.mute.mensaje", List.of(
                    "&8&m----------------------------------------",
                    "&8[&c&lSILENCIO&8] &f%jugador% &7ha sido silenciado",
                    "&7  &eStaff: &f%staff%",
                    "&7  &eTiempo: &f%duracion%",
                    "&7  &eMotivo: &f%motivo%",
                    "&8&m----------------------------------------"
            ));
            cambios = true;
        }

        if (!getConfig().isSet("broadcasts.sancion")) {
            getConfig().set("broadcasts.sancion.enabled", true);
            getConfig().set("broadcasts.sancion.mensaje", List.of(
                    "&8&m----------------------------------------",
                    "&8[&c&lSANCIÓN&8] &f%jugador% &7ha sido sancionado",
                    "&7  &eStaff: &f%staff%",
                    "&7  &eTiempo: &f%duracion%",
                    "&7  &eMotivo: &f%motivo%",
                    "&8&m----------------------------------------"
            ));
            cambios = true;
        }

        if (!getConfig().isSet("broadcasts.ban")) {
            getConfig().set("broadcasts.ban.enabled", true);
            getConfig().set("broadcasts.ban.mensaje", List.of(
                    "&8&m----------------------------------------",
                    "&8[&4&lBANEO&8] &f%jugador% &7ha sido baneado del servidor",
                    "&7  &cStaff: &f%staff%",
                    "&7  &cTiempo: &f%duracion%",
                    "&7  &cMotivo: &f%motivo%",
                    "&8&m----------------------------------------"
            ));
            cambios = true;
        }

        if (cambios) {
            saveConfig();
        }
    }

    /**
     * Emite un anuncio publico en el chat para todo el servidor informando
     * de una sancion, muteo o baneo en formato de lista (lore).
     */
    public void anunciarSancion(String tipo, String jugador, String staff, String motivo, String duracion) {
        if (notificacionesExternas != null) notificacionesExternas.sancion(tipo, jugador, staff, motivo, duracion);
        if (config().getBoolean("chat-snapshots.enabled", true) && evidencias != null && chatJugadores != null) {
            evidencias.capturarChat(jugador, chatJugadores,
                    config().getInt("chat-snapshots.window-minutes", 20),
                    config().getInt("chat-snapshots.max-messages", 20));
        }
        if (!config().getBoolean("broadcasts.activo", true)) return;
        
        // Verificar si este tipo especifico esta habilitado (ej. broadcasts.mute.enabled)
        if (config().isSet("broadcasts." + tipo + ".enabled") && !config().getBoolean("broadcasts." + tipo + ".enabled", true)) {
            return;
        }

        List<String> lineas = new ArrayList<>();
        if (config().isList("broadcasts." + tipo + ".mensaje")) {
            lineas = config().getStringList("broadcasts." + tipo + ".mensaje");
        } else if (config().isString("broadcasts." + tipo + ".mensaje")) {
            lineas.add(config().getString("broadcasts." + tipo + ".mensaje"));
        } else if (config().isList("broadcasts." + tipo)) {
            lineas = config().getStringList("broadcasts." + tipo);
        } else if (config().isString("broadcasts." + tipo)) {
            lineas.add(config().getString("broadcasts." + tipo));
        }

        if (lineas == null || lineas.isEmpty()) return;

        String dur = (duracion == null || duracion.isEmpty()) ? "permanente" : duracion;
        String mot = (motivo == null || motivo.isEmpty()) ? "Sin motivo especificado" : motivo;

        for (String linea : lineas) {
            String reemplazada = Text.con(linea,
                    "%jugador%", jugador,
                    "%staff%", staff,
                    "%motivo%", mot,
                    "%duracion%", dur,
                    "%tiempo%", dur);
            String formateada = Text.c(reemplazada);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(formateada);
            }
            Bukkit.getConsoleSender().sendMessage(formateada);
        }
    }
    /**
     * Recarga la configuracion del plugin sin reiniciar el servidor.
     */
    public void recargar() {
        reloadConfig();
        Text.cargar(config().getString("mensajes.prefijo", Text.PREFIJO_POR_DEFECTO));
        asegurarDefaultsConfig();
        getLogger().info("[OdisseyStaff] Configuracion recargada.");
    }

}
