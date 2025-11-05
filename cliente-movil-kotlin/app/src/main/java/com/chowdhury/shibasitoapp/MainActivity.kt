package com.chowdhury.shibasitoapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*

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
    // Variables para capturar los datos de la transferencia antes de enviarlos
    private var lastTransferMonto: Double = 0.0
    private var lastTransferDestino: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clienteIdLogueado = intent.getStringExtra("CLIENTE_ID") ?: "CL001"

        // Enlazar vistas
        etClienteOrigen = findViewById(R.id.etClienteOrigen)
        etClienteDestino = findViewById(R.id.etClienteDestino)
        etMonto = findViewById(R.id.etMonto)
        btnTransferir = findViewById(R.id.btnTransferir)
        btnConsultarSaldo = findViewById(R.id.btnConsultarSaldo)
        tvLogs = findViewById(R.id.tvLogs)

        etClienteOrigen.setText(clienteIdLogueado)

        log("App iniciada. Host: $RABBITMQ_HOST_IP")
        log("Usuario logueado: $clienteIdLogueado")

        // Configurar botones
        btnTransferir.setOnClickListener {
            val idDestino = etClienteDestino.text.toString()
            val montoStr = etMonto.text.toString()

            // Guardar los datos antes de la llamada RPC
            lastTransferMonto = montoStr.toDoubleOrNull() ?: 0.0
            lastTransferDestino = idDestino

            val payload = """
                {"idClienteOrigen": "$clienteIdLogueado", "idClienteDestino": "$idDestino", "monto": $montoStr}
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

            if (!isError && routingKey == "banco.transferir") {
                // Parsear la respuesta para obtener el nuevo saldo
                val responseObj = gson.fromJson(responseJson, Map::class.java)
                val data = responseObj["data"] as? Map<*, *>
                val nuevoSaldo = (data?.get("nuevo_saldo") as? Double) ?: 0.0

                // Lanzar la nueva actividad de recibo con los datos
                val intent = Intent(this, TransferReceiptActivity::class.java).apply {
                    putExtra("MONTO", lastTransferMonto)
                    putExtra("ID_ORIGEN", clienteIdLogueado)
                    putExtra("ID_DESTINO", lastTransferDestino)
                    putExtra("NUEVO_SALDO", nuevoSaldo)
                }
                startActivity(intent)
            } else {
                // Mostrar el popup normal para errores o consultas de saldo
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