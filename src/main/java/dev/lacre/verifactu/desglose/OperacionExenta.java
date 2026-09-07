package dev.lacre.verifactu.desglose;

/**
 * Causa de exención, lista L10 del anexo de la Orden HAC/1177/2024.
 * <p>
 * El XSD enumera los códigos sin descripción, así que aquí tampoco se documenta el
 * significado de cada uno: inventarlo sería peor que no ponerlo. Está en el anexo de la orden.
 * <p>
 * El nombre de cada constante es el código que viaja en el XML.
 */
public enum OperacionExenta implements Calificacion {

    E1, E2, E3, E4, E5, E6, E7, E8;

    @Override
    public String codigo() {
        return name();
    }
}
