package dev.lacre.remision.internal.adaptador;

/** Respuestas de la consulta de la AEAT con la forma del anexo IV de la descripción del servicio. */
final class RespuestasDeConsulta {

    static final String HUELLA_FA1 = "DAA7F72EEDC1AE8A294B7A011EC4A1EC2BE0E4DDB79AF3758377F8D61F38FE6B";
    static final String HUELLA_FA2 = "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60";

    private RespuestasDeConsulta() {}

    /** Sobre SOAP con la respuesta; {@code paginacion} es el bloque {@code ClavePaginacion} o vacío. */
    static String respuesta(String indicador, String resultado, String registros, String paginacion) {
        return """
                <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/">
                  <env:Header/>
                  <env:Body Id="Body">
                    %s
                  </env:Body>
                </env:Envelope>
                """.formatted(cuerpo(indicador, resultado, registros, paginacion));
    }

    /** El elemento {@code RespuestaConsultaFactuSistemaFacturacion}, sin sobre. */
    static String cuerpo(String indicador, String resultado, String registros, String paginacion) {
        return """
                <tikLRRC:RespuestaConsultaFactuSistemaFacturacion
                    xmlns:tikLRRC="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaConsultaLR.xsd"
                    xmlns:tik="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd">
                  <tikLRRC:Cabecera>
                    <tik:IDVersion>1.0</tik:IDVersion>
                    <tik:ObligadoEmision>
                      <tik:NombreRazon>OBLIGADO DE PRUEBA SL</tik:NombreRazon>
                      <tik:NIF>89890001K</tik:NIF>
                    </tik:ObligadoEmision>
                  </tikLRRC:Cabecera>
                  <tikLRRC:PeriodoImputacion>
                    <tikLRRC:Ejercicio>2024</tikLRRC:Ejercicio>
                    <tikLRRC:Periodo>11</tikLRRC:Periodo>
                  </tikLRRC:PeriodoImputacion>
                  <tikLRRC:IndicadorPaginacion>%s</tikLRRC:IndicadorPaginacion>
                  <tikLRRC:ResultadoConsulta>%s</tikLRRC:ResultadoConsulta>
                  %s
                  %s
                </tikLRRC:RespuestaConsultaFactuSistemaFacturacion>
                """.formatted(indicador, resultado, registros, paginacion);
    }

    /**
     * Un registro con encadenamiento y factura rectificada, cuyos {@code NumSerieFactura} y
     * {@code Huella} no son los del registro.
     */
    static String registro(String numSerie, String huella, String estado, String error) {
        return """
                <tikLRRC:RegistroRespuestaConsultaFactuSistemaFacturacion>
                  <tikLRRC:IDFactura>
                    <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                    <tik:NumSerieFactura>%s</tik:NumSerieFactura>
                    <tik:FechaExpedicionFactura>27-11-2024</tik:FechaExpedicionFactura>
                  </tikLRRC:IDFactura>
                  <tikLRRC:DatosRegistroFacturacion>
                    <tikLRRC:TipoFactura>R1</tikLRRC:TipoFactura>
                    <tikLRRC:TipoRectificativa>I</tikLRRC:TipoRectificativa>
                    <tikLRRC:FacturasRectificadas>
                      <tikLRRC:IDFacturaRectificada>
                        <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                        <tik:NumSerieFactura>RECTIFICADA</tik:NumSerieFactura>
                        <tik:FechaExpedicionFactura>01-11-2024</tik:FechaExpedicionFactura>
                      </tikLRRC:IDFacturaRectificada>
                    </tikLRRC:FacturasRectificadas>
                    <tikLRRC:DescripcionOperacion>Servicios de reparación</tikLRRC:DescripcionOperacion>
                    <tikLRRC:CuotaTotal>41.76</tikLRRC:CuotaTotal>
                    <tikLRRC:ImporteTotal>1085.79</tikLRRC:ImporteTotal>
                    <tikLRRC:Encadenamiento>
                      <tikLRRC:RegistroAnterior>
                        <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                        <tik:NumSerieFactura>ANTERIOR</tik:NumSerieFactura>
                        <tik:FechaExpedicionFactura>27-11-2024</tik:FechaExpedicionFactura>
                        <tik:Huella>%s</tik:Huella>
                      </tikLRRC:RegistroAnterior>
                    </tikLRRC:Encadenamiento>
                    <tikLRRC:FechaHoraHusoGenRegistro>2024-11-27T11:54:10+01:00</tikLRRC:FechaHoraHusoGenRegistro>
                    <tikLRRC:TipoHuella>01</tikLRRC:TipoHuella>
                    <tikLRRC:Huella>%s</tikLRRC:Huella>
                  </tikLRRC:DatosRegistroFacturacion>
                  <tikLRRC:DatosPresentacion>
                    <tik:NIFPresentador>89890001K</tik:NIFPresentador>
                    <tik:TimestampPresentacion>2024-11-27T11:54:12+01:00</tik:TimestampPresentacion>
                    <tik:IdPeticion>20241127115412360242</tik:IdPeticion>
                  </tikLRRC:DatosPresentacion>
                  <tikLRRC:EstadoRegistro>
                    <tikLRRC:TimestampUltimaModificacion>2024-11-27T11:54:12+01:00</tikLRRC:TimestampUltimaModificacion>
                    <tikLRRC:EstadoRegistro>%s</tikLRRC:EstadoRegistro>
                    %s
                  </tikLRRC:EstadoRegistro>
                </tikLRRC:RegistroRespuestaConsultaFactuSistemaFacturacion>
                """.formatted(numSerie, HUELLA_FA2.equals(huella) ? HUELLA_FA1 : HUELLA_FA2, huella, estado, error);
    }

    static String clavePaginacion(String numSerie) {
        return """
                <tikLRRC:ClavePaginacion>
                  <tik:IDEmisorFactura>89890001K</tik:IDEmisorFactura>
                  <tik:NumSerieFactura>%s</tik:NumSerieFactura>
                  <tik:FechaExpedicionFactura>27-11-2024</tik:FechaExpedicionFactura>
                </tikLRRC:ClavePaginacion>
                """.formatted(numSerie);
    }

    static String error(int codigo, String descripcion) {
        return """
                <tikLRRC:CodigoErrorRegistro>%d</tikLRRC:CodigoErrorRegistro>
                <tikLRRC:DescripcionErrorRegistro>%s</tikLRRC:DescripcionErrorRegistro>
                """.formatted(codigo, descripcion);
    }
}
