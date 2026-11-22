package es.odissey.staff;

import java.sql.SQLException;

/**
 * Fallo de la base de datos al leer o guardar un store.
 *
 * Sin checked a proposito: si leer() y escribir() declararan SQLException, los
 * doce stores tendrian que rodearlos de try/catch para poder compilar, y el
 * unico que de verdad tiene que manejar el fallo de la base es el almacen, que
 * es quien puede caerse de vuelta a los archivos .yml.
 */
public final class ErrorAlmacen extends RuntimeException {

    private final String tienda;

    public ErrorAlmacen(String tienda, SQLException causa) {
        super("[" + tienda + "] " + causa.getMessage(), causa);
        this.tienda = tienda;
    }

    public String tienda() {
        return tienda;
    }
}
