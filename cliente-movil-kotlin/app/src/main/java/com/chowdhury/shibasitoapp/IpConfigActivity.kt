package com.chowdhury.shibasitoapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class IpConfigActivity : AppCompatActivity() {

    // Usaremos SharedPreferences para guardar la IP
    companion object {
        const val PREFS_NAME = "ShibasitoPrefs"
        const val KEY_RABBITMQ_IP = "RabbitMQ_IP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Revisar si ya hay una IP guardada
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIp = prefs.getString(KEY_RABBITMQ_IP, null)

        if (savedIp != null) {
            // Si ya tenemos IP, saltamos directo al Login
            goToLogin()
            return // Importante: no continuar con el resto de onCreate
        }

        // Si no hay IP, mostramos la pantalla de configuración
        setContentView(R.layout.activity_ip_config)

        val etIpAddress = findViewById<EditText>(R.id.etIpAddress)
        val btnGuardarIp = findViewById<Button>(R.id.btnGuardarIp)

        btnGuardarIp.setOnClickListener {
            val ip = etIpAddress.text.toString()
            if (ip.isNotBlank() && ip.contains(".")) {
                // 2. Guardar la IP
                prefs.edit()
                    .putString(KEY_RABBITMQ_IP, ip)
                    .apply()

                // 3. Ir al Login
                goToLogin()
            } else {
                Toast.makeText(this, "Por favor ingresa una IP válida", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Cierra esta actividad para que el usuario no pueda volver a ella
    }
}