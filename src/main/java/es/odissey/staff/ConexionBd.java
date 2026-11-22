package es.odissey.staff;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * La conexión del plugin con la base de datos.
 *
 * Una sola para todas las tiendas, no una por store: abrir una conexión por
 * archivo seria hacer doce handshakes a MySQL cada vez que se guardara
 * cualquier cosa, y con SQLite abrir y cerrar el fichero sin parar es
 * precisamente lo que lo deja corrupto.
 */
public final class ConexionBd {

    private final OdisseyStaff plugin;
    private final String tipo;
    private final File sqliteFile;
    private final String host;
    private final int puerto;
    private final String basedatos;
    private final String usuario;
    private final String clave;
    private Connection conexion;

    public ConexionBd(OdisseyStaff plugin, String tipo, File sqliteFile, String host, int puerto,
                      String basedatos, String usuario, String clave) {
        this.plugin = plugin;
        this.tipo = tipo;
        this.sqliteFile = sqliteFile;
        this.host = host;
        this.puerto = puerto;
        this.basedatos = basedatos;
        this.usuario = usuario;
        this.clave = clave;
    }

    public String tipo() {
        return tipo;
    }

    /** El archivo SQLite, o null si no se esta usando SQLite. */
    public File sqliteFile() {
        return "sqlite".equals(tipo) ? sqliteFile : null;
    }

    /** Abre la conexión y crea la tabla si hace falta. Lanza si no puede. */
    public void iniciar() throws SQLException {
        if ("sqlite".equals(tipo) || "h2".equals(tipo)) {
            File padre = sqliteFile.getParentFile();
            if (padre != null && !padre.exists() && !padre.mkdirs()) {
                throw new SQLException("No se pudo crear la carpeta del archivo de base de datos");
            }
        }
        try {
            String driver = switch (tipo) {
                case "sqlite" -> "org.sqlite.JDBC";
                case "postgresql" -> "org.postgresql.Driver";
                case "h2" -> "org.h2.Driver";
                case "mariadb" -> "org.mariadb.jdbc.Driver";
                default -> "com.mysql.cj.jdbc.Driver";
            };
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("El driver JDBC de " + tipo + " no esta disponible", e);
        }
        Connection c = abrir();
        try (Statement s = c.createStatement()) {
            // LONGTEXT y no TEXT: el historial de chat de un jugador son cientos
            // de lineas y en MySQL un TEXT se corta a 64 KB, que es justo por
            // donde se perdia.
            s.executeUpdate("CREATE TABLE IF NOT EXISTS odissey_datos ("
                    + "tienda VARCHAR(64) NOT NULL, "
                    + "clave VARCHAR(191) NOT NULL, "
                    + "valor " + (("mysql".equals(tipo) || "mariadb".equals(tipo)) ? "LONGTEXT" : "TEXT") + " NOT NULL, "
                    + "PRIMARY KEY (tienda, clave))");
        } catch (SQLException e) {
            invalidar();
            throw new SQLException("No se pudo preparar la tabla de datos", e);
        }
        plugin.getLogger().info("[bd] conectado a " + tipo + ".");
    }

    /** Devuelve la conexión viva, reconectando si se murio. */
    public Connection abrir() throws SQLException {
        if (conexion != null) {
            try {
                if (conexion.isValid(2)) return conexion;
            } catch (SQLException ignorada) {
                // se reconecta abajo
            }
            invalidar();
        }
        if ("sqlite".equals(tipo)) {
            conexion = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        } else if ("h2".equals(tipo)) {
            String ruta = sqliteFile.getAbsolutePath().replace('\\', '/');
            conexion = DriverManager.getConnection("jdbc:h2:file:" + ruta
                    + ";DB_CLOSE_ON_EXIT=FALSE", usuario, clave);
        } else if ("postgresql".equals(tipo)) {
            String url = "jdbc:postgresql://" + host + ":" + puerto + "/" + basedatos;
            conexion = DriverManager.getConnection(url, usuario, clave);
        } else {
            String esquema = "mariadb".equals(tipo) ? "mariadb" : "mysql";
            String url = "jdbc:" + esquema + "://" + host + ":" + puerto + "/" + basedatos
                    + ("mysql".equals(esquema) ? "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8" : "");
            conexion = DriverManager.getConnection(url, usuario, clave);
        }
        return conexion;
    }

    public void invalidar() {
        if (conexion == null) return;
        try {
            conexion.close();
        } catch (SQLException ignorada) {
        }
        conexion = null;
    }

    public void cerrar() {
        invalidar();
    }
}
