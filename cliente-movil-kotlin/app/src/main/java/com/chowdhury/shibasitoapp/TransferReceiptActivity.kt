package com.chowdhury.shibasitoapp


import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.VideoView

class TransferReceiptActivity : AppCompatActivity() {

    private val ANIMATION_DURATION_MS = 6000L // Duración total de la animación (6 segundos)
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_receipt)

        // Enlazar vistas
        val tvMonto = findViewById<TextView>(R.id.tvMonto)
        val tvOrigen = findViewById<TextView>(R.id.tvOrigen)
        val tvDestino = findViewById<TextView>(R.id.tvDestino)
        val tvNuevoSaldo = findViewById<TextView>(R.id.tvNuevoSaldo)
        val videoView = findViewById<VideoView>(R.id.videoView)

        // 1. Obtener datos del Intent
        val monto = intent.getDoubleExtra("MONTO", 0.0)
        val idOrigen = intent.getStringExtra("ID_ORIGEN") ?: "Desconocido"
        val idDestino = intent.getStringExtra("ID_DESTINO") ?: "Desconocido"
        val nuevoSaldo = intent.getDoubleExtra("NUEVO_SALDO", 0.0)

        // 2. Mostrar los datos en la UI
        tvMonto.text = "Monto: S/ %.2f".format(monto)
        tvOrigen.text = "De: $idOrigen"
        tvDestino.text = "Para: $idDestino"
        tvNuevoSaldo.text = "Nuevo Saldo: S/ %.2f".format(nuevoSaldo)

        // 3. Configurar el Video en bucle
        val videoName = "transfer_success" // Tu video mp4 en res/raw
        val videoPath = "android.resource://" + packageName + "/raw/" + videoName
        val uri = Uri.parse(videoPath)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true // ¡Reproducir en bucle!
            mp.start()
        }

        // Si por alguna razón el video no está listo, asegúrate de iniciar el bucle
        // Esto previene que se quede sin iniciar si hay un problema
        if (!videoView.isPlaying) {
            videoView.start()
        }

        // 4. Temporizador para cerrar la actividad
        handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (!isFinishing) { // Prevenir cerrar si ya se está cerrando
                finish()
            }
        }, ANIMATION_DURATION_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Asegurarse de detener el video y remover el callback del handler
        findViewById<VideoView>(R.id.videoView).stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }
}