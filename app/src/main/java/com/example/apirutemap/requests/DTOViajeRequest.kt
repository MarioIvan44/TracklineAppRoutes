package com.example.apirutemap.requests

data class DTOViajeRequest(
    val horaSalida: String? = null,
    val horaEstimadaLlegada: String? = null,
    val horaLlegada: String? = null,
    val lugarPartida: String? = null,
    val coordenadaPartida: String? = null,
    val lugarLlegada: String? = null,
    val coordenadaLlegada: String? = null,
    val progresoTrans: String? = null,
    val idOrdenServicio: Long?,
    val idTransporteViaje: Long?,
    val idEstado: Long?
)
