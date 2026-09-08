-- El obligado tributario deja de ser una fila de apoyo para registro_facturacion y pasa a ser
-- el agregado del módulo identidad.
--
-- La zona horaria ERA configuración del despliegue (lacre.sistema-informatico.zona-horaria) y
-- pasa a ser dato de cada obligado. No es cosmético: FechaHoraHusoGenRegistro entra en el
-- cálculo de la huella con su desplazamiento, y Canarias es Atlantic/Canary, una hora por
-- detrás del peninsular. Un ERP que factura para obligados de ambos sitios generaba con la
-- configuración única huellas que la AEAT no reconoce para uno de los dos.

alter table obligado
    add column zona_horaria varchar(60)  not null default 'Europe/Madrid',
    add column version      bigint       not null default 0;

-- El default existe solo para las filas ya creadas. A partir de aquí, insertar un obligado sin
-- zona debe fallar: asumir península en silencio es exactamente el fallo que esta columna viene
-- a impedir.
alter table obligado alter column zona_horaria drop default;
