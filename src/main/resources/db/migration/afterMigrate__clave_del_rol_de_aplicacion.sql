-- Capa 1 de la defensa append-only: la clave con la que la aplicación entra como lacre_app.
--
-- Es un callback de Flyway y no una migración versionada: corre después de cada migrate, haya
-- migraciones pendientes o no, y no entra en el historial. La clave sale siempre de la
-- configuración del despliegue —rotarla es cambiar la variable y reiniciar— y la literal que
-- deja V2 no sobrevive al primer arranque.
--
-- Llega por el placeholder clave_rol_aplicacion, entre comillas dólar y aplicada con format(%L),
-- para que ni una comilla ni un dólar dentro de la clave rompan la sentencia. Dos detalles del
-- analizador de Flyway, que parte el script antes de que lo vea PostgreSQL:
--   - La clave va rodeada de guiones bajos, que substr descarta. Pegado a una etiqueta, un dólar
--     de la clave formaría $$, y $${ Flyway lo lee como un placeholder escapado.
--   - El bloque usa la etiqueta $bloque$ y no $$, que Flyway tomaría por su cierre.

do $bloque$
declare
    delimitada text := $clave$_${clave_rol_aplicacion}_$clave$;
    clave text := substr(delimitada, 2, length(delimitada) - 2);
begin
    if clave = '' then
        raise exception 'Falta la clave del rol lacre_app: configura LACRE_DB_CLAVE_APLICACION';
    end if;
    execute format('alter role lacre_app password %L', clave);
end;
$bloque$;
