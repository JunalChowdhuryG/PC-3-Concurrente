package com.chowdhury.shibasitoapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // --- Variables de UI ---
    private lateinit var tvBienvenida: TextView
    private lateinit var tvSaldo: TextView
    private lateinit var tvHistorial: TextView
    private lateinit var etClienteDestino: EditText
    private lateinit var etMonto: EditText
    private lateinit var btnTransferir: Button
    private lateinit var btnActualizar: Button

    private lateinit var btnCambiarIp: Button

    // --- Variables de Lógica ---
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var clienteIdLogueado: String = "CL001"
    private var rabbitMqHostIp: String = "localhost"

    private var lastTransferMonto: Double = 0.0
    private var lastTransferDestino: String = ""

    private val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(IpConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
        rabbitMqHostIp = prefs.getString(IpConfigActivity.KEY_RABBITMQ_IP, "localhost")!!
        clienteIdLogueado = intent.getStringExtra("CLIENTE_ID") ?: "CL001"

        tvBienvenida = findViewById(R.id.tvBienvenida)
        tvSaldo = findViewById(R.id.tvSaldo)
        tvHistorial = findViewById(R.id.tvHistorial)
        etClienteDestino = findViewById(R.id.etClienteDestino)
        etMonto = findViewById(R.id.etMonto)
        btnTransferir = findViewById(R.id.btnTransferir)
        btnActualizar = findViewById(R.id.btnActualizar)
        btnCambiarIp = findViewById(R.id.btnCambiarIp)

        tvBienvenida.text = "Bienvenido, $clienteIdLogueado"
        log("App iniciada. Host: $rabbitMqHostIp. Usuario: $clienteIdLogueado")

        btnActualizar.setOnClickListener {
            cargarDatosDelBanco()
        }

        btnTransferir.setOnClickListener {
            val idDestino = etClienteDestino.text.toString()
            val montoStr = etMonto.text.toString()

            lastTransferMonto = montoStr.toDoubleOrNull() ?: 0.0
            lastTransferDestino = idDestino

            val payload = """
                {"idClienteOrigen": "$clienteIdLogueado", "idClienteDestino": "$idDestino", "monto": $montoStr}
            """.trimIndent()
            executeRpc("banco.transferir", payload)
        }

        btnCambiarIp.setOnClickListener {
            // 1. Borrar la IP guardada
            val prefs = getSharedPreferences(IpConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(IpConfigActivity.KEY_RABBITMQ_IP)
                .apply()

            log("IP borrada. Reiniciando a Configuración.")

            // 2. Reiniciar la app volviendo a la pantalla de IP
            val intent = Intent(this, IpConfigActivity::class.java)
            // Estas "flags" limpian el historial de pantallas
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // Cierra esta actividad
        }
        cargarDatosDelBanco()
    }



    private fun cargarDatosDelBanco() {
        log("Actualizando datos del banco...")
        tvSaldo.text = "Cargando..."
        tvHistorial.text = "Cargando..."
        executeRpc("banco.consulta.saldo", """{"idCliente": "$clienteIdLogueado"}""")
        executeRpc("banco.historial", """{"idCliente": "$clienteIdLogueado"}""")
    }

    private fun log(message: String) {
        println(message)
    }

    private fun executeRpc(routingKey: String, payload: String) {
        log("-> [BG] Enviando Petición: $routingKey")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RpcClient(rabbitMqHostIp).use { rpcClient ->
                    val response = rpcClient.call(routingKey, payload)
                    log("<- [BG] Respuesta Recibida: $response")
                    withContext(Dispatchers.Main) {
                        handleRpcResponse(response, routingKey)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log("Error en hilo RPC: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleRpcResponse("""
                        {"status":"ERROR", "message":"${e.message}"}
                    """.trimIndent(), routingKey)
                }
            }
        }
    }

    // --- FUNCIÓN CORREGIDA ---
    private fun handleRpcResponse(responseJson: String, routingKey: String) {
        try {
            val jsonElement = JsonParser.parseString(responseJson)
            val responseObj = jsonElement.asJsonObject
            val isError = responseObj.get("status").asString == "ERROR"

            if (isError) {
                // Los errores SÍ se muestran como popup
                showErrorAlert(responseObj.get("message").asString)
                if (routingKey == "banco.consulta.saldo") tvSaldo.text = "Error"
                if (routingKey == "banco.historial") tvHistorial.text = "Error al cargar"
                return
            }

            // --- CORRECCIÓN ---
            // Movemos la extracción de 'data' DENTRO del 'when'

            when (routingKey) {
                "banco.consulta.saldo" -> {
                    // Aquí 'data' SÍ es un Objeto
                    val data = responseObj.get("data").asJsonObject
                    val saldo = data.get("saldo").asDouble
                    tvSaldo.text = "S/ %.2f".format(saldo)
                }

                "banco.historial" -> {
                    // Aquí 'data' es un Array
                    val historialArray = responseObj.get("data").asJsonArray
                    if (historialArray.size() == 0) {
                        tvHistorial.text = "No hay movimientos."
                        return // Salir temprano si no hay nada que mostrar
                    }

                    val historialTexto = StringBuilder()
                    for (item in historialArray) {
                        val transaccion = item.asJsonObject
                        // (Manejar el caso de que 'fecha' sea null si la BD lo permite)
                        val fechaStr = transaccion.get("fecha")?.asString ?: "fecha-desconocida"
                        val tipo = transaccion.get("tipo")?.asString ?: "tipo-desconocido"
                        val monto = transaccion.get("monto")?.asString ?: "0.00"

                        // Formatear la fecha
                        try {
                            val fecha = dateParser.parse(fechaStr)
                            val fechaFormateada = dateFormatter.format(fecha!!)
                            historialTexto.append("$fechaFormateada - $tipo: S/ $monto\n")
                        } catch (e: Exception) {
                            historialTexto.append("$fechaStr - $tipo: S/ $monto\n")
                        }
                    }
                    tvHistorial.text = historialTexto.toString()
                }

                "banco.transferir" -> {
                    // Aquí 'data' es un Objeto
                    val data = responseObj.get("data").asJsonObject
                    val nuevoSaldo = data.get("nuevo_saldo").asDouble

                    // Actualizar el saldo en la UI inmediatamente
                    tvSaldo.text = "S/ %.2f".format(nuevoSaldo)

                    // Lanzar la actividad de recibo
                    val intent = Intent(this, TransferReceiptActivity::class.java).apply {
                        putExtra("MONTO", lastTransferMonto)
                        putExtra("ID_ORIGEN", clienteIdLogueado)
                        putExtra("ID_DESTINO", lastTransferDestino)
                        putExtra("NUEVO_SALDO", nuevoSaldo)
                    }
                    startActivity(intent)

                    // (Opcional) Recargar el historial después de la transferencia
                    executeRpc("banco.historial", """{"idCliente": "$clienteIdLogueado"}""")
                }
            }

        } catch (e: Exception) {
            // Este es el error que estabas viendo
            showErrorAlert("Error al procesar respuesta: ${e.message}")
            if (routingKey == "banco.historial") {
                tvHistorial.text = "Error: La respuesta no es un JSON válido."
            }
        }
    }

    private fun showErrorAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}