package com.example.apirutemap

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.text.SimpleDateFormat
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import androidx.core.graphics.toColorInt

import kotlinx.coroutines.isActive
import java.util.Calendar
import java.util.Date
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import okhttp3.OkHttpClient
import java.time.Duration
import com.example.apirutemap.requests.DTOViajePatchRequest
import com.google.android.gms.maps.model.BitmapDescriptorFactory


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var btnCenterMap: Button
    private lateinit var btnLogout: Button

    private var viajeId: Long? = null
    private var positionJob: kotlinx.coroutines.Job? = null

    private var estimatedDuration: Int = 0

    private lateinit var map: GoogleMap
    private lateinit var btnIniciarTracking: Button
    private lateinit var btnFinalizarTracking: Button
    private lateinit var btnCancelarTracking: Button

    private lateinit var etSearchLocation: EditText
    private lateinit var btnSearch: Button
    private lateinit var searchContainer: LinearLayout

    private var inputViajeId: Long? = null

    private var start: String = ""
    private var end: String = ""
    private var startName = ""
    private var endName = ""
    private var horaEstimadaPartida = ""
    private var horaEstimadaLlegada = ""
    private var horaLlegada = ""
    private var trackingActivo = false
    private var poly: Polyline? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentMarker: Marker? = null

    private var progressJob: kotlinx.coroutines.Job? = null

    // Lista con coordenadas de la ruta actual
    private var routeCoordinates: List<LatLng> = emptyList()

    // Para no recalcular demasiado seguido
    private var lastRerouteTime: Long = 0
    private val rerouteCooldownMillis = 30_000 // mínimo 30s entre recalculos


    //PARA LLLAMAR AL MAPA DESDE VIAJES (CLIENTE)
    private lateinit var btnVolverViajes: Button

    private var fromViajesScreen = false


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias del buscador
        etSearchLocation = findViewById(R.id.etSearchLocation)
        btnSearch = findViewById(R.id.btnSearch)
        searchContainer = findViewById(R.id.searchContainer)

        // Acción de buscar
        btnSearch.setOnClickListener {
            val locationName = etSearchLocation.text.toString()
            if (locationName.isNotEmpty()) {
                searchLocation(locationName)
            } else {
                Toast.makeText(this, "Escribe una ubicación", Toast.LENGTH_SHORT).show()
            }
        }

        // Mostrar el buscador solo antes de iniciar
        searchContainer.visibility = View.VISIBLE

        // Evita que la pantalla se apague
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Referencias a botones
        btnIniciarTracking = findViewById(R.id.btnStartTrip)
        btnFinalizarTracking = findViewById(R.id.btnFinish)
        btnCancelarTracking = findViewById(R.id.btnCancel)
        btnCenterMap = findViewById(R.id.btnCenterMap)
        btnLogout = findViewById(R.id.btnLogout)


        // Listeners
        btnIniciarTracking.setOnClickListener { iniciarTracking() }
        btnFinalizarTracking.setOnClickListener { finalizarTracking() }
        btnCancelarTracking.setOnClickListener { cancelarTracking() }

        btnCenterMap.setOnClickListener {
            if (checkLocationPermission()) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 17f)
                        )
                    } else {
                        Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Activa los permisos de ubicación", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            mostrarDialogoCerrarSesion()
        }



        btnVolverViajes = findViewById(R.id.btnVolverViajes)

        // Revisar si venimos de ViajesActivity
        val fromViajes = intent.getBooleanExtra("fromViajesActivity", false)
        val idViaje = intent.getLongExtra("idViaje", -1L)

        fromViajesScreen = fromViajes && idViaje != -1L

        if (fromViajes && idViaje != -1L) {
            btnVolverViajes.visibility = View.VISIBLE
            btnIniciarTracking.visibility = View.GONE
            btnCancelarTracking.visibility = View.GONE
            btnCenterMap.visibility = View.GONE
            btnLogout.visibility = View.GONE
            etSearchLocation.visibility = View.GONE
            btnSearch.visibility = View.GONE
            searchContainer.visibility = View.GONE
            btnFinalizarTracking.visibility = View.GONE

            // Mostrar botón para regresar
            fetchViajeAndDrawRoute(idViaje)           // Traer coordenadas y dibujar ruta
        } else {
            btnVolverViajes.visibility = View.GONE
        }

        // ✅ AGREGAR LISTENER PARA VOLVER A VIAJES
        btnVolverViajes.setOnClickListener {
            // Detener las actualizaciones de progreso si están activas
            stopGettingProgressUpdates()

            // Crear intent para regresar a ViajesActivity
            val intent = Intent(this, ViajesActivity::class.java)

            // Agregar flags para limpiar la pila de actividades si es necesario
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)
            finish() // Cerrar la actividad actual
        }


        // Estado inicial
        actualizarBotones()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    private fun actualizarBotones() {
        if (fromViajesScreen) {
            // Es de viajes, todo oculto menos btnVolverViajes
            btnIniciarTracking.visibility = View.GONE
            btnCancelarTracking.visibility = View.GONE
            btnCenterMap.visibility = View.GONE
            btnLogout.visibility = View.GONE
            etSearchLocation.visibility = View.GONE
            btnSearch.visibility = View.GONE
            searchContainer.visibility = View.GONE
            btnFinalizarTracking.visibility = View.GONE
            return
        }
        if (trackingActivo) {
            btnIniciarTracking.visibility = View.GONE
            btnCancelarTracking.visibility = View.VISIBLE
            btnCancelarTracking.isEnabled = true
            btnLogout.visibility = View.GONE
            btnCenterMap.visibility = View.VISIBLE

        } else {
            btnIniciarTracking.visibility = View.VISIBLE
            btnIniciarTracking.isEnabled = true
            btnFinalizarTracking.visibility = View.GONE
            btnCancelarTracking.visibility = View.GONE
            btnCancelarTracking.isEnabled = false
            btnLogout.visibility = View.VISIBLE
            btnCenterMap.visibility = View.GONE
            etSearchLocation.visibility = View.VISIBLE
            etSearchLocation.isEnabled = true
            btnSearch.visibility = View.VISIBLE
            btnSearch.isEnabled = true
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun iniciarTracking() {
        // Resetear variables
        start = ""
        end = ""
        startName = ""
        endName = ""
        poly?.remove()
        poly = null
        btnCancelarTracking.visibility = View.VISIBLE
        btnCancelarTracking.isEnabled = true

        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    start = "${location.longitude},${location.latitude}"
                    startName = getPlaceName(location.latitude, location.longitude)

                    val latLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                    )

                    Toast.makeText(
                        this,
                        "Tu ubicación es el punto de partida, selecciona el destino.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Ahora solo seleccionar destino en el mapa
                    map.setOnMapClickListener {
                        if (end.isEmpty()) {
                            end = "${it.longitude},${it.latitude}"
                            endName = getPlaceName(it.latitude, it.longitude)
                            createRoute()
                        }
                    }

                    trackingActivo = true
                    actualizarBotones()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun finalizarTracking() {
        // Guardar hora real de llegada
        horaLlegada = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Formatos para poder calcular la diferencia
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val inicioDate = sdf.parse(horaEstimadaPartida)
        val finDate = sdf.parse(horaLlegada)

        // Calcular diferencia en minutos
        val diferenciaMin = ((finDate.time - inicioDate.time) / 60000).toInt()
        val horas = diferenciaMin / 60
        val minutos = diferenciaMin % 60
        val duracionViaje = "${horas} h ${minutos} min"

        //Lógica de finalizar tracking
        btnFinalizarTracking.isEnabled = true
        AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Finalizar viaje y tracking")
            .setCancelable(false)
            .setMessage(
                "Hora de partida: ${horaEstimadaPartida}\n" +
                        "Hora de llegada: ${horaLlegada}\n" +
                        "Duración del viaje: $duracionViaje\n\n" +

                        "Cuando finalize el viaje se guardarán todos los datos \n" +
                        "¿Está seguro que desea finalizar el tracking?"
            )
            .setNegativeButton("Cancelar") { _, _ ->
                btnLogout.visibility = View.GONE
                btnLogout.isEnabled = false
                btnCenterMap.visibility = View.VISIBLE
                btnCenterMap.isEnabled = true
            }
            .setPositiveButton("OK") { _, _ ->
                trackingActivo = false
                actualizarBotones()

                // ✅ DETENER AMBOS SISTEMAS
                stopSendingPositionUpdates() // detener updates cada 3 segundos
                stopGettingProgressUpdates() // detener GET cada 30 segundos (NUEVO)



                // 🕒 Calcular hora actual y estimada
                val formatoFull = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val horaLLegada = LocalDateTime.now()

                // Guardar hora real de llegada al backend
                val patchDto = DTOViajePatchRequest(
                    horaLLegada = horaLLegada.format(formatoFull),
                    progresoTrans = null,
                    coordenadaLlegada = end
                )

                CoroutineScope(Dispatchers.IO).launch {
                    viajeId?.let { id ->
                        try {
                            val resp = getBackendRetrofit().patchViaje(id, patchDto)
                            if (resp.isSuccessful) {
                                Log.i("tracking", "✅ Hora de llegada actualizada correctamente")
                            } else {
                                Log.e(
                                    "tracking",
                                    "Error PATCH horaLlegada: ${resp.code()} ${
                                        resp.errorBody()?.string()
                                    }"
                                )
                            }
                        } catch (ex: Exception) {
                            Log.e("tracking", "Exception PATCH horaLlegada", ex)
                        }
                    }
                }

                Toast.makeText(
                    this,
                    "Se ha finalizado correctamente el tracking",
                    Toast.LENGTH_SHORT
                ).show()
                btnLogout.visibility = View.VISIBLE
                btnLogout.isEnabled = true
                btnCenterMap.visibility = View.GONE
                searchContainer.visibility = View.VISIBLE
                etSearchLocation.visibility = View.VISIBLE
                etSearchLocation.isEnabled = true
                btnSearch.visibility = View.VISIBLE
                btnSearch.isEnabled = true
            }
            .show()

    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map

        map.uiSettings.apply {
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
        }

        // Coordenadas aproximadas del centro de Centroamérica
        val centroamerica = LatLng(13.7, -89.2)
        map.moveCamera(
            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                centroamerica,
                6.5f
            )
        )

        map.setMaxZoomPreference(18f) //Zoom máximo

        if (checkLocationPermission()) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRoute() {
        CoroutineScope(Dispatchers.IO).launch {
            val call = getRetrofit().create(ApiService::class.java).getRoute(
                BuildConfig.ORS_API_KEY,
                start,
                end
            )
            if (call.isSuccessful) {
                drawRoute(call.body())
            } else {
                Log.i("Tracking", "Error al obtener ruta")
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun drawRoute(routeResponse: RouteResponse?) {
        val polyLineOptions = PolylineOptions().color("#1E1E1E".toColorInt()).width(10f)

        routeResponse?.features?.first()?.geometry?.coordinates?.forEach {
            polyLineOptions.add(LatLng(it[1], it[0]))
        }

        val segment = routeResponse?.features?.first()?.properties?.segments?.first()
        val durationInSeconds = segment?.duration ?: 0.0
        val durationInMinutes = (durationInSeconds / 60).toInt()
        estimatedDuration = durationInMinutes

        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaPartida = Calendar.getInstance()
        val horaLlegada = Calendar.getInstance()
        horaLlegada.add(Calendar.SECOND, durationInSeconds.toInt())

        horaEstimadaPartida = dateFormat.format(horaPartida.time)
        horaEstimadaLlegada = dateFormat.format(horaLlegada.time)

        runOnUiThread {
            poly = map.addPolyline(polyLineOptions)

            // 1) Dialog "Información del tracking"
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Información del tracking")
                .setCancelable(false)
                .setMessage(
                    "Duración estimada de llegada (sin tráfico): $durationInMinutes min\n" +
                            "Lugar de partida: $startName\n" +
                            "Lugar de llegada: $endName"
                )
                .setPositiveButton("OK") { _, _ ->
                    // aquí abrimos dialog para que ingrese ID del viaje (numérico)
                    showIdInputDialog { idStr ->
                        // callback con el id ingresado (string)
                        val idLong = try { idStr.toLong() } catch (e: Exception) { null }
                        if (idLong == null) {
                            Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show()
                            return@showIdInputDialog
                        }
                        inputViajeId = idLong

                        // ahora abrimos el dialog "¿Comenzar el viaje?"
                        showStartTripConfirmation(idLong)
                    }
                }
                .show()
        }
    }

    // Muestra un AlertDialog con un EditText numérico y devuelve el string al callback
    private fun showIdInputDialog(onIdEntered: (String) -> Unit) {
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Ingrese ID del viaje"
            setPadding(60, 50, 60, 50)
            setTextColor(android.graphics.Color.parseColor("#F2F2F2"))
            setHintTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            background = null // elimina la línea blanca
            //setBackgroundResource(android.R.drawable.edit_text) // opcional, estilo suave
        }

        // Creamos el dialog pero SIN cerrarlo automáticamente
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Ingrese ID del viaje")
            .setView(et)
            .setCancelable(false)
            .setPositiveButton("OK", null) // lo configuramos manualmente abajo
            .setNegativeButton("Cancelar") { _, _ ->
                btnCancelarTracking.visibility = View.GONE
                btnIniciarTracking.visibility = View.VISIBLE
                start = ""
                end = ""
                startName = ""
                endName = ""
                poly?.remove()
                poly = null
                btnLogout.visibility = View.VISIBLE
                btnCenterMap.visibility = View.GONE
                AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Se ha cancelado el tracking")
                    .setMessage("Por favor comience uno nuevo")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val idText = et.text.toString().trim()
                val idLong = idText.toLongOrNull()

                if (idLong == null) {
                    Toast.makeText(this, "Ingrese un número válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 🔎 Verificar si el ID existe antes de cerrar el diálogo
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val resp = getBackendRetrofit().getViajeById(idLong)
                        runOnUiThread {
                            if (resp.isSuccessful) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "ID válido. Puede continuar.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                dialog.dismiss()
                                onIdEntered(idText) // ejecutar callback solo si es válido
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Error: El ID $idLong no existe en la base de datos.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error al verificar ID: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }


    // Muestra confirmación de "¿Comenzar el viaje?" y, si OK -> valida id y hace PUT
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showStartTripConfirmation(idToCheck: Long) {
        AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("¿Comenzar el viaje?")
            .setCancelable(false)
            .setMessage(
                "Se actualizarán los datos del viaje con la información del mapa actual.\n\n" +
                        "Id viaje: $idToCheck\n" +
                        "Hora estimada llegada: $horaEstimadaLlegada\n\n" +
                        "⚠️ Importante: si selecciona 'OK' ya no se podrá cancelar el tracking."
            )
            .setPositiveButton("OK") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val backend = getBackendRetrofit()

                        // 🔎 Verificar si el viaje existe
                        val getResp = backend.getViajeById(idToCheck)
                        if (getResp.code() == 404) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "El ID $idToCheck no existe.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }

                        if (getResp.isSuccessful) {
                            // 🕒 Calcular hora actual y estimada
                            val formatoFull = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                            val horaActual = LocalDateTime.now()
                            val duracionEstim = Duration.ofMinutes(estimatedDuration.toLong())
                            val horaEstimadaLlegada = horaActual.plus(duracionEstim)

                            // 📦 DTO con los campos que vienen de la app
                            val dto = DTOViajePatchRequest(
                                horaSalida = horaActual.format(formatoFull),
                                horaEstimadaLlegada = horaEstimadaLlegada.format(formatoFull),
                                lugarPartida = startName,
                                coordenadaPartida = start,
                                lugarLLegada = endName,
                                coordenadaLlegada = end
                            )

                            Log.d("tracking", "🚀 Enviando PATCH: lugarLlegada=$endName, coordLlegada=$end")

                            // 🔄 PATCH
                            val patchResp = backend.patchViaje(idToCheck, dto)

                            if (patchResp.isSuccessful) {
                                viajeId = idToCheck

                                //  Cambiar la UI correctamente desde el hilo principal
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "El tracking se ha iniciado correctamente.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    searchContainer.visibility = View.GONE
                                    btnFinalizarTracking.visibility = View.VISIBLE
                                    btnFinalizarTracking.isEnabled = true
                                    btnCancelarTracking.visibility = View.GONE
                                    btnCancelarTracking.isEnabled = false

                                    startSendingPositionUpdates() //Patch cada 3 segundos
                                    startGettingProgressUpdates() // GET cada 3 segundos (NUEVO)
                                }

                            } else {
                                val err = patchResp.errorBody()?.string()
                                Log.e("tracking", "PATCH error: ${patchResp} $err")
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Error al actualizar viaje: ${patchResp.code()}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Error al verificar viaje: ${getResp.code()}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e("tracking", "Exception en PATCH viaje", ex)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error de conexión al verificar o actualizar viaje",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Atrás") { _, _ ->
                showIdInputDialog { idStr ->
                    val idLong = try { idStr.toLong() } catch (e: Exception) { null }
                    if (idLong == null) {
                        Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show()
                        return@showIdInputDialog
                    }
                    inputViajeId = idLong
                    showStartTripConfirmation(idLong)
                }
            }
            .show()

    }



    private fun cancelarTracking() {
        trackingActivo = false
        actualizarBotones()

        // Limpiar marcador
        currentMarker?.remove()
        currentMarker = null


        //Lógica para cancelar tracking
        AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("¿Estás seguro que deseas cancelar el tracking?")
            .setCancelable(false)
            .setMessage(
                "Si lo cancelas, se eliminarán todos los datos del tracking y deberás comenzar uno nuevo"
            )
            .setNegativeButton("Atrás") { _, _ ->
                btnIniciarTracking.visibility = View.GONE
                btnIniciarTracking.isEnabled = false
                btnCancelarTracking.visibility = View.VISIBLE
                btnCancelarTracking.isEnabled = true
            }
            .setPositiveButton("Cancelar") { _, _ ->
                //Lógica para cancelar el viaje
                start = ""
                end = ""
                startName = ""
                endName = ""
                poly?.remove()
                poly = null
                AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Se ha eliminado el tracking")
                    .setMessage(
                        "Por favor comience uno nuevo"
                    )
                    .setPositiveButton("OK", null)
            }
            .show()
    }

    private fun getPlaceName(lat: Double, lng: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        val address = addresses?.firstOrNull()
        return when {
            address?.locality != null -> address.locality
            address?.subAdminArea != null -> address.subAdminArea
            address?.adminArea != null -> address.adminArea
            address?.featureName != null -> address.featureName
            else -> "Ubicación desconocida"
        }
    }


    private fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    private fun getBackendRetrofit(): BackendApiService {
        // 🔹 Leer token guardado en SharedPreferences
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)

        // 🔹 Crear cliente HTTP con interceptor para manejar expiración de token
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()

            // Si existe token, agregarlo en la cabecera (como cookie)
            if (token != null) {
                builder.addHeader("Cookie", "authToken=$token")
            }

            val response = chain.proceed(builder.build())

            // 🔻 Detectar token expirado o inválido (401 Unauthorized)
            if (response.code == 401) {
                Log.e("Auth", "❌ Token expirado o inválido. Redirigiendo al login...")

                // Eliminar token guardado
                prefs.edit().remove("token").apply()

                // Redirigir al login en el hilo principal
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Tu sesión ha expirado. Inicia sesión nuevamente.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Crear intent con flags para limpiar el historial de actividades
                    val intent = Intent(this@MainActivity, LoginTransportistas::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }

            response
        }.build()

        // 🔹 Construir Retrofit con el cliente personalizado
        val retrofit = Retrofit.Builder()
            .baseUrl("https://apitrackline-3047cf7af332.herokuapp.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(BackendApiService::class.java)
    }


    private fun startLocationUpdates() {
        // ✅ NO iniciar actualizaciones de ubicación si estamos en modo cliente
        if (fromViajesScreen) return

        val locationRequest = LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            3000 // cada 3 segundos
        ).build()

        val locationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)

                // Mover marcador en tiempo real
                if (currentMarker == null) {
                    currentMarker = map.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Tu ubicación")
                    )
                    map.moveCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            15f
                        )
                    )
                } else {
                    currentMarker?.position = latLng
                }

                // 👉 Verificar desvío de la ruta
                if (routeCoordinates.isNotEmpty() && isOffRoute(latLng, routeCoordinates)) {
                    val now = System.currentTimeMillis()
                    if (now - lastRerouteTime > rerouteCooldownMillis) {
                        Log.i("tracking", "Usuario fuera de la ruta, recalculando...")
                        start = "${location.longitude},${location.latitude}"
                        createRoute()
                        lastRerouteTime = now
                    }
                }
            }
        }

        // Verificación de permisos antes de pedir actualizaciones
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    // Función para comprobar si está fuera de la ruta
    private fun isOffRoute(
        current: LatLng,
        route: List<LatLng>,
        thresholdMeters: Float = 50f
    ): Boolean {
        var minDistance = Float.MAX_VALUE
        val result = FloatArray(1)

        for (point in route) {
            android.location.Location.distanceBetween(
                current.latitude, current.longitude,
                point.latitude, point.longitude,
                result
            )
            if (result[0] < minDistance) {
                minDistance = result[0]
            }
        }
        return minDistance > thresholdMeters
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Se requiere el permiso de ubicación", Toast.LENGTH_SHORT).show()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startSendingPositionUpdates() {
        // Evita duplicar job
        if (positionJob?.isActive == true) return

        positionJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val loc = try {
                    // request single latest location (síncrono mediante suspend)
                    getLastLocationSuspend()
                } catch (e: Exception) {
                    null
                }

                loc?.let { location ->
                    val latLngString = "${location.longitude},${location.latitude}"
                    // Construir progresoTrans: si quieres mantener historial, lo puedes recuperar y append,
                    // pero aquí simplifico enviando la última posición como JSON string
                    //val ts = System.currentTimeMillis()
                    //val pointJson = "${location.longitude},${location.latitude}"

                    // Si quieres append, tendrías que obtener el progresoTrans actual desde el server o
                    // mantener localmente una lista y mandar la lista completa cada patch.
                    val formatoFull = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                    val horaLlegada = LocalDateTime.now()
                    val patchDto = DTOViajePatchRequest(
                        horaLLegada = horaLlegada.format(formatoFull),
                        progresoTrans = latLngString,
                    )

                    viajeId?.let { id ->
                        try {
                            val resp = getBackendRetrofit().patchViaje(id, patchDto)
                            if (!resp.isSuccessful) {
                                val errorBody = resp.errorBody()?.string()
                                Log.e("tracking", "❌ PATCH error ${resp.code()} - ${resp.message()} - body: $errorBody")
                                // ⚠️ Si hay error, esperar más tiempo antes de reintentar
                                kotlinx.coroutines.delay(10000) // 10 segundos extra en caso de error
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Error ${resp.code()}: ${resp.message()}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            else {
                                // En startSendingPositionUpdates (Transportista):
                                Log.d("TRANSPORTISTA", "📍 Enviando posición: id=$id pos=$latLngString cada 3s")
                            }
                        }catch (ex: Exception) {
                            Log.e("tracking", "🚨 Exception en PATCH viaje: ${ex.message}", ex)
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Excepción: ${ex.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                    } ?: run {
                        Log.w("tracking", "No hay viajeId aún, saltando patch")
                    }
                }

                // esperar 5 segundos tras cada petición
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private suspend fun getLastLocationSuspend(): android.location.Location? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            if (!checkLocationPermission()) {
                cont.resume(null) {}
                return@suspendCancellableCoroutine
            }
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) cont.resume(location) {}
                else cont.resume(null) {}
            }.addOnFailureListener {
                cont.resume(null) {}
            }
        }


    private fun stopSendingPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun searchLocation(locationName: String) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(locationName, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                map.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                map.addMarker(
                    com.google.android.gms.maps.model.MarkerOptions()
                        .position(latLng)
                        .title(locationName)
                )
            } else {
                Toast.makeText(this, "No se encontró la ubicación", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al buscar ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cerrarSesion() {
        // Detener todos los procesos en curso
        stopSendingPositionUpdates()
        stopGettingProgressUpdates()

        // Limpiar preferencias
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("token").apply()

        // Mostrar mensaje de confirmación
        Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()

        // Redirigir al login
        val intent = Intent(this, LoginTransportistas::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun mostrarDialogoCerrarSesion() {
        AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Cerrar sesión")
            .setMessage("Al cerrar sesión:\n\n• Se detendrá el tracking en curso\n• Se perderán los datos no guardados\n• Volverás a la pantalla de inicio de sesión")
            .setCancelable(true)
            .setPositiveButton("Cerrar sesión") { dialog, _ ->
                dialog.dismiss()
                cerrarSesion()
            }
            .setNegativeButton("Continuar en la app") { dialog, _ ->
                dialog.dismiss()
                // Opcional: Mostrar mensaje de que se canceló
                Toast.makeText(this, "Continúas en la aplicación", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Más tarde") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchViajeAndDrawRoute(idViaje: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = getBackendRetrofit().getViajeById(idViaje)
                if (resp.isSuccessful) {
                    val apiResponse = resp.body()
                    val viaje = apiResponse?.data
                    if (viaje != null) {

                        Log.d("MainActivity", "Viaje desde API: $viaje")

                        start = parseCoords(viaje.coordenadaPartida)
                        end = parseCoords(viaje.coordenadaLlegada)

                        Log.d("MainActivity", "start=$start, end=$end")

                        if (start.isNotEmpty() && end.isNotEmpty()) {
                            runOnUiThread {
                                createRouteForExistingTrip()
                                // ✅ INICIAR ACTUALIZACIONES PARA CLIENTE
                                viajeId = idViaje
                                startGettingProgressUpdates()//AQUÍ
                            }
                        } else {
                            Log.e("MainActivity", "No hay coordenadas válidas para dibujar ruta")
                            runOnUiThread {
                                AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                                    .setTitle("El tracking no ha comenzado")
                                    .setMessage("El transportista aún no ha iniciado el tracking del viaje.")
                                    .setPositiveButton("Aceptar") { _, _ ->
                                        finish() // Regresa a la actividad anterior (ViajesActivity)
                                    }
                                    .show()
                            }
                        }
                    }
                } else {
                    Log.e("MainActivity", "Error al traer viaje: ${resp.code()}")
                    runOnUiThread {
                        AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                            .setTitle("Error")
                            .setMessage("No se pudo obtener el viaje.")
                            .setPositiveButton("Aceptar") { _, _ ->
                                finish()
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception fetchViajeAndDrawRoute", e)
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                        .setTitle("Error")
                        .setMessage("Excepción al obtener el viaje: ${e.message}")
                        .setPositiveButton("Aceptar") { _, _ ->
                            finish()
                        }
                        .show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRouteForExistingTrip() {
        CoroutineScope(Dispatchers.IO).launch {
            val call = getRetrofit().create(ApiService::class.java).getRoute(
                BuildConfig.ORS_API_KEY,
                start, // LAT, LNG
                end
            )
            if (call.isSuccessful) {
                val routeResponse = call.body()
                val polyLineOptions = PolylineOptions().color("#1E1E1E".toColorInt()).width(10f)
                routeResponse?.features?.first()?.geometry?.coordinates?.forEach {
                    polyLineOptions.add(LatLng(it[1], it[0])) // lat, lng
                }

                routeCoordinates = routeResponse?.features?.first()?.geometry?.coordinates
                    ?.map { LatLng(it[1], it[0]) } ?: emptyList()

                runOnUiThread {
                    poly?.remove()
                    poly = map.addPolyline(polyLineOptions)

                    // Mover cámara al inicio
                    val coords = routeResponse?.features?.first()?.geometry?.coordinates
                    if (!coords.isNullOrEmpty()) {
                        val first = coords.first()
                        map.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                LatLng(first[1], first[0]),
                                15f
                            )
                        )
                    }
                }
            } else {
                Log.e("MainActivity", "Error al obtener ruta: ${call.code()}")
            }
        }
    }

    private fun parseCoords(coord: String?): String {
        if (coord.isNullOrEmpty()) return ""

        // Las coordenadas ya vienen en formato "lng,lat" desde el API
        // No necesitamos intercambiarlas
        val parts = coord.split(",").map { it.trim() }
        if (parts.size != 2) return ""

        // Validar que sean números válidos
        return try {
            val lng = parts[0].toDouble()
            val lat = parts[1].toDouble()
            "$lng,$lat" // Mantener el mismo formato: lng,lat
        } catch (e: NumberFormatException) {
            Log.e("MainActivity", "Coordenadas inválidas: $coord")
            ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startGettingProgressUpdates() {
        // Evita duplicar job
        if (progressJob?.isActive == true) return

        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                viajeId?.let { id ->
                    try {
                        val resp = getBackendRetrofit().getViajeById(id)
                        if (resp.isSuccessful) {
                            val apiResponse = resp.body()
                            val viaje = apiResponse?.data
                            val progresoTrans = viaje?.progresoTrans

                            if (!progresoTrans.isNullOrEmpty()) {
                                // En startGettingProgressUpdates (Cliente):
                                Log.d("CLIENTE", "📡 Obteniendo posición del transportista cada 30s: $progresoTrans")
                                updateTransportPosition(progresoTrans)
                            } else {
                                Log.d("ProgressUpdate", "No hay progreso disponible aún")
                            }
                        } else {
                            Log.e("ProgressUpdate", "Error al obtener progreso: ${resp.code()}")
                        }
                    } catch (e: Exception) {
                        Log.e("ProgressUpdate", "Excepción al obtener progreso", e)
                    }
                }

                // Esperar 10 segundos antes de la siguiente actualización
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    private fun updateTransportPosition(progresoTrans: String) {
        try {
            // Parsear las coordenadas del formato "lng,lat"
            val parts = progresoTrans.split(",").map { it.trim() }
            if (parts.size == 2) {
                val lng = parts[0].toDouble()
                val lat = parts[1].toDouble()
                val position = LatLng(lat, lng)

                runOnUiThread {
                    // ✅ USAR SOLO currentMarker PARA AMBOS CASOS
                    if (currentMarker == null) {
                        currentMarker = map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title("Posición del Transportista")
                        )
                        // Mover cámara a la posición del transportista
                        map.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(position, 15f)
                        )
                    } else {
                        currentMarker?.position = position
                    }
                }
            }
        } catch (e: NumberFormatException) {
            Log.e("ProgressUpdate", "Formato de coordenadas inválido: $progresoTrans")
        } catch (e: Exception) {
            Log.e("ProgressUpdate", "Error al actualizar posición del transportista", e)
        }
    }

    private fun stopGettingProgressUpdates() {
        progressJob?.cancel()
        progressJob = null

        // NO removemos currentMarker aquí porque puede estar en uso por el locationCallback
        // Solo lo removemos si estamos en modo cliente (fromViajesScreen)
        if (fromViajesScreen) {
            currentMarker?.remove()
            currentMarker = null
        }
    }

}

