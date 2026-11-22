package es.odissey.staff;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos de fecha compartidos.
 *
 * Antes cada clase tenia su propio SimpleDateFormat estatico. SimpleDateFormat
 * NO es seguro entre hilos: el chat de Bukkit corre aparte del hilo principal y
 * el antid-spam escribe en la consola desde ahi, asi que dos hilos acababan
 * peleandose por el mismo objeto y salian fechas en chino o directamente una
 * excepcion.
 *
 * DateTimeFormatter es inmutable y se puede compartir sin ningun problema.
 */
public final class Fechas {

    private Fechas() {
    }

    /** "dd/MM HH:mm" */
    public static final DateTimeFormatter DIA_HORA =
            patron("dd/MM HH:mm");

    /** "dd/MM HH:mm:ss" */
    public static final DateTimeFormatter DIA_HORA_SEG =
            patron("dd/MM HH:mm:ss");

    /** "dd/MM/yyyy HH:mm" */
    public static final DateTimeFormatter DIA_ANIO_HORA =
            patron("dd/MM/yyyy HH:mm");

    /** "HH:mm:ss" */
    public static final DateTimeFormatter HORA_SEG =
            patron("HH:mm:ss");

    /** "yyyy-MM-dd_HH-mm-ss" */
    public static final DateTimeFormatter SELLO =
            patron("yyyy-MM-dd_HH-mm-ss");

    private static DateTimeFormatter patron(String p) {
        return DateTimeFormatter.ofPattern(p, Locale.ROOT).withZone(ZoneId.systemDefault());
    }

    /** Da formato a un instante en milisegundos. */
    public static String de(long ms, DateTimeFormatter formato) {
        return formato.format(Instant.ofEpochMilli(ms));
    }

    /** "dd/MM HH:mm" de un instante en milisegundos. */
    public static String diaHora(long ms) {
        return de(ms, DIA_HORA);
    }
}
