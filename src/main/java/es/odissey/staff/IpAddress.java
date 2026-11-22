package es.odissey.staff;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Validación numérica sin aceptar dominios ni provocar búsquedas DNS. */
public final class IpAddress {
    private IpAddress() { }

    public static boolean esIp(String valor) { return normalizar(valor) != null; }

    public static String normalizar(String valor) {
        if (valor == null || valor.isBlank() || !valor.matches("[0-9a-fA-F:.]+")) return null;
        if (!valor.contains(".") && !valor.contains(":")) return null;
        try {
            InetAddress direccion = InetAddress.getByName(valor);
            // InetAddress solo acepta IPv4/IPv6 literal por el filtro anterior.
            return direccion.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
