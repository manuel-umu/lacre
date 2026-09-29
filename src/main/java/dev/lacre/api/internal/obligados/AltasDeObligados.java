package dev.lacre.api.internal.obligados;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Nif;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de alta de obligados: crea el obligado o actualiza sus datos, identificado por su
 * NIF. Va bajo un cerrojo consultivo por NIF, para que dos altas simultáneas no choquen.
 */
@Service
class AltasDeObligados {

    private final Obligados obligados;
    private final JdbcClient jdbc;
    private final Supplier<UUID> generadorDeIdentificadores;

    AltasDeObligados(Obligados obligados, JdbcClient jdbc, Supplier<UUID> generadorDeIdentificadores) {
        this.obligados = obligados;
        this.jdbc = jdbc;
        this.generadorDeIdentificadores = generadorDeIdentificadores;
    }

    /** Obligado tal y como queda, y si se acaba de crear. */
    record Alta(ObligadoTributario obligado, boolean creado) {}

    @Transactional
    Alta darDeAlta(String nif, PeticionObligado peticion) {
        Nif delObligado = new Nif(nif);
        ZoneId zona = peticion.zona();

        serializarElNif(delObligado);

        return obligados
                .findByNif(delObligado)
                .map(existente -> new Alta(obligados.save(existente.conDatos(peticion.nombreRazon(), zona)), false))
                .orElseGet(() -> new Alta(
                        obligados.save(ObligadoTributario.nuevo(
                                generadorDeIdentificadores.get(), delObligado,
                                peticion.nombreRazon(), zona)),
                        true));
    }

    /** Cerrojo consultivo por NIF; se libera al terminar la transacción. */
    private void serializarElNif(Nif nif) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:clave))")
                .param("clave", "obligado:" + nif.valor())
                .query()
                .singleRow();
    }
}
