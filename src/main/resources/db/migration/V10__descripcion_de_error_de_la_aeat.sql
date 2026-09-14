-- La descripción del error de un envío cabe entera.
--
-- V5 la dejó en 500 caracteres, pero la respuesta del servicio de remisión declara
-- DescripcionErrorRegistro como TextMax1500Type (RespuestaSuministro.xsd). Una descripción válida
-- de más de 500 hacía fallar la escritura del desenlace, se deshacía el lote y, al reintentarlo,
-- la AEAT contestaba «duplicado»: el rechazo que había que subsanar se perdía sin rastro.

alter table envio_registro alter column descripcion_error type varchar(1500);
