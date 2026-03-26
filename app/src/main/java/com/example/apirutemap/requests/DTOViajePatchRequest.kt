package com.example.apirutemap.requests

data class DTOViajePatchRequest(
    val horaSalida: String? = null,
    val horaEstimadaLlegada: String? = null,
    val lugarPartida: String? = null,
    val coordenadaPartida: String? = null,
    val lugarLLegada: String? = null,
    val coordenadaLlegada: String? = null,
    val progresoTrans: String? = null,
    val horaLLegada: String? = null
)
