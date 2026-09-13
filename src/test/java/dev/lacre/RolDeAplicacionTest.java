package dev.lacre;

import dev.lacre.api.internal.ApiDePrueba;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa 1 de la defensa append-only: la aplicación se conecta como {@code lacre_app} y Flyway
 * como propietario, cableado como en un despliegue. Usa su propio contenedor porque el resto de
 * los tests se conectan como propietario para poder probar el trigger.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RolDeAplicacionTest {

    private static final String CLAVE_APLICACION = "clave-de-pruebas-del-rol-de-aplicacion";

    /** Permiso denegado. El trigger lanza {@code restrict_violation}, que es {@code 23001}. */
    private static final String PERMISO_DENEGADO = "42501";

    /** Lo que necesita el código de cada tabla, ni más ni menos. */
    private static final Map<String, Set<String>> PERMISOS = Map.of(
            "obligado", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "registro_facturacion", Set.of("SELECT", "INSERT"),
            "envio_registro", Set.of("SELECT", "INSERT", "UPDATE"),
            "control_flujo_envio", Set.of("SELECT", "INSERT", "UPDATE"),
            "peticion_idempotente", Set.of("SELECT", "INSERT"));

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void conexionesComoEnUnDespliegue(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.datasource.username", () -> "lacre_app");
        propiedades.add("spring.datasource.password", () -> CLAVE_APLICACION);
        propiedades.add("spring.flyway.user", POSTGRES::getUsername);
        propiedades.add("spring.flyway.password", POSTGRES::getPassword);
        propiedades.add("spring.flyway.placeholders.clave_rol_aplicacion", () -> CLAVE_APLICACION);
    }

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Obligados obligados;

    @Test
    void laAplicacionSeConectaComoElRolRestringido() {
        assertThat(jdbc.sql("select current_user").query(String.class).single())
                .isEqualTo("lacre_app");
    }

    /** Obligado, registro, envío e idempotencia: todo lo que escribe un alta, con estos permisos. */
    @Test
    void conElRolRestringidoSeRegistraUnaFacturaDeExtremoAExtremo() throws Exception {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        String nif = obligados.findById(obligado).orElseThrow().nif().valor();

        mvc.perform(post("/v1/registros/alta")
                        .with(ApiDePrueba.autenticada())
                        .header("Idempotency-Key", "rol-restringido-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(nif)))
                .andExpect(status().isCreated());
    }

    /**
     * El permiso para antes que el trigger: si la sentencia llegara a él, el código sería
     * {@code 23001} y no {@code 42501}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "update registro_facturacion set xml = xml",
            "delete from registro_facturacion",
            "truncate registro_facturacion"})
    void modificarUnRegistroLoParaElPermisoAntesQueElTrigger(String sentencia) {
        assertThatThrownBy(() -> jdbc.sql(sentencia).update())
                .isInstanceOf(DataAccessException.class)
                .satisfies(fallo -> assertThat(estadoSql(fallo)).isEqualTo(PERMISO_DENEGADO));
    }

    @Test
    void elRolTieneExactamenteLosPermisosQueNecesitaElCodigo() {
        Map<String, Set<String>> concedidos = new TreeMap<>();
        jdbc.sql("""
                select table_name, privilege_type
                from information_schema.role_table_grants
                where grantee = 'lacre_app' and table_schema = 'public'
                """)
                .query((RowCallbackHandler) fila -> concedidos
                        .computeIfAbsent(fila.getString("table_name"), tabla -> new TreeSet<>())
                        .add(fila.getString("privilege_type")));

        assertThat(concedidos).isEqualTo(PERMISOS);
    }

    /** {@code pg_tables} ve también las tablas sin ningún permiso, que son las que importan. */
    @Test
    void ningunaTablaQuedaFueraDeLaMatrizDePermisos() {
        assertThat(jdbc.sql("""
                        select tablename from pg_tables
                        where schemaname = 'public' and tablename <> 'flyway_schema_history'
                        """).query(String.class).list())
                .containsExactlyInAnyOrderElementsOf(PERMISOS.keySet());
    }

    /** Rotar la clave es cambiar la configuración y volver a migrar, sin tocar el historial. */
    @Test
    void laClaveDelRolSeRotaCambiandoLaConfiguracion() throws SQLException {
        String nueva = "clave-rotada-del-rol-de-aplicacion";
        try {
            migrarConClave(nueva);

            try (Connection conexion = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "lacre_app", nueva)) {
                assertThat(conexion.isValid(1)).isTrue();
            }
            assertThatThrownBy(() -> DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "lacre_app", CLAVE_APLICACION).close())
                    .isInstanceOf(SQLException.class);
        } finally {
            migrarConClave(CLAVE_APLICACION);
        }
    }

    /** Una comilla dentro de la clave no rompe la sentencia que la fija. */
    @Test
    void unaClaveConComillasSeAplicaIgual() throws SQLException {
        String conComillas = "clave-con-'comillas'-y-$dolares$";
        try {
            migrarConClave(conComillas);

            try (Connection conexion = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), "lacre_app", conComillas)) {
                assertThat(conexion.isValid(1)).isTrue();
            }
        } finally {
            migrarConClave(CLAVE_APLICACION);
        }
    }

    private static void migrarConClave(String clave) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .placeholders(Map.of("clave_rol_aplicacion", clave))
                .load()
                .migrate();
    }

    private static String estadoSql(Throwable fallo) {
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static String alta(String nif) {
        return """
                {
                  "idFactura": {
                    "idEmisorFactura": "%s",
                    "numSerieFactura": "FA/1",
                    "fechaExpedicionFactura": "2026-01-15"
                  },
                  "nombreRazonEmisor": "Obligado de prueba SL",
                  "tipoFactura": "F1",
                  "descripcionOperacion": "Servicios de consultoría",
                  "destinatarios": [ { "nombreRazon": "Cliente SL", "nif": "A28015865" } ],
                  "desglose": [
                    {
                      "impuesto": "01",
                      "claveRegimen": "01",
                      "calificacion": "S1",
                      "tipoImpositivo": 21,
                      "baseImponibleOimporteNoSujeto": 111.10,
                      "cuotaRepercutida": 12.35
                    }
                  ],
                  "cuotaTotal": 12.35,
                  "importeTotal": 123.45
                }
                """.formatted(nif);
    }
}
