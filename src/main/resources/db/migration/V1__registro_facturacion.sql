-- Registros de facturación encadenados de un obligado tributario.
--
-- La tabla registro_facturacion es de SOLO INSERCIÓN. El RD 1007/2023 exige que los registros
-- sean inalterables, y aquí eso se defiende en tres capas independientes:
--
--   1. Permisos: el rol de aplicación solo tiene SELECT e INSERT (V2).
--   2. Trigger: cualquier UPDATE, DELETE o TRUNCATE lanza excepción.
--   3. Restricción UNIQUE (obligado_id, posicion): hace estructuralmente imposible bifurcar
--      la cadena aunque falle el cerrojo de la aplicación.
--
-- Cada capa cubre el fallo de las otras: los permisos no protegen del superusuario, el trigger
-- no protege de que alguien lo desactive, y la restricción no impide modificar una fila pero sí
-- que existan dos con la misma posición.

create table obligado (
    id            uuid         primary key,
    nif           varchar(9)   not null unique,
    nombre_razon  varchar(120) not null
);

create table registro_facturacion (
    id                            uuid         primary key,
    obligado_id                   uuid         not null references obligado (id),

    -- Posición en la cadena del obligado, empezando en 1. Ver el UNIQUE de abajo.
    posicion                      bigint       not null,
    tipo                          varchar(10)  not null,

    -- Identificación de la factura: la que se expide, o la que se anula.
    emisor                        varchar(9)   not null,
    num_serie_factura             varchar(60)  not null,
    fecha_expedicion_factura      date         not null,

    -- Encadenamiento. huella_anterior es nula solo en el primer registro de la cadena.
    huella                        char(64)     not null,
    huella_anterior               char(64),

    -- El instante en que se generó el registro, que entra en el cálculo de la huella.
    -- Se guarda también el desplazamiento horario porque timestamptz normaliza a UTC y perdería
    -- el huso original: sin él, la fila no podría reproducir su propia huella. Añadir esta
    -- columna después, sobre una tabla de solo inserción, sería mucho más caro que ponerla ya.
    fecha_hora_huso_gen_registro  timestamptz  not null,
    huso_offset_segundos          integer      not null,

    -- El XML remitido a la AEAT, tal cual se generó. Es la serialización autoritativa.
    xml                           text         not null,

    constraint registro_facturacion_cadena_unica unique (obligado_id, posicion)
);

create index registro_facturacion_por_factura
    on registro_facturacion (obligado_id, emisor, num_serie_factura, fecha_expedicion_factura);

-- Capa 2: nada modifica ni borra un registro, con independencia de los permisos que tenga quien
-- lo intente. FOR EACH STATEMENT y no FOR EACH ROW porque también hay que cubrir el TRUNCATE,
-- que no tiene filas.
create function registro_facturacion_es_inalterable() returns trigger
    language plpgsql as $$
begin
    raise exception
        'registro_facturacion es de solo inserción: % no está permitido (RD 1007/2023)', tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger registro_facturacion_append_only
    before update or delete or truncate on registro_facturacion
    for each statement
execute function registro_facturacion_es_inalterable();
