package com.cazadoxs.escanerlimpio.classify

import kotlinx.serialization.Serializable

/** Smart folders. Every document lands in exactly one, chosen from its text. */
@Serializable
enum class Category(val label: String, val singular: String) {
    IDENTIDAD("Identidad", "Documento de identidad"),
    NOMINAS("Nóminas", "Nómina"),
    FACTURAS("Facturas", "Factura"),
    TICKETS("Tickets", "Ticket"),
    HACIENDA("Hacienda", "Hacienda"),
    BANCO("Banco", "Documento bancario"),
    CONTRATOS("Contratos", "Contrato"),
    SEGUROS("Seguros", "Seguro"),
    SALUD("Salud", "Informe médico"),
    VEHICULO("Vehículo", "Vehículo"),
    ESTUDIOS("Estudios", "Documento académico"),
    OTROS("Otros", "Documento");

    companion object {
        /** Categories that usually matter for the Spanish tax return (Renta). */
        val RENTA_DEFAULT = setOf(NOMINAS, HACIENDA, FACTURAS, BANCO, SALUD, SEGUROS, ESTUDIOS)

        fun fromKey(key: String?): Category? = entries.firstOrNull { it.name == key }
    }
}
