# TrackLine App Routes - Aplicación Móvil de Rutas y Seguimiento de Viajes en Tiempo Real

![Kotlin](https://img.shields.io/badge/Lenguaje-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Plataforma-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Google Maps](https://img.shields.io/badge/Mapas-Google%20Maps%20SDK-4285F4?style=for-the-badge&logo=googlemaps&logoColor=white)
![OpenRouteService](https://img.shields.io/badge/Rutas-OpenRouteService-2E7D32?style=for-the-badge&logo=openstreetmap&logoColor=white)
![Retrofit](https://img.shields.io/badge/HTTP-Retrofit%20%7C%20OkHttp-48B983?style=for-the-badge&logo=square&logoColor=white)

## Descripción del Proyecto

**TrackLine App Routes** es la aplicación Android del ecosistema **Trackline**, un sistema de gestión y rastreo de órdenes de servicio para una agencia de logística y trámites aduanales. La app consume la API REST [ApiTrackline](https://github.com/MarioIvan44/ApiTrackline) y ofrece dos experiencias según el rol del usuario:

- El **transportista** traza la ruta de su viaje sobre el mapa y transmite su ubicación en tiempo real mientras conduce.
- El **cliente** consulta sus viajes y sigue en el mapa el avance del transportista hasta la entrega.

## Funcionalidades Principales

### Módulo del Transportista
- **Trazado de rutas:** Toma la ubicación actual como punto de partida y permite seleccionar el destino tocando el mapa o buscando un lugar.
- **Rutas para vehículos de carga:** Calcula la ruta con el perfil `driving-hgv` (vehículos pesados) de OpenRouteService y la dibuja como polilínea sobre Google Maps.
- **Inicio de viaje por ID:** Vincula el recorrido a un viaje existente del backend, validando el ID antes de iniciar.
- **Transmisión de ubicación en tiempo real:** Envía las coordenadas del transportista al backend cada **5 segundos** durante el viaje.
- **Finalización y cancelación:** Registra la hora real de llegada, calcula la duración total del viaje o cancela el seguimiento activo.

### Módulo del Cliente
- **Listado de viajes:** Consulta paginada de los viajes asociados a su cuenta.
- **Búsqueda por ID:** Localiza un viaje específico dentro de su historial.
- **Seguimiento en vivo:** Abre el viaje en el mapa, dibuja la ruta y actualiza la posición del transportista cada **10 segundos**.

### Sesión y Seguridad
- **Inicio de sesión contra la API:** Autenticación con usuario y contraseña; el token JWT se obtiene de la cookie `authToken` y se guarda en `SharedPreferences`.
- **Redirección por rol:** Tras consultar `/api/auth/me`, la app dirige al transportista al mapa y al cliente a su lista de viajes; otros roles no tienen acceso.
- **Manejo de sesión expirada:** Si el token es inválido o expira, la app cierra la sesión y regresa al login.

---

## Roles y Permisos

| Rol | Pantalla inicial | Funciones Clave |
| :--- | :--- | :--- |
| **Transportista** | Mapa de rutas (`MainActivity`) | Trazar rutas, iniciar viajes, transmitir su ubicación y finalizar recorridos. |
| **Cliente** | Mis viajes (`ViajesActivity`) | Consultar sus viajes y seguir en tiempo real la ubicación del transportista. |

---

## Estructura del Proyecto

```
app/src/main/
├── AndroidManifest.xml              # Permisos, clave de Google Maps y declaración de actividades
├── java/com/example/apirutemap/
│   ├── LoginActivity.kt             # Pantalla de login y redirección según el rol
│   ├── MainActivity.kt              # Mapa, trazado de rutas y seguimiento en tiempo real
│   ├── ViajesActivty.kt             # Listado paginado y búsqueda de viajes del cliente
│   ├── ApiService.kt                # Cliente Retrofit para OpenRouteService
│   ├── BackendApiService.kt         # Endpoints de ApiTrackline consumidos por la app
│   ├── RetrofitClient.kt            # Configuración de Retrofit con la cookie de sesión
│   ├── ApiResponse.kt               # Envoltorio genérico de respuestas del backend
│   ├── PageResponse.kt              # Modelo de respuestas paginadas
│   ├── RouteResponse.kt             # Modelo de la respuesta de rutas
│   ├── DTOViajeResponse.kt          # Modelo de un viaje
│   ├── requests/                    # DTOs de envío (crear y actualizar viaje)
│   └── ui/theme/                    # Colores, tipografía y tema
└── res/
    ├── layout/                      # Pantallas: login, mapa, lista y tarjetas de viajes
    └── values/                      # Strings, colores y temas
```

### Endpoints de ApiTrackline Consumidos

| Método | Endpoint | Uso en la app |
| :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Iniciar sesión y obtener el token |
| `GET` | `/api/auth/me` | Obtener el rol y el ID del usuario autenticado |
| `GET` | `/apiViaje/buscarPorId/{id}` | Validar un viaje y consultar su progreso |
| `GET` | `/apiViaje/datosViaje/userId/{idUsuario}` | Listado paginado de viajes del cliente |
| `POST` | `/apiViaje/crear` | Registrar un viaje |
| `PUT` | `/apiViaje/actualizar/{id}` | Actualizar un viaje completo |
| `PATCH` | `/apiViaje/actualizarParcial/{id}` | Enviar ubicación, hora de salida y hora de llegada |

---

## Stack Tecnológico y Dependencias

- **Kotlin:** Lenguaje principal de la aplicación.
- **Android SDK (API 24 - 35):** Compatible desde Android 7.0 hasta Android 15.
- **Android Views (XML) + AppCompat:** Construcción de las pantallas y componentes de la interfaz.
- **Google Maps SDK for Android:** Visualización del mapa, marcadores y polilíneas de la ruta.
- **Android Geocoder:** Búsqueda de lugares por nombre y conversión de coordenadas a direcciones legibles.
- **Google Play Services Location (FusedLocationProviderClient):** Obtención de la ubicación del dispositivo en tiempo real.
- **OpenRouteService API:** Cálculo de rutas optimizadas para vehículos de carga.
- **Retrofit 3 + Gson Converter:** Consumo de las APIs REST y conversión de JSON a modelos de Kotlin.
- **OkHttp:** Cliente HTTP e interceptor que adjunta la cookie de sesión a cada petición.
- **Kotlin Coroutines:** Peticiones de red en segundo plano y ciclos periódicos de envío y consulta de ubicación.
- **SharedPreferences:** Almacenamiento local del token, rol e ID del usuario.
- **Jetpack Compose / Material 3:** Configuración de tema incluida en el proyecto.

---

## Configuración del Entorno y Ejecución

### Requisitos Previos
- [Android Studio](https://developer.android.com/studio) (versión reciente con soporte para AGP 8.12)
- JDK 11 o superior (incluido en Android Studio)
- Dispositivo físico o emulador con **Google Play Services** y ubicación activada
- Clave de [Google Maps Platform](https://developers.google.com/maps/documentation/android-sdk/get-api-key) con Maps SDK for Android habilitado
- Clave de [OpenRouteService](https://openrouteservice.org/dev/#/signup)
- La API [ApiTrackline](https://github.com/MarioIvan44/ApiTrackline) desplegada o corriendo localmente

### 1. Clonar el repositorio

```bash
git clone https://github.com/MarioIvan44/TracklineAppRoutes.git
```

Abre la carpeta del proyecto en Android Studio y espera a que Gradle sincronice las dependencias.

### 2. Configurar las claves y la URL del backend

| Configuración | Archivo | Ubicación |
| :--- | :--- | :--- |
| Clave de Google Maps | `app/src/main/AndroidManifest.xml` | `meta-data` con `com.google.android.geo.API_KEY` |
| Clave de OpenRouteService | `MainActivity.kt` | Función `createRoute()` |
| URL del backend | `RetrofitClient.kt` y `LoginActivity.kt` | Constantes `BASE_URL` y `apiUrl` |

> Por defecto la app apunta a la instancia de ApiTrackline desplegada en Heroku. Para usar un backend local desde el emulador, utiliza `http://10.0.2.2:8080/`.

### 3. Ejecutar la aplicación

Desde Android Studio, selecciona un dispositivo o emulador y presiona **Run**. También puedes compilar desde la terminal:

```bash
# Linux / Mac
./gradlew installDebug

# Windows
gradlew.bat installDebug
```

Al abrir la app, concede el permiso de ubicación e inicia sesión con una cuenta de rol **Transportista** o **Cliente** registrada en ApiTrackline.

---

## Convenciones de Nomenclatura

| Elemento | Convención | Ejemplo |
| :--- | :--- | :--- |
| Actividades | `PascalCase` + sufijo `Activity` | `MainActivity`, `ViajesActivity` |
| Interfaces de servicios Retrofit | `PascalCase` + sufijo `Service` | `ApiService`, `BackendApiService` |
| DTOs | Prefijo `DTO` + entidad + tipo | `DTOViajeRequest`, `DTOViajePatchRequest`, `DTOViajeResponse` |
| Modelos de respuesta | `PascalCase` + sufijo `Response` | `RouteResponse`, `PageResponse` |
| Funciones | `camelCase`, verbo en español | `iniciarTracking`, `finalizarTracking`, `cargarViajes` |
| Variables | `camelCase` | `horaLlegada`, `trackingActivo`, `paginaActual` |
| Layouts XML | `snake_case` | `activity_main.xml`, `item_viaje.xml`, `login_transportista.xml` |
| Textos visibles al usuario | Siempre en **español** | `"Credenciales incorrectas"`, `"Bienvenido"` |

---

## Proyectos Relacionados

| Proyecto | Descripción |
| :--- | :--- |
| [ApiTrackline](https://github.com/MarioIvan44/ApiTrackline) | API REST en Spring Boot que provee la autenticación, los viajes y el progreso consumidos por esta app |

---

## Autor

**Mario Iván Vásquez**

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/mario-v%C3%A1squez-6a4948346/)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/MarioIvan44)
