import com.google.gson.annotations.SerializedName

data class DTOViajeResponse(
    val idViaje: Long?,
    val idOrdenServicio: Long?,
    val clienteNIT: String?,
    val nombreCliente: String?,
    val apellidoCliente: String?,
    val telefonoCliente: String?,
    val correoCliente: String?,

    @SerializedName("horaLLegada")
    val horaLlegada: String?,  // mapeo correcto

    @SerializedName("lugarLLegada")
    val lugarLlegada: String?, // mapeo correcto

    val horaEstimadaLlegada: String?,
    val horaSalida: String?,
    val lugarPartida: String?,
    val coordenadaPartida: String?,
    val coordenadaLlegada: String?,
    val progreso: String?,
    val progresoTrans: String?


)
