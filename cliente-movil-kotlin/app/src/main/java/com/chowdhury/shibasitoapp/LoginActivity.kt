package com.chowdhury.shibasitoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etClienteLogin = findViewById<EditText>(R.id.etClienteLogin)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val clienteId = etClienteLogin.text.toString()

            // 3. Validación Ficticia (como pediste)
            // Solo revisa que no esté vacío. No consulta la BD.
            if (clienteId.isNotBlank()) {
                // Iniciar la Actividad Principal
                val intent = Intent(this, MainActivity::class.java)
                // Enviar el ID de cliente a la siguiente pantalla
                intent.putExtra("CLIENTE_ID", clienteId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Por favor ingresa un ID de Cliente", Toast.LENGTH_SHORT).show()
            }
        }
    }
}