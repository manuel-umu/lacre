-- El obligado tributario deja de ser una fila de apoyo para registro_facturacion y pasa a ser
-- el agregado del módulo identidad.
--
-- La zona horaria ERA configuración del despliegue (lacre.sistema-informatico.zona-horaria) y
-- pasa a ser dato de cada obligado. No es cosmético: el diseño de registro de la AEAT dice que
-- el huso de FechaHoraHusoGenRegistro es "el que está usando el sistema informático de
-- facturación en el momento de generación", y Canarias es Atlantic/Canary, una hora por detrás
-- del peninsular. Con una sola zona de despliegue, un ERP que factura para obligados de ambos
-- sitios no puede declarar la verdad sobre ninguno de los dos.
--
-- Ojo con el motivo: la huella NO se rompe por esto. La AEAT recalcula el hash sobre el XML que
-- recibe, así que cuadra con cualquier huso que se emita. Lo que se protege aquí es la
-- veracidad del dato, no la aritmética.

alter table obligado
    add column zona_horaria varchar(60)  not null default 'Europe/Madrid',
    add column version      bigint       not null default 0;

-- El default existe solo para las filas ya creadas. A partir de aquí, insertar un obligado sin
-- zona debe fallar: asumir península en silencio es exactamente el fallo que esta columna viene
-- a impedir.
alter table obligado alter column zona_horaria drop default;
