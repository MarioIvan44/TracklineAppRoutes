package com.example.apirutemap

import DTOViajeResponse
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.apirutemap.PageResponse
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.regex.Pattern

class ViajesActivity : AppCompatActivity() {

    private lateinit var contenedorViajes: LinearLayout
    private lateinit var btnAnterior: Button
    private lateinit var btnSiguiente: Button
    private lateinit var tvPagina: TextView
    private lateinit var etBuscarViaje: EditText
    private lateinit var btnBuscar: Button
    private lateinit var btnLimpiarBusqueda: Button

    private var paginaActual = 0
    private val tamanioPagina = 5
    private var idUsuario: Long = 0
    private var modoBusqueda = false
    private var viajeBuscado: DTOViajeResponse? = null

    private lateinit var api: BackendApiService

    private lateinit var map: GoogleMap
    private var poly: Polyline? = null

    private lateinit var ivLogout: ImageView // De Button a ImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tarjetas_viajes)
        api = RetrofitClient.getClient(this).create(BackendApiService::class.java)

        // Inicializar vistas
        contenedorViajes = findViewById(R.id.contenedorViajes)
        btnAnterior = findViewById(R.id.btnAnterior)
        btnSiguiente = findViewById(R.id.btnSiguiente)
        tvPagina = findViewById(R.id.tvPagina)
        ivLogout = findViewById(R.id.ivLogout) // ImageView en lugar de Button
        etBuscarViaje = findViewById(R.id.etBuscarViaje)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnLimpiarBusqueda = findViewById(R.id.btnLimpiarBusqueda)

        // ✅ CONFIGURAR BUSCADOR - Solo números y máximo 10 caracteres
        configurarBuscador()

        // ✅ Leer el ID desde el intent o SharedPreferences
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        idUsuario = intent.getLongExtra("idCliente", prefs.getLong("idUsuario", 0L))

        if (idUsuario == 0L) {
            Toast.makeText(this, "Error: ID de cliente no recibido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cargarViajes()

        // Listeners
        btnAnterior.setOnClickListener {
            if (paginaActual > 0) {
                paginaActual--
                cargarViajes()
            } else {
                Toast.makeText(this, "Ya estás en la primera página", Toast.LENGTH_SHORT).show()
            }
        }

        btnSiguiente.setOnClickListener {
            paginaActual++
            cargarViajes()
        }

        btnBuscar.setOnClickListener {
            buscarViajePorId()
        }

        btnLimpiarBusqueda.setOnClickListener {
            limpiarBusqueda()
        }

        // ✅ MEJORADO: ImageView en lugar de Button para cerrar sesión
        ivLogout.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }

        // ✅ OPCIONAL: Agregar efecto de tooltip/presión prolongada
        ivLogout.setOnLongClickListener {
            Toast.makeText(this, "Cerrar sesión", Toast.LENGTH_SHORT).show()
            true
        }
    }

