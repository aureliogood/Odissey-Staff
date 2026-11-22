package es.odissey.staff;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * /staffunmute, /staffhistory, /stafffrozen, /staffrespawn y /staffspectate.
 *
 * Son comandos de consulta y de pequeñas correcciones que antes no existian o que
 * obligaban a editar ficheros a mano. El mas urgente era /staffunmute: se
 * podia silenciar a alguien, pero no habia forma de levantar el silencio.
 */
public final class StaffUtilCommands extends BaseCommand {


    public StaffUtilCommands(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        String sub = comando.getName().toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "staffunmute": unmute(quien, args); break;
            case "unban": unban(quien, args); break;
            case "unwarn": unwarn(quien, args); break;
            case "staffhistory": history(quien, args); break;
            case "checkban": checkBan(quien, args); break;
            case "checkmute": checkMute(quien, args); break;
            case "warnings": warnings(quien, args); break;
            case "banlist": banList(quien); break;
            case "mutelist": muteList(quien); break;
            case "stafffrozen": frozen(quien); break;
            case "staffrespawn": respawn(quien, args); break;
            case "staffspectate": spectate(quien, args); break;
            case "staffnota": nota(quien, args); break;
            case "staffvigilar": vigilar(quien, args); break;
            case "staffchatlog": chatLog(quien, args); break;
            case "staffonline": online(quien, args); break;
            case "staffbloquear": bloquearCmd(quien, args); break;
            case "staffalt": alt(quien, args); break;
            case "network": network(quien, args); break;
            case "staffinvsee": invsee(quien, args); break;
            case "staffsnapshot": snapshot(quien, args); break;
            case "staffjaula": jaula(quien, args); break;
            default: quien.sendMessage(Text.error("Comando desconocido: " + sub));
        }
    }

    private void unban(CommandSender quien, String[] args) {
        if (args.length == 0) { quien.sendMessage(Text.error("Uso: /unban <jugador>")); return; }
        String nombre = args[0];
        String ip = IpAddress.normalizar(nombre);
        if (ip != null) {
            var listaIp = plugin.getServer().getBanList(org.bukkit.BanList.Type.IP);
            if (!listaIp.isBanned(ip)) { quien.sendMessage(Text.aviso("La IP " + ip + " no tiene un ban activo.")); return; }
            listaIp.pardon(ip);
            long idIp = plugin.historial().ultimaDe("ipban", ip);
            if (idIp > 0) plugin.historial().marcarLevantada(idIp, quien.getName());
            plugin.historial().guardar();
            quien.sendMessage(Text.exito("Ban levantado para la IP " + ip + "."));
            avisar(quien, "ban por IP levantado para " + ip);
            return;
        }
        var lista = plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME);
        if (!lista.isBanned(nombre)) {
            quien.sendMessage(Text.aviso(nombre + " no tiene un ban activo."));
            return;
        }
        lista.pardon(nombre);
        long id = plugin.historial().ultimaDe("baneo", nombre);
        if (id > 0) {
            plugin.historial().marcarLevantada(id, quien.getName());
            plugin.webBans().revocar(id, quien.getName());
        }
        plugin.historial().guardar();
        quien.sendMessage(Text.exito("Ban levantado a " + nombre + "."));
        avisar(quien, "ban levantado a " + nombre);
    }

    private void unwarn(CommandSender quien, String[] args) {
        if (args.length == 0) { quien.sendMessage(Text.error("Uso: /unwarn <jugador> [id]")); return; }
        String nombre = args[0];
        List<WarnStore.Aviso> activos = plugin.warns().de(nombre);
        if (activos.isEmpty()) { quien.sendMessage(Text.aviso(nombre + " no tiene avisos activos.")); return; }
        long id;
        if (args.length > 1) {
            try { id = Long.parseLong(args[1]); }
            catch (NumberFormatException e) { quien.sendMessage(Text.error("El id debe ser numérico.")); return; }
        } else {
            id = activos.get(0).id;
        }
        if (!plugin.warns().retirar(id, quien.getName())) {
            quien.sendMessage(Text.error("No existe un aviso activo con id #" + id + " para " + nombre + "."));
            return;
        }
        quien.sendMessage(Text.exito("Aviso #" + id + " retirado de " + nombre + "."));
        avisar(quien, "aviso #" + id + " retirado de " + nombre);
    }

    // ---------------- Consultas de LiteBans ----------------

    private void checkBan(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /checkban <jugador>"));
            return;
        }
        String nombre = args[0];
        String ip = IpAddress.normalizar(nombre);
        org.bukkit.BanEntry<?> entrada;
        if (ip == null) entrada = plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME).getBanEntry(nombre);
        else entrada = plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).getBanEntry(ip);
        if (entrada == null) {
            quien.sendMessage(Text.exito((ip == null ? nombre : "La IP " + ip) + " no tiene un ban activo."));
            return;
        }
        String caduca = entrada.getExpiration() == null
                ? "permanente" : Fechas.diaHora(entrada.getExpiration().getTime());
        quien.sendMessage(Text.marca() + ChatColor.RED + "Ban activo para " + ChatColor.WHITE + (ip == null ? nombre : ip));
        quien.sendMessage(ChatColor.GRAY + " · Motivo: " + ChatColor.YELLOW
                + (entrada.getReason() == null ? "sin motivo" : entrada.getReason()));
        quien.sendMessage(ChatColor.GRAY + " · Por: " + ChatColor.WHITE
                + (entrada.getSource() == null ? "desconocido" : entrada.getSource()));
        quien.sendMessage(ChatColor.GRAY + " · Caduca: " + ChatColor.WHITE + caduca);
    }

    private void checkMute(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /checkmute <jugador>"));
            return;
        }
        String nombre = args[0];
        String ip = IpAddress.normalizar(nombre);
        if (ip != null) {
            MuteStore.Registro porIp = plugin.silencios().registroIp(ip);
            if (porIp == null || !plugin.silencios().silenciadoIp(ip)) {
                quien.sendMessage(Text.exito("La IP " + ip + " no tiene un silencio activo."));
                return;
            }
            String venceIp = porIp.hasta == Long.MAX_VALUE ? "permanente" : Fechas.diaHora(porIp.hasta);
            quien.sendMessage(Text.marca() + ChatColor.AQUA + "Silencio activo para IP " + ChatColor.WHITE + ip);
            quien.sendMessage(ChatColor.GRAY + " · Motivo: " + ChatColor.YELLOW + porIp.motivo);
            quien.sendMessage(ChatColor.GRAY + " · Caduca: " + ChatColor.WHITE + venceIp);
            return;
        }
        Player online = plugin.getServer().getPlayerExact(nombre);
        org.bukkit.OfflinePlayer offline = online == null
                ? plugin.getServer().getOfflinePlayer(nombre) : online;
        MuteStore.Registro registro = plugin.silencios().registro(offline.getUniqueId());
        if (registro == null || !plugin.silencios().silenciado(offline.getUniqueId())) {
            quien.sendMessage(Text.exito(nombre + " no tiene un silencio activo."));
            return;
        }
        String caduca = registro.hasta == Long.MAX_VALUE ? "permanente" : Fechas.diaHora(registro.hasta);
        quien.sendMessage(Text.marca() + ChatColor.AQUA + "Silencio activo para "
                + ChatColor.WHITE + registro.nombre);
        quien.sendMessage(ChatColor.GRAY + " · Motivo: " + ChatColor.YELLOW
                + (registro.motivo.isEmpty() ? "sin motivo" : registro.motivo));
        quien.sendMessage(ChatColor.GRAY + " · Caduca: " + ChatColor.WHITE + caduca);
        if (registro.fantasma) quien.sendMessage(ChatColor.GRAY + " · Modo: " + ChatColor.RED + "fantasma");
    }

    private void warnings(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /warnings <jugador>"));
            return;
        }
        String nombre = args[0];
        List<WarnStore.Aviso> avisos = plugin.warns().de(nombre);
        quien.sendMessage(Text.marca() + ChatColor.GOLD + "Avisos activos de "
                + ChatColor.WHITE + nombre + ChatColor.GRAY + " (" + avisos.size() + ")");
        if (avisos.isEmpty()) {
            quien.sendMessage(Text.info("No tiene avisos activos."));
            return;
        }
        for (WarnStore.Aviso aviso : avisos) {
            quien.sendMessage(ChatColor.GRAY + " #" + aviso.id + " · " + Fechas.diaHora(aviso.cuando)
                    + ChatColor.YELLOW + " · " + aviso.motivo + ChatColor.DARK_GRAY + " por " + aviso.staff);
        }
        quien.sendMessage(Text.info("Retirar uno: /staffwarn " + nombre + " quitar <id>"));
    }

    private void banList(CommandSender quien) {
        List<?> entradas = new ArrayList<>(plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME).getBanEntries());
        List<?> ips = new ArrayList<>(plugin.getServer().getBanList(org.bukkit.BanList.Type.IP).getBanEntries());
        quien.sendMessage(Text.marca() + ChatColor.RED + "Bans activos: " + entradas.size()
                + " por nombre, " + ips.size() + " por IP");
        if (entradas.isEmpty() && ips.isEmpty()) {
            quien.sendMessage(Text.info("No hay bans activos."));
            return;
        }
        int limite = Math.min(20, entradas.size());
        for (int i = 0; i < limite; i++) {
            Object objeto = entradas.get(i);
            if (!(objeto instanceof org.bukkit.BanEntry<?> entrada)) continue;
            String caduca = entrada.getExpiration() == null ? "permanente"
                    : Fechas.diaHora(entrada.getExpiration().getTime());
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + entrada.getTarget()
                    + ChatColor.DARK_GRAY + " hasta " + caduca
                    + (entrada.getReason() == null ? "" : ChatColor.YELLOW + " · " + entrada.getReason()));
        }
        if (entradas.size() > limite) quien.sendMessage(Text.info("Y " + (entradas.size() - limite) + " más."));
        int limiteIp = Math.min(20, ips.size());
        for (int i = 0; i < limiteIp; i++) {
            Object objeto = ips.get(i);
            if (!(objeto instanceof org.bukkit.BanEntry<?> entrada)) continue;
            String caduca = entrada.getExpiration() == null ? "permanente"
                    : Fechas.diaHora(entrada.getExpiration().getTime());
            quien.sendMessage(ChatColor.GRAY + " · IP " + ChatColor.WHITE + entrada.getTarget()
                    + ChatColor.DARK_GRAY + " hasta " + caduca
                    + (entrada.getReason() == null ? "" : ChatColor.YELLOW + " · " + entrada.getReason()));
        }
        if (ips.size() > limiteIp) quien.sendMessage(Text.info("Y " + (ips.size() - limiteIp) + " bans de IP más."));
    }

    private void muteList(CommandSender quien) {
        List<MuteStore.Registro> registros = plugin.silencios().todos();
        quien.sendMessage(Text.marca() + ChatColor.AQUA + "Silencios activos (" + registros.size() + ")");
        if (registros.isEmpty()) {
            quien.sendMessage(Text.info("No hay silencios activos."));
            return;
        }
        registros.sort((a, b) -> Long.compare(b.puesto, a.puesto));
        int limite = Math.min(20, registros.size());
        for (int i = 0; i < limite; i++) {
            MuteStore.Registro registro = registros.get(i);
            String caduca = registro.hasta == Long.MAX_VALUE ? "permanente" : Fechas.diaHora(registro.hasta);
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + registro.nombre
                    + ChatColor.DARK_GRAY + " hasta " + caduca
                    + (registro.motivo.isEmpty() ? "" : ChatColor.YELLOW + " · " + registro.motivo));
        }
        if (registros.size() > limite) quien.sendMessage(Text.info("Y " + (registros.size() - limite) + " más."));
    }

    // ---------------- /staffonline ----------------

    /** Quien ha estado conectado en las ultimas horas. */
    private void online(CommandSender quien, String[] args) {
        int horas = 6;
        if (args.length > 0) horas = enteroSeguro(args[0], horas);
        horas = Math.max(1, Math.min(horas, 168));

        java.util.Map<String, List<ConexionStore.Sesion>> mapa = plugin.conexiones().enLasUltimas(horas);
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                + "══ Conectados en las últimas " + horas + " h (" + mapa.size() + ") ══");
        if (mapa.isEmpty()) {
            quien.sendMessage(Text.info("Nadie se ha conectado en ese rato."));
            return;
        }
        java.util.List<String> dentro = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, List<ConexionStore.Sesion>> e : mapa.entrySet()) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + e.getKey()
                    + ChatColor.DARK_GRAY + " · " + e.getValue().size() + " sesión(es)");
            for (ConexionStore.Sesion s : e.getValue()) {
                boolean abierto = s.salio == 0;
                if (abierto) dentro.add(e.getKey());
                String ip = s.ip == null || s.ip.isEmpty() ? "" : ChatColor.DARK_GRAY + " · " + ChatColor.YELLOW + s.ip;
                quien.sendMessage(ChatColor.DARK_GRAY + "      " + ConexionStore.hora(s.entro)
                        + " → " + (abierto ? ChatColor.GREEN + "sigue dentro" : ConexionStore.hora(s.salio))
                        + ip);
            }
        }
        quien.sendMessage(Text.info("Ahora mismo dentro: "
                + (dentro.isEmpty() ? "nadie" : String.join(", ", dentro))));
    }

    // ---------------- /staffbloquear ----------------

    /** Un comando prohibido a un jugador concreto. */
    private void bloquearCmd(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffbloquear <jugador> <comando> | /staffbloquear <jugador> off | /staffbloquear"));
            return;
        }
        if (args.length == 1) {
            java.util.Map<String, java.util.Set<String>> todos = plugin.bloqueos().todos();
            quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                    + "══ Comandos bloqueados (" + todos.size() + " jugador(es)) ══");
            if (todos.isEmpty()) {
                quien.sendMessage(Text.info("No hay ninguno."));
                return;
            }
            for (java.util.Map.Entry<String, java.util.Set<String>> e : todos.entrySet()) {
                quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + e.getKey()
                        + ChatColor.DARK_GRAY + " → " + String.join(", ", e.getValue()));
            }
            return;
        }

        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();
        String cmd = args[1].toLowerCase(java.util.Locale.ROOT);

        if (cmd.equals("off") || cmd.equals("quitar") || cmd.equals("all")) {
            if (plugin.bloqueos().quitar(nombre, cmd.equals("all") ? null : cmd)) {
                quien.sendMessage(Text.exito("Quitado de " + nombre + "."));
                plugin.getLogger().info("[bloqueos] " + quien.getName() + " desbloquea a " + nombre);
            } else {
                quien.sendMessage(Text.aviso(nombre + " no tenía ese comando bloqueado."));
            }
            return;
        }

        plugin.bloqueos().bloquear(nombre, cmd, quien.getName());
        quien.sendMessage(Text.exito(nombre + " ya no puede usar /" + cmd + "."));
        if (online != null) {
            online.sendMessage(Text.error("Un miembro del staff te ha bloqueado el comando /" + cmd + "."));
        }
        plugin.getLogger().info("[bloqueos] " + quien.getName() + " bloquea /" + cmd + " a " + nombre);
    }

    // ---------------- /staffalt ----------------

    /** Cuentas alternativas: misma IP, nombres parecidos y horas jugadas. */
    private void alt(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffalt <jugador>"));
            return;
        }
        String nombre = args[0];
        String ipDirecta = IpAddress.normalizar(nombre);
        if (ipDirecta != null) {
            List<String> nombres = plugin.conexiones().mismaIp(ipDirecta, "");
            quien.sendMessage(Text.marca() + ChatColor.GOLD + "Cuentas vistas desde " + ipDirecta
                    + ChatColor.GRAY + " (" + nombres.size() + ")");
            quien.sendMessage(nombres.isEmpty() ? Text.info("No hay cuentas registradas para esa IP.")
                    : ChatColor.RED + String.join(", ", nombres));
            return;
        }
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        String ip = plugin.conexiones().ultimaIp(nombre);
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ "
                + ChatColor.WHITE + nombre + ChatColor.GOLD + " ══");
        quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Última IP: "
                + (ip.isEmpty() ? "sin datos" : ChatColor.YELLOW + ip));
        quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Jugado hoy: "
                + ChatColor.YELLOW + ConexionStore.bonito(plugin.conexiones().jugadosHoy(nombre)));

        List<String> mismaIp = plugin.conexiones().mismaIp(ip, nombre);
        if (mismaIp.isEmpty()) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Misma IP: "
                    + ChatColor.DARK_GRAY + "nadie más");
        } else {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Misma IP ("
                    + mismaIp.size() + "): " + ChatColor.RED + String.join(", ", mismaIp));
            quien.sendMessage(Text.info("ojo: si es una casa con varios jugadores, es lo normal"));
        }

        List<String> parecidos = plugin.conexiones().nombresParecidos(nombre);
        if (parecidos.isEmpty()) {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Nombres parecidos: "
                    + ChatColor.DARK_GRAY + "ninguno");
        } else {
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + "Nombres parecidos: "
                    + ChatColor.RED + String.join(", ", parecidos));
        }

        List<VigilanciaStore.Marca> fichas = new java.util.ArrayList<>();
        for (String n : mismaIp) {
            if (plugin.vigilancia().estaVigilado(n)) fichas.add(plugin.vigilancia().marca(n));
        }
        if (!fichas.isEmpty()) {
            quien.sendMessage(Text.aviso("⚠ " + fichas.size()
                    + " de los nombres con su misma IP están en la lista de vigilancia."));
        }
    }

    /** Atajos compatibles con /network de LiteBansReborn. */
    private void network(CommandSender quien, String[] args) {
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /network <alts|connections> <jugador>"));
            return;
        }
        if (args[0].equalsIgnoreCase("alts")) {
            alt(quien, new String[]{args[1]});
            return;
        }
        if (!args[0].equalsIgnoreCase("connections")) {
            quien.sendMessage(Text.error("Uso: /network <alts|connections> <jugador>"));
            return;
        }
        String nombre = args[1];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();
        String ip = plugin.conexiones().ultimaIp(nombre);
        List<String> otras = ip.isEmpty() ? List.of() : plugin.conexiones().mismaIp(ip, nombre);
        long entrada = plugin.conexiones().entroDe(nombre);
        quien.sendMessage(Text.marca() + ChatColor.GOLD + "Red de " + ChatColor.WHITE + nombre);
        quien.sendMessage(Text.info("Última IP: " + (ip.isEmpty() ? "desconocida" : ip)));
        quien.sendMessage(Text.info("Cuentas asociadas: " + (otras.isEmpty() ? "ninguna registrada" : String.join(", ", otras))));
        quien.sendMessage(Text.info("Conectado ahora: " + (entrada > 0 ? ConexionStore.bonito(System.currentTimeMillis() - entrada) : "no")));
        quien.sendMessage(Text.info("Tiempo jugado hoy: " + ConexionStore.bonito(plugin.conexiones().jugadosHoy(nombre))));
    }

    // ---------------- /staffinvsee ----------------

    /** Lo que llevaba el jugador en el inventario. */
    private void invsee(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffinvsee <jugador>"));
            return;
        }
        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        if (online != null) {
            // Si esta dentro, se le abre el inventario de verdad.
            online = jugador(quien, args[0]);
            if (!soloJugador(quien)) return;
            ((Player) quien).openInventory(online.getInventory());
            quien.sendMessage(Text.info("Viendo el inventario de " + online.getName() + "."));
            return;
        }

        java.util.UUID uuid = plugin.getServer().getOfflinePlayer(nombre).getUniqueId();
        ActividadStore.Inventario inv = plugin.actividad().inventario(uuid);
        if (inv == null || inv.objetos.isEmpty()) {
            quien.sendMessage(Text.aviso("No hay datos de " + nombre + "."));
            quien.sendMessage(Text.info("Se guarda lo que llevaba al salir del servidor, y no ha salido todavía."));
            return;
        }
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Inventario de "
                + ChatColor.WHITE + inv.nombre + ChatColor.GOLD + " (al salir, "
                + ConexionStore.hora(inv.cuando) + ") ══");
        for (String o : inv.objetos) quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + o);
    }

    // ---------------- /staffsnapshot ----------------

    /** Lo que ha hecho en los ultimos minutos. */
    private void snapshot(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffsnapshot <jugador> [minutos]"));
            return;
        }
        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();
        int minutos = plugin.actividad().minutos();
        if (args.length > 1) minutos = enteroSeguro(args[1], minutos);
        minutos = Math.max(1, Math.min(minutos, 240));

        java.util.UUID uuid;
        if (online != null) uuid = online.getUniqueId();
        else uuid = plugin.getServer().getOfflinePlayer(nombre).getUniqueId();

        List<ActividadStore.Hecho> lista = plugin.actividad().reciente(uuid, minutos);
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Actividad de "
                + ChatColor.WHITE + nombre + ChatColor.GOLD + " (últimos " + minutos + " min) ══");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info(online != null
                    ? "No ha hecho nada anotable en ese rato."
                    : "No hay datos: solo se guardan los jugadores que están dentro."));
            return;
        }
        for (ActividadStore.Hecho h : lista) {
            quien.sendMessage(ChatColor.GRAY + h.linea());
        }
        quien.sendMessage(Text.info(lista.size() + " cosas. Se borra al salir del servidor."));
    }

    // ---------------- /staffjaula ----------------

    /**
     * Manda a un jugador a una celda y le quita lo que sobra.
     *
     * Las celdas se configuran en config.yml con un punto y una lista de
     * objetos que el jugador lleva encima y no deberia tener alli.
     */
    private void jaula(CommandSender quien, String[] args) {
        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /staffjaula <jugador> <celda> <tiempo> [motivo]"));
            quien.sendMessage(Text.info("Celdas: " + celdasDisponibles()));
            return;
        }
        String nombre = args[0];
        Player objetivo = jugador(quien, nombre);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, nombre);
        if (objetivo == null) {
            quien.sendMessage(Text.error("Ese jugador no está conectado."));
            return;
        }
        if (objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No te puedes meter a ti mismo."));
            return;
        }
        if (!puedeTocar(quien, objetivo, "mandar a la celda")) return;

        String celda = args[1];

        if (celda.equalsIgnoreCase("off") || celda.equalsIgnoreCase("salir")) {
            plugin.sanciones().quitar(objetivo.getUniqueId());
            plugin.gestor().descongelar(objetivo);
            objetivo.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
            objetivo.sendTitle(null, null, 0, 0, 0);
            objetivo.sendMessage(Text.exito("Te han sacado de la celda."));
            quien.sendMessage(Text.exito("Liberado de la celda."));
            return;
        }

        org.bukkit.configuration.ConfigurationSection seccion =
                plugin.config().getConfigurationSection("jaulas." + celda);
        if (seccion == null) {
            quien.sendMessage(Text.error("No existe la celda " + celda + "."));
            quien.sendMessage(Text.info("Celdas: " + celdasDisponibles()));
            return;
        }

        String mundo = seccion.getString("mundo", objetivo.getWorld().getName());
        org.bukkit.World w = plugin.getServer().getWorld(mundo);
        if (w == null) {
            quien.sendMessage(Text.error("El mundo '" + mundo + "' de la celda " + celda + " no existe."));
            return;
        }

        long ms = 0;
        if (args.length > 2) ms = plugin.leerTiempoEnMs(args[2]);
        if (ms <= 0) {
            quien.sendMessage(Text.error("Falta el tiempo: /staffjaula " + nombre + " " + celda + " 30m [motivo]"));
            return;
        }
        StringBuilder motivo = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (motivo.length() > 0) motivo.append(' ');
            motivo.append(args[i]);
        }
        if (motivo.length() == 0) motivo.append("En la celda");

        org.bukkit.Location destino = new org.bukkit.Location(w,
                seccion.getDouble("x", 0), seccion.getDouble("y", 64), seccion.getDouble("z", 0),
                (float) seccion.getDouble("yaw", 0), (float) seccion.getDouble("pitch", 0));

        // Se le quita lo que la celda marca como no permitido, antes de meterlo.
        List<String> quitados = quitarProhibidos(objetivo, seccion.getStringList("objetos"));

        plugin.gestor().teletransportar(objetivo, destino);
        objetivo.setFallDistance(0f);
        SancionStore.Registro r = plugin.sanciones().poner(objetivo, ms, motivo.toString(), quien.getName());
        plugin.sanciones().congelar(objetivo, r);
        plugin.sanciones().mostrar(objetivo, r);

        objetivo.sendMessage(Text.aviso("Te han metido en la celda " + celda + "."));
        if (!quitados.isEmpty()) {
            objetivo.sendMessage(Text.info("Te han retirado: " + ChatColor.YELLOW + String.join(", ", quitados)));
        }

        quien.sendMessage(Text.exito(nombre + " en la celda " + celda + " por "
                + ChatColor.YELLOW + SancionStore.enLetras(ms) + ChatColor.GREEN + ": " + motivo));
        avisarStaff(quien, objetivo, "mandarlo a la celda " + celda);
        plugin.getLogger().info("[jaula] " + quien.getName() + " -> celda " + celda + " (" + nombre + ")");
    }

    /**
     * Le quita al jugador los materiales que la celda marca como prohibidos.
     *
     * Se recorre el contenido a mano porque Inventory no tiene getItem(Material):
     * solo getItem(int), que es por ranura. Asi se quitan todas las pilas del
     * material, no solo la primera.
     */
    private List<String> quitarProhibidos(Player objetivo, List<String> materiales) {
        List<String> fuera = new ArrayList<>();
        if (materiales == null || materiales.isEmpty()) return fuera;
        org.bukkit.inventory.PlayerInventory inv = objetivo.getInventory();

        for (String m : materiales) {
            org.bukkit.Material mat;
            try {
                mat = org.bukkit.Material.valueOf(m.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[jaula] material desconocido en la celda: " + m);
                continue;
            }

            int quitados = 0;
            ItemStack[] contenido = inv.getStorageContents();
            boolean cambio = false;
            for (int i = 0; i < contenido.length; i++) {
                ItemStack item = contenido[i];
                if (item == null || item.getType() != mat) continue;
                quitados += item.getAmount();
                contenido[i] = null;
                cambio = true;
            }
            // Tambien la armadura y la mano secundaria, que no estan en el
            // contenido normal. getArmorContents() devuelve una copia, asi que
            // hay que limpiarla y volver a aplicarla: antes solo se contaba
            // ("Te han retirado: 1x netherite_helmet") sin quitarla de verdad.
            ItemStack[] armadura = inv.getArmorContents();
            boolean armaCambio = false;
            for (int i = 0; i < armadura.length; i++) {
                ItemStack arm = armadura[i];
                if (arm == null || arm.getType() != mat) continue;
                quitados += arm.getAmount();
                armadura[i] = null;
                armaCambio = true;
            }
            if (armaCambio) {
                inv.setArmorContents(armadura);
                cambio = true;
            }
            if (inv.getItemInOffHand() != null && inv.getItemInOffHand().getType() == mat) {
                quitados += inv.getItemInOffHand().getAmount();
                inv.setItemInOffHand(null);
                cambio = true;
            }
            if (cambio) {
                inv.setStorageContents(contenido);
                fuera.add(quitados + "x " + m.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (!fuera.isEmpty()) objetivo.updateInventory();
        return fuera;
    }

    private String celdasDisponibles() {
        org.bukkit.configuration.ConfigurationSection s = plugin.config().getConfigurationSection("jaulas");
        if (s == null || s.getKeys(false).isEmpty()) return "ninguna (configura jaulas en config.yml)";
        return String.join(", ", s.getKeys(false));
    }

    // ---------------- /staffnota ----------------

    /**
     * Notas internas del staff. El jugador no se entera de que estan anotado.
     *
     *   /staffnota Rob <texto>      -> deja una nota
     *   /staffnota Rob              -> lista sus notas
     *   /staffnota Rob borrar <id>  -> quita una
     */
    private void nota(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffnota <jugador> [borrar <id>] | /staffnota <jugador> <texto>"));
            return;
        }
        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        // Listar
        if (args.length == 1) {
            List<NotaStore.Nota> lista = plugin.notas().de(nombre);
            quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Notas sobre "
                    + ChatColor.WHITE + nombre + ChatColor.GOLD + " (" + lista.size() + ") ══");
            if (lista.isEmpty()) {
                quien.sendMessage(Text.info("No hay ninguna."));
                return;
            }
            for (NotaStore.Nota n : lista) {
                quien.sendMessage(ChatColor.GRAY + " #" + n.id + ChatColor.DARK_GRAY + " "
                        + NotaStore.fecha(n.cuando) + ChatColor.GRAY + " por " + ChatColor.WHITE + n.autor
                        + ChatColor.YELLOW + ": " + n.texto);
            }
            return;
        }

        // Borrar
        if (args.length >= 3 && args[1].equalsIgnoreCase("borrar")) {
            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                quien.sendMessage(Text.error("El número de nota no vale."));
                return;
            }
            if (plugin.notas().borrar(nombre, id)) {
                quien.sendMessage(Text.exito("Nota #" + id + " borrada de " + nombre + "."));
                plugin.getLogger().info("[notas] " + quien.getName() + " borro la nota #" + id + " de " + nombre);
            } else {
                quien.sendMessage(Text.error("No hay ninguna nota #" + id + " de " + nombre + "."));
            }
            return;
        }

        // Dejar
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (texto.length() > 0) texto.append(' ');
            texto.append(args[i]);
        }
        if (texto.length() == 0) {
            quien.sendMessage(Text.error("Falta el texto de la nota."));
            return;
        }
        plugin.notas().anotar(nombre, quien.getName(), texto.toString());
        int total = plugin.notas().cantidad(nombre);
        quien.sendMessage(Text.exito("Nota guardada sobre " + nombre
                + ChatColor.GREEN + " (lleva " + total + ")."));
        plugin.getLogger().info("[notas] " + quien.getName() + " -> " + nombre + ": " + texto);
    }

    // ---------------- /staffvigilar ----------------

    /**
     *   /staffvigilar Rob <motivo>  -> lo pone en la lista
     *   /staffvigilar Rob off       -> lo saca
     *   /staffvigilar               -> lista los que hay
     */
    private void vigilar(CommandSender quien, String[] args) {
        if (args.length == 0) {
            List<VigilanciaStore.Marca> lista = plugin.vigilancia().todas();
            quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                    + "══ Lista de vigilancia (" + lista.size() + ") ══");
            if (lista.isEmpty()) {
                quien.sendMessage(Text.info("No hay nadie fichado."));
                return;
            }
            for (VigilanciaStore.Marca m : lista) {
                quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + m.jugador
                        + ChatColor.YELLOW + " · " + (m.motivo.isEmpty() ? "sin motivo" : m.motivo)
                        + ChatColor.DARK_GRAY + " (" + m.quien + ", " + VigilanciaStore.fecha(m.cuando) + ")");
            }
            return;
        }

        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("quitar"))) {
            VigilanciaStore.Marca m = plugin.vigilancia().quitar(nombre);
            if (m == null) {
                quien.sendMessage(Text.aviso(nombre + " no estaba en la lista."));
            } else {
                quien.sendMessage(Text.exito(nombre + " ya no está vigilado."));
                plugin.getLogger().info("[vigilancia] " + quien.getName() + " saca a " + nombre);
                if (online != null) online.sendMessage(Text.info("Ya no estás en la lista de vigilancia."));
            }
            return;
        }

        StringBuilder motivo = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (motivo.length() > 0) motivo.append(' ');
            motivo.append(args[i]);
        }
        if (motivo.length() == 0) {
            quien.sendMessage(Text.error("Falta el motivo: /staffvigilar <jugador> <motivo>"));
            return;
        }

        plugin.vigilancia().vigilar(nombre, motivo.toString(), quien.getName());
        quien.sendMessage(Text.exito(nombre + " está ahora en la lista de vigilancia."));
        quien.sendMessage(Text.info("A todo el staff conectado le llegará un aviso cuando entre."));
        plugin.getLogger().info("[vigilancia] " + quien.getName() + " -> " + nombre + ": " + motivo);
    }

    // ---------------- /staffchatlog ----------------

    /**
     * /staffchatlog
     *
     * Sin argumentos: lo que ha dicho el staff por /staffchat.
     * Con un jugador: lo que ha dicho ESE jugador.
     *
     *   /staffchatlog Rob              ultimos mensajes de Rob
     *   /staffchatlog Rob 60           solo de la ultima hora
     *   /staffchatlog Rob 60 una palabra  solo los que la contengan
     *
     * Ojo con el nombre del comando: /staffchat ya existe y es para hablar por
     * el canal de staff, asi que lo del chat de los jugadores va aqui, que es
     * donde ya estaba el historial del staff.
     */
    private void chatLog(CommandSender quien, String[] args) {
        if (args.length > 0 && !plugin.esStaff(quien)) {
            quien.sendMessage(Text.error("Eso no se mira sin ser staff."));
            return;
        }
        if (args.length == 0) {
            String filtro = "";
            List<StaffChatLog.Linea> lista = plugin.chatLog().buscar(filtro, 20);

            quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                    + "══ Historial del chat de staff " + ChatColor.DARK_GRAY
                    + "(" + plugin.chatLog().total() + " en total) ══");
            if (lista.isEmpty()) {
                quien.sendMessage(Text.info("Todavía no se ha dicho nada por /staffchat."));
                return;
            }
            for (StaffChatLog.Linea l : lista) {
                quien.sendMessage(ChatColor.GRAY + " " + StaffChatLog.fecha(l.cuando)
                        + ChatColor.GRAY + " " + ChatColor.WHITE + l.autor
                        + ChatColor.GRAY + ": " + l.texto);
            }
            quien.sendMessage(Text.info("Para ver lo que dice un jugador: /staffchatlog <jugador>"));
            return;
        }

        chatDeJugador(quien, args);
    }

    /** Lo que ha dicho un jugador en concreto. */
    private void chatDeJugador(CommandSender quien, String[] args) {
        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        int minutos = plugin.config().getInt("chat.ventana-por-defecto", 240);
        if (args.length > 1) minutos = enteroSeguro(args[1], minutos);
        minutos = Math.max(0, Math.min(minutos, 60 * 24 * 30));

        String palabra = "";
        if (args.length > 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(args[i]);
            }
            palabra = sb.toString();
        }

        List<ChatJugadoresLog.Linea> lista =
                plugin.chatJugadores().de(nombre, minutos, palabra, 40);

        String rango = minutos <= 0 ? "todo lo guardado"
                : (minutos >= 1440 ? "ultimas " + (minutos / 1440) + " dias"
                : "ultimos " + minutos + " min");
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Chat de "
                + ChatColor.WHITE + nombre + ChatColor.GOLD + " ══ " + ChatColor.DARK_GRAY + "(" + rango
                + (palabra.isEmpty() ? "" : ", con \"" + palabra + "\"") + ")");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info(online != null
                    ? "No ha dicho nada en ese rato."
                    : "No hay nada guardado de " + nombre + "."));
            quien.sendMessage(Text.info("Se guarda lo que dice la gente de "
                    + "verdad, hasta " + plugin.config().getInt("chat.dias-guardados", 14)
                    + " dias y " + plugin.config().getInt("chat.max-lineas-por-jugador", 300)
                    + " mensajes por jugador."));
            return;
        }
        for (ChatJugadoresLog.Linea l : lista) {
            quien.sendMessage(ChatColor.GRAY + " " + ChatJugadoresLog.fecha(l.cuando)
                    + ChatColor.GRAY + " " + ChatColor.WHITE + l.texto);
        }
        quien.sendMessage(Text.info(lista.size() + " mensajes."));
    }

    // ---------------- /staffunmute ----------------

    /**
     * Levanta un silencio.
     *
     * Antes esto no existia: /staffmute ponia el silencio y no habia manera de
     * quitarlo salvo editar silencios.yml a mano. Un silencio mal puesto se
     * comia el chat de alguien hasta que expiraba.
     */
    private void unmute(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffunmute <jugador>"));
            return;
        }
        String nombre = args[0];
        String ip = IpAddress.normalizar(nombre);
        if (ip != null) {
            MuteStore.Registro porIp = plugin.silencios().registroIp(ip);
            if (porIp == null || !plugin.silencios().silenciadoIp(ip)) {
                quien.sendMessage(Text.aviso("La IP " + ip + " no tiene un silencio activo."));
                return;
            }
            plugin.silencios().quitarIp(ip);
            long idIp = plugin.historial().ultimaDe("ipmute", ip);
            if (idIp > 0) plugin.historial().marcarLevantada(idIp, quien.getName());
            plugin.historial().guardar();
            quien.sendMessage(Text.exito("Silencio levantado para la IP " + ip + "."));
            avisar(quien, "silencio por IP levantado para " + ip);
            return;
        }
        Player online = plugin.getServer().getPlayerExact(nombre);
        if (online == null) online = jugadorQueEmpiezaPor(quien, nombre);

        java.util.UUID uuid;
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            org.bukkit.OfflinePlayer off = plugin.getServer().getOfflinePlayer(nombre);
            uuid = off.getUniqueId();
        }

        // El mismo filtro que el resto de sanciones. Sin esto, un staff sin
        // odissey.admin podia levantar el silencio que le habia puesto otro
        // admin: el menu de sanciones lo comprobaba, este comando no.
        if (!puedeTocar(quien, online, "levantar el silencio de")) return;

        MuteStore.Registro r = plugin.silencios().registro(uuid);
        if (r == null || !plugin.silencios().silenciado(uuid)) {
            quien.sendMessage(Text.aviso((online != null ? online.getName() : nombre) + " no está silenciado."));
            return;
        }

        plugin.silencios().quitar(uuid);
        long id = plugin.historial().ultimaDe("silencio", r.nombre.isEmpty() ? nombre : r.nombre);
        if (id > 0) plugin.historial().marcarLevantada(id, quien.getName());
        plugin.historial().guardar();

        quien.sendMessage(Text.exito("Silencio levantado a "
                + (r.nombre.isEmpty() ? nombre : r.nombre)
                + (r.motivo.isEmpty() ? "" : ChatColor.GRAY + " (motivo: " + r.motivo + ")")));
        if (online != null) {
            online.sendMessage(Text.exito("Ya no estás silenciado."));
        }
        avisar(quien, "silencio levantado a " + (r.nombre.isEmpty() ? nombre : r.nombre));
    }

    // ---------------- /staffhistory ----------------

    /** /staffhistory <jugador> o, sin argumentos, el historial del servidor. */
    private void history(CommandSender quien, String[] args) {
        if (args.length == 0) {
            List<SanctionStore.Entrada> ultimas = plugin.historial().recientes(10);
            quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                    + "══ Últimas sanciones del servidor ══");
            if (ultimas.isEmpty()) {
                quien.sendMessage(Text.info("Todavía no hay nada registrado."));
                return;
            }
            for (SanctionStore.Entrada e : ultimas) quien.sendMessage(e.linea());
            return;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("staff")) {
            String autor = args[1];
            List<SanctionStore.Entrada> lista = plugin.historial().porStaff(autor);
            quien.sendMessage(Text.marca() + ChatColor.GOLD + "══ Acciones de "
                    + ChatColor.WHITE + autor + ChatColor.GOLD + " (" + lista.size() + ") ══");
            if (lista.isEmpty()) {
                quien.sendMessage(Text.info("No hay acciones registradas para ese staff."));
                return;
            }
            int limite = Math.min(lista.size(), 15);
            for (int i = 0; i < limite; i++) quien.sendMessage(lista.get(i).linea());
            if (lista.size() > limite) quien.sendMessage(Text.info("… y " + (lista.size() - limite) + " más."));
            return;
        }

        String nombre = args[0];
        Player online = jugador(quien, nombre);
        if (online != null) nombre = online.getName();

        List<SanctionStore.Entrada> lista = plugin.historial().de(nombre);
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD + "══ Historial de "
                + ChatColor.WHITE + nombre + ChatColor.GOLD + " (" + lista.size() + ") ══");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info("No tiene ninguna sanción registrada."));
            return;
        }
        int limite = Math.min(lista.size(), 15);
        for (int i = 0; i < limite; i++) quien.sendMessage(lista.get(i).linea());
        if (lista.size() > limite) {
            quien.sendMessage(Text.info("… y " + (lista.size() - limite) + " más."));
        }
    }

    // ---------------- /stafffrozen ----------------

    /** Quien está congelado ahora mismo. */
    private void frozen(CommandSender quien) {
        List<Player> lista = plugin.gestor().congelados();
        quien.sendMessage(Text.marca() + ChatColor.DARK_GRAY + " " + ChatColor.GOLD
                + "══ Congelados ahora (" + lista.size() + ") ══");
        if (lista.isEmpty()) {
            quien.sendMessage(Text.info("No hay nadie congelado."));
            return;
        }
        for (Player p : lista) {
            StaffManager.Estado e = plugin.gestor().estado(p);
            quien.sendMessage(ChatColor.GRAY + " · " + ChatColor.WHITE + p.getName()
                    + ChatColor.DARK_GRAY + " desde " + Fechas.diaHora(e.congeladoDesde)
                    + (e.congeladoPor.isEmpty() ? "" : ChatColor.DARK_GRAY + " por " + e.congeladoPor)
                    + (e.congeladoMotivo.isEmpty() ? "" : ChatColor.YELLOW + " · " + e.congeladoMotivo));
        }
    }

    // ---------------- /staffrespawn ----------------

    /** Mata a un jugador para que reaparezca. Para los que se quedan atascados. */
    private void respawn(CommandSender quien, String[] args) {
        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /staffrespawn <jugador>"));
            return;
        }
        Player objetivo = jugador(quien, args[0]);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, args[0]);
        if (objetivo == null) {
            quien.sendMessage(Text.error("Ese jugador no está conectado."));
            return;
        }
        if (objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No te puedes matar a ti mismo."));
            return;
        }
        if (objetivo.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            quien.sendMessage(Text.error("Está en otra dimensión, no se le puede matar ahí."));
            return;
        }
        if (plugin.gestor().congelado(objetivo)) {
            quien.sendMessage(Text.error("Está congelado. Suéltalo antes (/freeze)."));
            return;
        }
        objetivo.spigot().respawn();
        objetivo.sendMessage(Text.info("Un miembro del staff te ha reaparecido."));
        quien.sendMessage(Text.exito("Has reaparecido a " + objetivo.getName() + "."));
        avisar(quien, "respawn a " + objetivo.getName());
    }

    // ---------------- /staffspectate ----------------

    /** Ver a alguien sin que te vea y sin poder tocar nada. */
    private void spectate(CommandSender quien, String[] args) {
        if (!soloJugador(quien)) return;
        Player staff = (Player) quien;

        // Este comando ahora es un atajo del espiado. Antes hacia su propio modo
        // espectador, SIN registro: el staff veia a alguien pero no le llegaba su
        // chat y el panel no lo refrescaba. Dos sistemas para lo mismo, y el
        // staff acababa usando el que le salia primero.
        if (args.length == 0 || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("salir")) {
            if (!plugin.espia().estaEspiando(staff.getUniqueId())
                    && !plugin.gestor().esEspectador(staff)) {
                quien.sendMessage(Text.aviso("No estás en modo espectador."));
                return;
            }
            plugin.menuEspiar().salir(staff, false);
            return;
        }

        Player objetivo = jugador(quien, args[0]);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, args[0]);
        if (objetivo == null) {
            quien.sendMessage(Text.error("Ese jugador no está conectado."));
            return;
        }
        if (objetivo.equals(staff)) {
            quien.sendMessage(Text.error("No puedes verte a ti mismo."));
            return;
        }
        plugin.menuEspiar().entrar(staff, objetivo);
    }

    private void avisar(CommandSender quien, String texto) {
        if (!plugin.config().getBoolean("avisos.avisar-en-consola", true)) return;
        plugin.getLogger().info("[staff] " + quien.getName() + ": " + texto);
    }
}
