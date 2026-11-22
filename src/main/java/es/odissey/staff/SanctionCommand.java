package es.odissey.staff;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /staffwarn, /staffmute, /staffban y /staffkick.
 *
 * Los silencios se guardan en un archivo propio y no en el de Minecraft, para
 * que un ban temporal se acabe solo aunque el servidor reinicie, y para poder
 * avisar al staff cuando se acaba.
 */
public final class SanctionCommand extends BaseCommand {

    private static final Pattern DURACION = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    public SanctionCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        String sub = comando.getName().toLowerCase(java.util.Locale.ROOT);
        String[] partes = sub.split("staff");
        String accion = sub.equals("ghostmute") ? "ghostmute"
                : sub.equals("tempban") ? "ban"
                : sub.equals("tempmute") ? "mute"
                : (partes.length > 1 ? partes[1] : "");
        boolean temporalObligatorio = sub.equals("tempban") || sub.equals("tempmute");

        if ("ban".equals(accion) && args.length == 0 && quien instanceof Player staff) {
            plugin.banDialogos().abrirLista(staff);
            return;
        }

        if ("ghostmute".equals(accion) && !plugin.config().getBoolean("ghost-mute.enabled", true)) {
            quien.sendMessage(Text.error("Ghost mute está desactivado en la configuración."));
            return;
        }

        if (args.length < 2) {
            quien.sendMessage(Text.error("Uso: /" + sub + " <jugador> <motivo>"));
            return;
        }
        String nombreObjetivo = args[0];
        Player objetivo = jugador(quien, nombreObjetivo);
        if (objetivo == null) objetivo = jugadorQueEmpiezaPor(quien, nombreObjetivo);
        if (objetivo == null && !"kick".equals(accion) && !"ban".equals(accion)) {
            quien.sendMessage(Text.error("Ese jugador no está conectado. Para un ban temporal o permanente usa /staffban aunque no esté dentro."));
            return;
        }

        StringBuilder motivo = new StringBuilder();
        Duration duracion = null;
        boolean permanente = false;

        // Un tiempo al principio del motivo: /staffban Rob 7d Robar bases
        //
        // Solo se mira en ban y mute, que son los que tienen duracion. En warn
        // y kick no hay tiempo que poner, asi que si no se filtra aqui "5m" se
        // comia parte del motivo: /staffwarn Rob 5m Robando deaba de decir
        // "Robando" y decia "5m Robando".
        boolean admiteTiempo = "ban".equals(accion) || "mute".equals(accion) || "ghostmute".equals(accion);
        int desde = 1;
        if (admiteTiempo && (args[1].equalsIgnoreCase("perm") || args[1].equalsIgnoreCase("permanente"))) {
            permanente = true;
            desde = 2;
        } else if (admiteTiempo) {
            Duration d = leerDuracion(args[1]);
            if (d != null) {
                duracion = d;
                desde = 2;
            }
        }

        if (temporalObligatorio && (duracion == null || permanente)) {
            quien.sendMessage(Text.error("/" + sub + " requiere una duración válida, por ejemplo 1h o 7d."));
            return;
        }