    // ✅ CONFIGURAR BUSCADOR - Solo números y máximo 10 caracteres
    private fun configurarBuscador() {
        // Filtro para solo permitir números
        val numericFilter = object : InputFilter {
            private val pattern = Pattern.compile("[0-9]*")

            override fun filter(
                source: CharSequence?,
                start: Int,
                end: Int,
                dest: Spanned?,
                dstart: Int,
                dend: Int
            ): CharSequence? {
                return if (source != null && !pattern.matcher(source).matches()) {
                    "" // Rechazar caracteres no numéricos
                } else {
                    null // Aceptar caracteres
                }
            }
        }

        // Aplicar filtros
        etBuscarViaje.filters = arrayOf(
            numericFilter,
            InputFilter.LengthFilter(10) // Máximo 10 caracteres
        )

        // Placeholder
        etBuscarViaje.hint = "Ingresa ID del viaje"

        // Opcional: Buscar al presionar Enter
        etBuscarViaje.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                buscarViajePorId()
                true
            } else {
                false
            }
        }
    }

    // ✅ BUSCAR VIAJE POR ID
    private fun buscarViajePorId() {
        val idTexto = etBuscarViaje.text.toString().trim()

        if (idTexto.isEmpty()) {
            Toast.makeText(this, "Ingresa un ID de viaje", Toast.LENGTH_SHORT).show()
            return
        }

        val idViaje = try {
            idTexto.toLong()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostrar loading
        Toast.makeText(this, "Buscando viaje...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ViajesActivity", "Buscando viaje ID: $idViaje")
                val response = api.getViajeById(idViaje)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        val viaje = apiResponse?.data

                        if (viaje != null) {
                            // ✅ VIAJE ENCONTRADO
                            modoBusqueda = true
                            viajeBuscado = viaje
                            mostrarViajeUnico(viaje)
                            Toast.makeText(this@ViajesActivity, "Viaje encontrado", Toast.LENGTH_SHORT).show()
                        } else {
                            // ❌ VIAJE NO ENCONTRADO
                            Toast.makeText(this@ViajesActivity, "No se encontró el viaje $idViaje", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        when (response.code()) {
                            404 -> Toast.makeText(this@ViajesActivity, "Viaje no encontrado", Toast.LENGTH_LONG).show()
                            401 -> Toast.makeText(this@ViajesActivity, "No autorizado", Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(this@ViajesActivity, "Error al buscar viaje", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViajesActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ViajesActivity", "Error al buscar viaje", e)
                }
            }
        }
    }

    // ✅ MOSTRAR VIAJE ÚNICO (Resultado de búsqueda)
    private fun mostrarViajeUnico(viaje: DTOViajeResponse) {
        contenedorViajes.removeAllViews()

        val view = layoutInflater.inflate(R.layout.item_viaje, contenedorViajes, false)

        view.findViewById<TextView>(R.id.tvTituloViaje).text = "Viaje #${viaje.idViaje} 🔍"
        view.findViewById<TextView>(R.id.tvHoraSalidaEstimada).text =
            "Hora estimada de salida: ${viaje.horaSalida ?: "No registrada"}"
        view.findViewById<TextView>(R.id.tvHoraLlegadaEstimada).text =
            "Hora estimada de llegada: ${viaje.horaEstimadaLlegada ?: "No registrada"}"
        view.findViewById<TextView>(R.id.tvOrdenServicio).text =
            "Orden de servicio: ${viaje.idOrdenServicio}"

        val verTracking = view.findViewById<TextView>(R.id.tvVerTracking)
        verTracking.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("idViaje", viaje.idViaje)
            intent.putExtra("fromViajesActivity", true)
            startActivity(intent)
        }

        contenedorViajes.addView(view)

        // ✅ ACTUALIZAR UI PARA MODO BÚSQUEDA
        tvPagina.text = "Resultado de búsqueda"
        btnAnterior.isEnabled = false
        btnSiguiente.isEnabled = false
        btnLimpiarBusqueda.visibility = View.VISIBLE
    }

    // ✅ LIMPIAR BÚSQUEDA Y VOLVER A LISTA PAGINADA
    private fun limpiarBusqueda() {
        modoBusqueda = false
        viajeBuscado = null
        etBuscarViaje.text.clear()
        btnLimpiarBusqueda.visibility = View.GONE
        paginaActual = 0 // Volver a primera página
        cargarViajes()
        Toast.makeText(this, "Mostrando todos los viajes", Toast.LENGTH_SHORT).show()
    }

    // ✅ NUEVA FUNCIÓN: Mostrar diálogo de confirmación
    private fun mostrarDialogoCerrarSesion() {
        AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Sí, cerrar sesión") { dialog, _ ->
                dialog.dismiss()
                cerrarSesion()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Operación cancelada", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(true)
            .show()
    }

    // ✅ FUNCIÓN MEJORADA: Cerrar sesión con manejo de errores
    private fun cerrarSesion() {
        try {
            // Limpiar todas las preferencias
            val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.clear() // 🧹 borra token, rol, usuario, etc.
            editor.apply()

            // Mostrar confirmación
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()

            // 🔥 Redirige al login y limpia el stack para evitar volver atrás
            val intent = Intent(this, LoginTransportistas::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("ViajesActivity", "Error durante cierre de sesión", e)
            // Aún así intentar redirigir al login
            val intent = Intent(this, LoginTransportistas::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun cargarViajes() {
        // Si estamos en modo búsqueda, no cargar la lista paginada
        if (modoBusqueda && viajeBuscado != null) {
            mostrarViajeUnico(viajeBuscado!!)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ViajesActivity", "Solicitando viajes para usuario ID: $idUsuario (página $paginaActual)")
                val response = api.getViajesPaginados(idUsuario, paginaActual, tamanioPagina)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val pageData = response.body()
                        if (pageData != null) {
                            mostrarViajes(pageData)
                        } else {
                            Log.e("ViajesActivity", "Respuesta vacía del servidor")
                            Toast.makeText(this@ViajesActivity, "No se recibieron datos", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ViajesActivity", "Error ${response.code()}: $errorBody")
                        Toast.makeText(this@ViajesActivity, "Error al obtener viajes (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViajesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarViajes(pageData: PageResponse<DTOViajeResponse>) {
        contenedorViajes.removeAllViews()

        // ✅ VERIFICAR SI HAY VIAJES
        if (pageData.content.isEmpty()) {
            val tvEmpty = TextView(this@ViajesActivity).apply {
                text = "No hay viajes para mostrar"
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.darker_gray))
                setPadding(0, 50, 0, 0)
                gravity = android.view.Gravity.CENTER
            }
            contenedorViajes.addView(tvEmpty)
        }

        for (viaje in pageData.content) {
            val view = layoutInflater.inflate(R.layout.item_viaje, contenedorViajes, false)

            view.findViewById<TextView>(R.id.tvTituloViaje).text = "Viaje #${viaje.idViaje}"
            view.findViewById<TextView>(R.id.tvHoraSalidaEstimada).text =
                "Hora estimada de salida: ${viaje.horaSalida ?: "No registrada"}"
            view.findViewById<TextView>(R.id.tvHoraLlegadaEstimada).text =
                "Hora estimada de llegada: ${viaje.horaEstimadaLlegada ?: "No registrada"}"
            view.findViewById<TextView>(R.id.tvOrdenServicio).text =
                "Orden de servicio: ${viaje.idOrdenServicio}"

            val verTracking = view.findViewById<TextView>(R.id.tvVerTracking)
            verTracking.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("idViaje", viaje.idViaje)
                intent.putExtra("fromViajesActivity", true)
                startActivity(intent)
            }

            contenedorViajes.addView(view)
        }

        // ✅ ACTUALIZAR PAGINACIÓN MEJORADA
        actualizarPaginacion(pageData)
    }

    // ✅ FUNCIÓN MEJORADA: Actualizar estado de paginación
    private fun actualizarPaginacion(pageData: PageResponse<DTOViajeResponse>) {
        val totalPages = pageData.totalPages
        val currentPage = pageData.number + 1

        tvPagina.text = "Página $currentPage de $totalPages"

        // Botón Anterior: deshabilitar si es primera página
        btnAnterior.isEnabled = !pageData.first

        // Botón Siguiente: deshabilitar si es última página
        btnSiguiente.isEnabled = !pageData.last

        // Ocultar botones si solo hay una página
        if (totalPages <= 1) {
            btnAnterior.visibility = View.GONE
            btnSiguiente.visibility = View.GONE
            tvPagina.text = "Todos los viajes"
        } else {
            btnAnterior.visibility = View.VISIBLE
            btnSiguiente.visibility = View.VISIBLE
        }
    }

    // Retrofit para Backend
    private fun getBackendRetrofit(): BackendApiService {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (token != null) request.addHeader("Cookie", "authToken=$token")
            chain.proceed(request.build())
        }.build()

        return retrofit2.Retrofit.Builder()
            .baseUrl("https://apitrackline-3047cf7af332.herokuapp.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .client(client)
            .build()
            .create(BackendApiService::class.java)
    }

    // Retrofit para OpenRouteService
    private fun getORSRetrofit(): retrofit2.Retrofit {
        return retrofit2.Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }
}