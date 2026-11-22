package es.odissey.staff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /staffsay y /staffactionbar: mensajes grandes en pantalla para todo el
 * servidor.
 *
 * Para lo tipico de "estais todos baneados por exploit, parad de escribir", que
 * con un mensaje en el chat normal se pierde entre veinte lineas de gente
 * hablando y nadie lo lee.
 *
 * Con un numero delante el mensaje se repite cada esos segundos, para avisos
 * largos de evento o de mantenimiento: /staffsay 10 El evento empieza.
 */
public final class StaffSayCommand extends BaseCommand {

    // El mismo objeto atiende a /staffsay y a /staffactionbar, asi que la tarea
    // de repeticion no puede ser un unico campo: antes, parar uno ("/staffsay
    // off") cancelaba en silencio la repeticion del otro, y el que la habia
    // puesto creia que su aviso seguia saliendo.
    private final java.util.Map<String, Integer> repeticiones = new java.util.HashMap<>();

    public StaffSayCommand(OdisseyStaff plugin) {
        super(plugin, "odissey.staff");
    }

    @Override
    protected void ejecutar(CommandSender quien, Command comando, String[] args) {
        boolean barra = comando.getName().equalsIgnoreCase("staffactionbar");
        String etiqueta = barra ? "staffactionbar" : "staffsay";

        if (args.length == 0) {
            quien.sendMessage(Text.error("Uso: /" + etiqueta + " [segundos] <mensaje>"));
            quien.sendMessage(Text.info("Con segundos delante se repite cada esos segundos. "
                    + "/" + etiqueta + " off para parar."));
            return;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("cerrar"))) {
            detenerRepeticion(etiqueta);
            quien.sendMessage(Text.exito("Mensaje global apagado."));
            return;
        }

        int desde = 0;
        int cada = 0;
        if (esNumero(args[0]) && args.length > 1) {
            // esNumero() acepta cualquier cadena de digitos, y un numero que no
            // cabe en un int (por ejemplo 99999999999) hacia reventar el
            // comando entero con un NumberFormatException.
            try {
                cada = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                cada = 0;
            }
            desde = 1;
            if (cada < 1 || cada > 3600) cada = 0;
        }

        StringBuilder texto = new StringBuilder();
        for (int i = desde; i < args.length; i++) {
            if (texto.length() > 0) texto.append(' ');
            texto.append(args[i]);
        }
        if (texto.length() == 0) {
            quien.sendMessage(Text.error("Falta el mensaje."));
            return;
        }

        String mensaje = ChatColor.translateAlternateColorCodes('&', texto.toString());
        mostrar(barra, mensaje);
        quien.sendMessage(Text.exito("Mensaje enviado a todos: " + ChatColor.WHITE + texto));

        if (cada > 0) {
            iniciarRepeticion(etiqueta, barra, mensaje, cada);
            quien.sendMessage(Text.info("Se repitirá cada " + cada + "s hasta que pongas /" + etiqueta + " off."));
        } else {
            detenerRepeticion(etiqueta);
        }
    }

    private void mostrar(boolean barra, String mensaje) {
        if (barra) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.gestor().vanish(p)) continue;
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(mensaje));
            }
        } else {
            // Fuera del bucle: broadcastMessage manda a TODO el servidor, si se
            // llamara una vez por jugador saldría el texto repetido N veces.
            Bukkit.broadcastMessage(mensaje);
        }
    }

    private void iniciarRepeticion(String etiqueta, boolean barra, String mensaje, int cada) {
        detenerRepeticion(etiqueta);
        final boolean b = barra;
        final String m = mensaje;
        repeticiones.put(etiqueta, Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> mostrar(b, m), cada * 20L, cada * 20L).getTaskId());
    }

    private void detenerRepeticion(String etiqueta) {
        Integer tarea = repeticiones.remove(etiqueta);
        if (tarea == null) return;
        Bukkit.getScheduler().cancelTask(tarea);
    }

    /** Al apagar el plugin no se deja un bucle corriendo. */
    public void apagarTodo() {
        for (Integer tarea : new java.util.ArrayList<>(repeticiones.values())) {
            if (tarea != null) Bukkit.getScheduler().cancelTask(tarea);
        }
        repeticiones.clear();
    }
}