        // Un ban permanente no tiene vuelta atras, asi que se pide confirmarlo
        // escribiendo PERM al final. El motivo se cuenta con la palabra quitada,
        // para que no acabe en el historial el "PERM" de confirmacion.
        if (permanente && !"PERM".equalsIgnoreCase(args[args.length - 1])) {
            StringBuilder motivoSinPerm = new StringBuilder();
            for (int i = desde; i < args.length; i++) {
                if (motivoSinPerm.length() > 0) motivoSinPerm.append(' ');
                motivoSinPerm.append(args[i]);
            }
            quien.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "Ban permanente.");
            quien.sendMessage(Text.info("No hay vuelta atrás: escribe el comando otra vez añadiendo PERM al final."));
            quien.sendMessage(Text.aviso("/staffban " + args[0] + " perm " + motivoSinPerm + " PERM"));
            return;
        }
        // El PERM del final es solo la confirmacion: el motivo va desde "desde"
        // hasta uno antes del final.
        int finMotivo = args.length;
        if (permanente) finMotivo = args.length - 1;
        if (desde >= finMotivo) {
            quien.sendMessage(Text.error("Falta el motivo."));
            return;
        }
        for (int i = desde; i < finMotivo; i++) {
            if (motivo.length() > 0) motivo.append(' ');
            motivo.append(args[i]);
        }
        String textoMotivo = motivo.toString();
        if (textoMotivo.isEmpty()) {
            quien.sendMessage(Text.error("Falta el motivo."));
            return;
        }

        if (objetivo != null && objetivo.equals(quien)) {
            quien.sendMessage(Text.error("No puedes aplicártelo a ti mismo."));
            return;
        }
        // No se toca a otro staff sin odissey.admin, y nunca a un admin.
        String verbo = switch (accion) {
            case "warn" -> "mandar un aviso";
            case "mute" -> "silenciar";
            case "ghostmute" -> "silenciar";
            case "ban" -> "banear";
            default -> "expulsar";
        };
        if (!puedeTocar(quien, objetivo, verbo)) return;
        if ("ban".equals(accion) && !ModerationLimits.puedeBanear(plugin, quien, permanente)) return;

        switch (accion) {
            case "warn":
                aplicarAviso(quien, objetivo, textoMotivo);
                break;
            case "mute":
                aplicarSilencio(quien, objetivo, textoMotivo, duracion, false);
                break;
            case "ghostmute":
                aplicarSilencio(quien, objetivo, textoMotivo, duracion, true);
                break;
            case "ban":
                aplicarBan(quien, objetivo, nombreObjetivo, textoMotivo, duracion, permanente);
                break;
            case "kick":
                aplicarKick(quien, objetivo, nombreObjetivo, textoMotivo);
                break;
            default:
                quien.sendMessage(Text.error("Acción desconocida: " + accion));
        }
    }

    /** Revalida y aplica el formulario de diálogo con los mismos límites del comando. */
    public boolean permitirObjetivoDialogo(Player staff, Player objetivo, boolean eraStaff, boolean eraAdmin) {
        if (objetivo.equals(staff)) {
            staff.sendMessage(Text.error("No puedes aplicártelo a ti mismo."));
            return false;
        }
        if (eraAdmin || (eraStaff && !staff.hasPermission("odissey.admin"))) {
            staff.sendMessage(Text.error("No tienes permiso para banear a ese jugador."));
            return false;
        }
        return puedeTocar(staff, objetivo, "banear");
    }

    public void banearDesdeDialogo(Player staff, java.util.UUID uuid, String nombre,
                                   boolean eraStaff, boolean eraAdmin, String textoDuracion,
                                   String unidad, String motivo) {
        Player objetivo = Bukkit.getPlayer(uuid);
        if (objetivo == null || !objetivo.getName().equalsIgnoreCase(nombre)) {
            staff.sendMessage(Text.error("Ese jugador ya no está conectado. Abre /ban de nuevo."));
            return;
        }
        if (!permitirObjetivoDialogo(staff, objetivo, eraStaff || plugin.esStaff(objetivo),
                eraAdmin || objetivo.hasPermission("odissey.admin"))) return;
        long cantidad;
        try { cantidad = Long.parseLong(textoDuracion == null ? "" : textoDuracion.trim()); }
        catch (NumberFormatException ex) { staff.sendMessage(Text.error("Escribe una duración numérica válida.")); return; }
        if (cantidad < 1 || cantidad > 3650) {
            staff.sendMessage(Text.error("La duración debe estar entre 1 y 3650 unidades."));
            return;
        }
        String sufijo = switch (unidad == null ? "" : unidad) {
            case "m" -> "m"; case "h" -> "h"; case "d" -> "d";
            default -> "";
        };
        Duration duracion = sufijo.isEmpty() ? null : leerDuracion(cantidad + sufijo);
        if (duracion == null) { staff.sendMessage(Text.error("Selecciona minutos, horas o días.")); return; }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) { staff.sendMessage(Text.error("Escribe el motivo del ban.")); return; }
        if (!ModerationLimits.puedeBanear(plugin, staff, false)) return;
        aplicarBan(staff, objetivo, nombre, limpio, duracion, false);
    }

    private Duration leerDuracion(String texto) {
        Matcher m = DURACION.matcher(texto.trim());
        if (!m.matches()) return null;
        // "99999999999999999999d" encaja con el patron pero revienta al parsear
        // (o al multiplicar por 7 en "w"). Antes salia un NumberFormatException
        // y el comando acababa en "Ese comando ha fallado".
        long n;
        try {
            n = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        try {
            switch (m.group(2).toLowerCase(java.util.Locale.ROOT)) {
                case "s": return Duration.ofSeconds(n);
                case "m": return Duration.ofMinutes(n);
                case "h": return Duration.ofHours(n);
                case "d": return Duration.ofDays(n);
                case "w": return Duration.ofDays(Math.multiplyExact(n, 7));
                default: return null;
            }
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private String textoMotivo(CommandSender quien, String motivo, Duration duracion, boolean permanente) {
        StringBuilder sb = new StringBuilder(motivo);
        sb.append(" · por ").append(quien.getName());
        if (permanente) sb.append(" · permanente");
        else if (duracion != null) sb.append(" · ").append(legible(duracion));
        return sb.toString();
    }

    private String legible(Duration d) {
        long s = d.getSeconds();
        if (s % 86400 == 0) return (s / 86400) + "d";
        if (s % 3600 == 0) return (s / 3600) + "h";
        if (s % 60 == 0) return (s / 60) + "m";
        return s + "s";
    }

    private void aplicarAviso(CommandSender quien, Player objetivo, String motivo) {
        objetivo.sendMessage(Text.aviso("Has recibido un aviso de un miembro del staff."));
        objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
        objetivo.playSound(objetivo.getLocation(),
                org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);

        // WarnStore guarda el aviso ANTES de avisar por el chat, para que el
        // numero que sale en el mensaje sea el bueno y no el de hace un segundo.
        WarnStore.Aviso w = null;
        String automatico = "";
        if (plugin.warns().activo()) {
            int previos = plugin.warns().contar(objetivo.getName());
            w = plugin.warns().anotar(objetivo.getName(), objetivo.getUniqueId(),
                    quien.getName(), motivo);
            automatico = plugin.warns().aplicar(quien instanceof Player ? (Player) quien : null,
                    objetivo, previos);
        }

        quien.sendMessage(Text.exito("Aviso enviado a " + objetivo.getName() + "."));
        if (w != null) {
            int total = plugin.warns().contar(objetivo.getName());
            quien.sendMessage(Text.info("Avisos que lleva: " + ChatColor.WHITE + total
                    + ChatColor.GRAY + " (vivos; caducan a los "
                    + plugin.config().getInt("warns.caducan-en-dias", 90) + " días)"));
            WarnStore.Nivel siguiente = plugin.warns().siguienteNivel(objetivo.getName());
            if (siguiente != null) {
                quien.sendMessage(Text.aviso("Le faltan " + ChatColor.WHITE
                        + (siguiente.avisos - total) + ChatColor.YELLOW
                        + " para " + siguiente.accion + " automático."));
            } else if (!plugin.warns().niveles().isEmpty()) {
                quien.sendMessage(Text.info("Ya no hay más escalones: a partir de aquí decides tú."));
            }
        }
        if (!automatico.isEmpty()) {
            quien.sendMessage(Text.prefijo() + ChatColor.RED + "" + ChatColor.BOLD + "Automático: "
                    + ChatColor.RESET + automatico + ChatColor.GRAY
                    + " (le has dado el aviso número "
                    + plugin.warns().contar(objetivo.getName()) + ")");
        }
        plugin.historial().anotar("aviso", objetivo.getName(), "", quien.getName(), motivo,
                automatico.isEmpty() ? "" : "disparo automatico: " + automatico);
        plugin.historial().guardar();
        avisarStaff(quien, objetivo, "un aviso");
        avisoStaff(quien, "aviso a " + objetivo.getName() + ": " + motivo);
    }

    private void aplicarSilencio(CommandSender quien, Player objetivo, String motivo, Duration duracion, boolean fantasma) {
        if (objetivo == null) return;
        long hasta = duracion == null ? Long.MAX_VALUE : System.currentTimeMillis() + duracion.toMillis();
        plugin.silencios().poner(objetivo.getUniqueId(), objetivo.getName(), textoMotivo(quien, motivo, duracion, false), hasta, fantasma);
        if (!fantasma) {
            objetivo.sendMessage(Text.aviso("Has sido silenciado por un miembro del staff."));
            objetivo.sendMessage(Text.info("Motivo: " + ChatColor.WHITE + motivo));
            if (duracion != null) {
                objetivo.sendMessage(Text.info("Se acaba en: " + legible(duracion) + "."));
            } else {
                objetivo.sendMessage(Text.info("Es permanente: un staff tiene que levantarlo con /staffunmute."));
            }
        }
        quien.sendMessage(Text.exito(objetivo.getName() + (fantasma ? " silenciado en modo fantasma." : " silenciado.")));
        plugin.historial().anotar("silencio", objetivo.getName(), "", quien.getName(), motivo,
                duracion == null ? "permanente" : legible(duracion));
        plugin.historial().guardar();
        avisarStaff(quien, objetivo, "un silencio");
        avisoStaff(quien, "silencio a " + objetivo.getName() + ": " + motivo);
        if (fantasma) {
            plugin.avisarEquipo(ChatColor.RED + objetivo.getName() + ChatColor.YELLOW + " recibió ghost mute: " + motivo);
        } else {
            plugin.anunciarSancion("mute", objetivo.getName(), quien.getName(), motivo,
                    duracion == null ? "permanente" : legible(duracion));
        }
    }

    private void aplicarBan(CommandSender quien, Player objetivo, String nombreObjetivo,
                             String motivo, Duration duracion, boolean permanente) {
        String texto = textoMotivo(quien, motivo, duracion, permanente);
        // Sin tiempo escrito, un ban seava de 7 dias. Antes el mensaje usaba
        // legible(duracion) con duracion == null y petaba con un
        // NullPointerException DESPUES de haber baneado: el ban se ponia y el
        // staff se comia un "Ese comando ha fallado: null" sin explicacion.
        Duration efectiva = duracion != null ? duracion : Duration.ofDays(7);
        java.util.Date fecha = permanente ? null
                : new java.util.Date(System.currentTimeMillis() + efectiva.toMillis());
        String quien3 = quien.getName();
        try {
            Bukkit.getBanList(BanList.Type.NAME).addBan(nombreObjetivo, texto, fecha, quien3);
        } catch (Exception e) {
            quien.sendMessage(Text.error("No se pudo aplicar el ban: " + e.getMessage()));
            return;
        }
        if (objetivo != null && plugin.config().getBoolean("evidencias.capturar-al-banear", true)) {
            plugin.evidencias().capturarInventario(objetivo, quien.getName());
        }
        if (objetivo != null) {
            String duracionPantalla = permanente ? "Permanente" : legible(efectiva);
            String mensajeKick = mensajeDesconexionBan(nombreObjetivo, motivo, duracionPantalla, quien3);
            if (plugin.config().getBoolean("proxy-kick.enabled", true)) {
                try {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    java.io.DataOutputStream salida = new java.io.DataOutputStream(bytes);
                    salida.writeUTF("KickPlayer");
                    salida.writeUTF(objetivo.getName());
                    salida.writeUTF(mensajeKick);
                    objetivo.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                } catch (java.io.IOException imposible) {
                    plugin.getLogger().warning("No se pudo preparar el kick en el proxy para " + objetivo.getName());
                }
            }
            objetivo.kickPlayer(mensajeKick);
        }
        quien.sendMessage(Text.exito(nombreObjetivo + " baneado "
                + (permanente ? "permanentemente" : legible(efectiva)) + "."));
        long idHistorial = plugin.historial().anotar("baneo", nombreObjetivo, "", quien.getName(), motivo,
                permanente ? "permanente" : (duracion == null ? "7d" : legible(duracion)));
        plugin.historial().guardar();
        java.util.UUID uuidEncontrado = objetivo != null ? objetivo.getUniqueId() : plugin.conexiones().uuidDe(nombreObjetivo);
        plugin.webBans().banear(idHistorial, nombreObjetivo, uuidEncontrado == null ? null : uuidEncontrado.toString(), motivo, quien3,
                System.currentTimeMillis(), permanente ? null : fecha.getTime());
        if (objetivo != null) avisarStaff(quien, objetivo, "un baneo");
        avisoStaff(quien, "ban a " + nombreObjetivo + ": " + motivo);
        plugin.anunciarSancion("ban", nombreObjetivo, quien.getName(), motivo,
                permanente ? "permanente" : legible(efectiva));
    }

    private String mensajeDesconexionBan(String jugador, String motivo, String duracion, String staff) {
        java.util.List<String> lineas = plugin.config().getStringList("mensajes.desconexion-baneo");
        if (lineas.isEmpty()) {
            lineas = java.util.List.of(
                    "&cHas sido baneado de %servidor%",
                    "",
                    "&6Motivo: &f%motivo%",
                    "&6Duración: &f%duracion%",
                    "&6Staff: &f%staff%"
            );
        }
        String servidor = plugin.config().getString("mensajes.servidor", "Odissey");
        java.util.List<String> formateadas = new java.util.ArrayList<>(lineas.size());
        for (String linea : lineas) {
            String texto = Text.con(linea,
                    "%jugador%", jugador,
                    "%servidor%", servidor,
                    "%motivo%", motivo,
                    "%duracion%", duracion,
                    "%tiempo%", duracion,
                    "%staff%", staff);
            formateadas.add(Text.c(texto));
        }
        return String.join("\n", formateadas);
    }

    private void aplicarKick(CommandSender quien, Player objetivo, String nombreObjetivo, String motivo) {
        if (objetivo == null) {
            quien.sendMessage(Text.error("Ese jugador no está conectado."));
            return;
        }
        objetivo.kickPlayer(ChatColor.RED + "Has sido expulsado: " + motivo);
        quien.sendMessage(Text.exito(nombreObjetivo + " expulsado."));
        plugin.historial().anotar("expulsion", objetivo.getName(), "", quien.getName(), motivo, "");
        plugin.historial().guardar();
        avisarStaff(quien, objetivo, "una expulsion");
        avisoStaff(quien, "kick a " + nombreObjetivo + ": " + motivo);
    }

    /** Todo lo que hace un staff queda anotado para el resto del equipo. */
    private void avisoStaff(CommandSender quien, String texto) {
        // La clave se leia de "avisos.avisar-en-consola", que no existe en
        // config.yml: salia siempre el valor por defecto y lo que el admin
        // cambiaba en el fichero no hacia nada.
        if (!plugin.config().getBoolean("avisos.avisar-en-consola", true)) return;
        plugin.getLogger().info("[staff] " + quien.getName() + ": " + texto);
        for (org.bukkit.entity.Player otro : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.esStaff(otro)) continue;
            otro.sendMessage(Text.prefijo() + ChatColor.GRAY + "[staff] "
                    + ChatColor.WHITE + quien.getName() + " " + texto);
        }
    }


}
