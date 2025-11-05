package com.chowdhury.shibasitoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private val RABBITMQ_HOST_IP = "192.168.0.9" // (Tu IP)

    // UI Elements
    private lateinit var etClienteOrigen: EditText
    private lateinit var etClienteDestino: EditText
    private lateinit var etMonto: EditText
    private lateinit var btnTransferir: Button
    private lateinit var btnConsultarSaldo: Button
    private lateinit var tvLogs: TextView

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var clienteIdLogueado: String = "CL001" // Default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Recibir el ID de Cliente desde el Login
        clienteIdLogueado = intent.getStringExtra("CLIENTE_ID") ?: "CL001"

        // Enlazar vistas
        etClienteOrigen = findViewById(R.id.etClienteOrigen)
        etClienteDestino = findViewById(R.id.etClienteDestino)
        etMonto = findViewById(R.id.etMonto)
        btnTransferir = findViewById(R.id.btnTransferir)
        btnConsultarSaldo = findViewById(R.id.btnConsultarSaldo)
        tvLogs = findViewById(R.id.tvLogs)

        // 2. Establecer el ID de Cliente en la UI
        etClienteOrigen.setText(clienteIdLogueado)

        log("App iniciada. Host: $RABBITMQ_HOST_IP")
        log("Usuario logueado: $clienteIdLogueado")

        // Configurar botones
        btnTransferir.setOnClickListener {
            val idDestino = etClienteDestino.text.toString()
            val monto = etMonto.text.toString()
            val payload = """
                {"idClienteOrigen": "$clienteIdLogueado", "idClienteDestino": "$idDestino", "monto": $monto}
            """.trimIndent()
            executeRpc("banco.transferir", payload)
        }

        btnConsultarSaldo.setOnClickListener {
            val payload = """
                {"idCliente": "$clienteIdLogueado"}
            """.trimIndent()
            executeRpc("banco.consulta.saldo", payload)
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            tvLogs.append("\n$message")
        }
    }

    private fun executeRpc(routingKey: String, payload: String) {
        log("-> [BG] Enviando Petición: $routingKey")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RpcClient(RABBITMQ_HOST_IP).use { rpcClient ->
                    val response = rpcClient.call(routingKey, payload)
                    log("<- [BG] Respuesta Recibida: $response")
                    withContext(Dispatchers.Main) {
                        // 3. Pasar el routingKey para saber si es transferencia
                        showAlert(response, routingKey)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log("Error en hilo RPC: ${e.message}")
                withContext(Dispatchers.Main) {
                    showAlert("""
                        {"status":"ERROR", "message":"${e.message}"}
                    """.trimIndent(), routingKey)
                }
            }
        }
    }

    private fun showAlert(responseJson: String, routingKey: String) {
        try {
            val jsonElement = JsonParser.parseString(responseJson)
            val isError = responseJson.contains("\"status\":\"ERROR\"")

            // 4. Lógica para la Animación
            if (!isError && routingKey == "banco.transferir") {
                // ¡ÉXITO DE TRANSFERENCIA! Lanzar la animación.
                val intent = Intent(this, SuccessAnimationActivity::class.java)
                startActivity(intent)
            } else {
                // Mostrar el popup normal para todo lo demás
                val prettyJson = gson.toJson(jsonElement)
                val title = if (isError) "Error del Servidor" else "Respuesta Exitosa"
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(prettyJson)
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error de Formato")
                .setMessage(responseJson)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}