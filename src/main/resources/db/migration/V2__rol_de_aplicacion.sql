-- Capa 1 de la defensa append-only: permisos.
--
-- El rol lacre_app solo puede leer e insertar en registro_facturacion. Ni siquiera puede
-- intentar un UPDATE: el error llega antes de que el trigger entre en juego.
--
-- IMPORTANTE: esto no surte efecto hasta que la aplicación se conecte como lacre_app. Flyway
-- necesita permisos de DDL, así que migra con el rol propietario; la aplicación debe usar una
-- conexión distinta con este rol. Mientras la aplicación se conecte como propietaria, esta capa
-- es decorativa y quien protege son el trigger y la restricción UNIQUE.
--
-- Se crea con guarda porque los roles son de ámbito de clúster, no de base de datos: si la
-- misma instancia aloja dos bases de datos de lacre, la segunda migración lo encontraría hecho.

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'lacre_app') then
        create role lacre_app noinherit login password 'cambiar_en_despliegue';
    end if;
end;
$$;

grant usage on schema public to lacre_app;

grant select, insert on registro_facturacion to lacre_app;
grant select, insert, update, delete on obligado to lacre_app;

-- Explícito y no por omisión: quien lea esto tiene que ver que la exclusión es intencionada.
revoke update, delete, truncate on registro_facturacion from lacre_app;

-- Las tablas que cree Flyway en el futuro no heredan nada por defecto; cada migración que añada
-- una tabla debe conceder sus permisos.
