package dev.lacre.verifactu.internal.adaptador;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar si la conexión de la aplicación puede modificar, borrar o vaciar
 * {@code registro_facturacion}, como la del propietario de la base de datos.
 */
@Component
class ComprobacionDelRolDeAplicacion {

    ComprobacionDelRolDeAplicacion(JdbcClient jdbc) {
        Conexion conexion = jdbc.sql("""
                        select current_user as usuario,
                               has_table_privilege('registro_facturacion',
                                                   'UPDATE, DELETE, TRUNCATE') as puede_modificar
                        """)
                .query(Conexion.class)
                .single();
        if (conexion.puedeModificar()) {
            throw new IllegalStateException("La aplicación se conecta a la base de datos como «"
                    + conexion.usuario() + "», que puede modificar o borrar registros de "
                    + "facturación. Debe conectarse con el rol lacre_app "
                    + "(spring.datasource.username) y dejar el propietario para las migraciones "
                    + "(spring.flyway.user).");
        }
    }

    private record Conexion(String usuario, boolean puedeModificar) {
    }
}
