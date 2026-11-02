package es.florida.vet.integracion;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base de datos MySQL SOLO para las pruebas de integración: "vetstock_test".
 * Se crea y se recarga con el mismo script que usa la aplicación
 * (database/migrations/001_esquema_inicial.sql), así las pruebas comprueban
 * el esquema real y nunca tocan la base de datos "vetstock" de la demo.
 *
 * Variables opcionales: TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD.
 */
final class BaseDatosPruebas {

    static final String URL = System.getenv().getOrDefault("TEST_DB_URL",
            "jdbc:mysql://localhost:3306/vetstock_test?createDatabaseIfNotExist=true&useSSL=false"
                    + "&allowPublicKeyRetrieval=true&serverTimezone=Europe/Madrid&connectTimeout=3000");
    static final String USUARIO = System.getenv().getOrDefault("TEST_DB_USER", "root");
    static final String CLAVE = System.getenv().getOrDefault("TEST_DB_PASSWORD", "root");

    private static final Path SCRIPT = Path.of("..", "database", "migrations", "001_esquema_inicial.sql");

    private BaseDatosPruebas() {
    }

    static boolean disponible() {
        try (Connection ignored = DriverManager.getConnection(URL, USUARIO, CLAVE)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Borra y vuelve a crear todas las tablas con los datos ficticios iniciales. */
    static void recargar() {
        try (Connection conexion = DriverManager.getConnection(URL, USUARIO, CLAVE)) {
            String sql = Files.readString(SCRIPT, StandardCharsets.UTF_8)
                    .replace("CREATE DATABASE IF NOT EXISTS vetstock", "CREATE DATABASE IF NOT EXISTS vetstock_test")
                    .replace("USE vetstock;", "USE vetstock_test;");
            ScriptUtils.executeSqlScript(conexion,
                    new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("No se pudo preparar la base de datos de pruebas", e);
        }
    }
}
