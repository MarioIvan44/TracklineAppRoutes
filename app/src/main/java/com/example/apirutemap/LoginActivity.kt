package com.example.apirutemap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginTransportistas : AppCompatActivity() {

    private lateinit var txtUser: EditText
    private lateinit var txtPassword: EditText
    private lateinit var btnIngresar: Button
    private lateinit var cbRecordarme: CheckBox

    private val apiUrl = "https://apitrackline-3047cf7af332.herokuapp.com/api/auth/login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.login_transportista)

        txtUser = findViewById(R.id.txtUser)
        txtPassword = findViewById(R.id.txtPassword)
        btnIngresar = findViewById(R.id.btnIngresar)
        cbRecordarme = findViewById(R.id.cbRecordarme)

        // Si ya hay sesión guardada, entrar directamente
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val rolGuardado = prefs.getString("rol", null)

        if (token != null && rolGuardado != null) {
            redirigirSegunRol(rolGuardado)
            return
        }

        btnIngresar.setOnClickListener {
            val usuario = txtUser.text.toString().trim()
            val contrasenia = txtPassword.text.toString().trim()

            if (usuario.isEmpty() || contrasenia.isEmpty()) {
                Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            iniciarSesion(usuario, contrasenia)
        }
    }

    private fun iniciarSesion(usuario: String, contrasenia: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("usuario", usuario)
                    put("contrasenia", contrasenia)
                }

                val client = OkHttpClient.Builder()
                    .followRedirects(false)
                    .build()

                val body = json.toString().toRequestBody("application/json".toMediaType())

                // 1️⃣ PRIMERA PETICIÓN: login
                val requestLogin = Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .build()

                val responseLogin = client.newCall(requestLogin).execute()
                val responseBodyLogin = responseLogin.body?.string()
                Log.d("Login", "🧾 Respuesta del login: $responseBodyLogin")

                if (!responseLogin.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginTransportistas, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2️⃣ EXTRAER TOKEN/COKIE
                val cookies = responseLogin.headers("Set-Cookie")
                val token = cookies.find { it.startsWith("authToken") }
                    ?.substringAfter("=")
                    ?.substringBefore(";")

                if (token == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginTransportistas, "No se encontró token en la cookie", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3️⃣ SEGUNDA PETICIÓN: obtener datos del usuario
                val requestUser = Request.Builder()
                    .url("https://apitrackline-3047cf7af332.herokuapp.com/api/auth/me")
                    .addHeader("Cookie", "authToken=$token")
                    .get()
                    .build()

                val responseUser = client.newCall(requestUser).execute()
                val responseBodyUser = responseUser.body?.string()
                Log.d("Login", "🧾 Respuesta del /me: $responseBodyUser")

                if (!responseUser.isSuccessful || responseBodyUser.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginTransportistas, "Error al obtener datos del usuario", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 4️⃣ PARSEAR EL JSON del /me
                val jsonResponse = JSONObject(responseBodyUser)
                val userObj = jsonResponse.getJSONObject("user")
                val rol = userObj.getString("rol")
                val idUsuario = userObj.getLong("id")

                // 5️⃣ VERIFICAR ROL
                withContext(Dispatchers.Main) {
                    if (rol == "Cliente" || rol == "Transportista") {
                        guardarSesion(token, rol, idUsuario)
                        Toast.makeText(this@LoginTransportistas, "Bienvenido, $rol", Toast.LENGTH_SHORT).show()
                        redirigirSegunRol(rol)
                    } else {
                        Toast.makeText(this@LoginTransportistas, "Acceso denegado, rol no autorizado", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("Login", "Error: ${e.message}")
                    Toast.makeText(this@LoginTransportistas, "Error al conectar con el servidor", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun guardarSesion(token: String, rol: String, idUsuario: Long) {
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("token", token)
            .putString("rol", rol)
            .putLong("idUsuario", idUsuario)
            .apply()
        Log.d("Login", "✅ Token y rol guardados: $token | $rol")
    }

    private fun redirigirSegunRol(rol: String) {
        when (rol) {
            "Cliente" -> {
                val intent = Intent(this, ViajesActivity::class.java)
                startActivity(intent)
                finish()
            }
            "Transportista" -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            else -> {
                Toast.makeText(this, "Rol no autorizado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
