package com.chowdhury.shibasitoapp

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.VideoView
import androidx.core.net.toUri

class SuccessAnimationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_success)

        val videoView = findViewById<VideoView>(R.id.videoView)

        // (Asegúrate de que tu video se llame 'transfer_success.mp4' en res/raw)
        val videoName = "transfer_success"

        val videoPath = "android.resource://" + packageName + "/raw/" + videoName
        val uri = videoPath.toUri()

        videoView.setVideoURI(uri)

        // Cerrar esta pantalla cuando el video termine
        videoView.setOnCompletionListener {
            finish()
        }

        videoView.start()
    }
}