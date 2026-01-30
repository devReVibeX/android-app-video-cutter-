package com.example.videocutter

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startTime = findViewById<EditText>(R.id.startTime)
        val endTime = findViewById<EditText>(R.id.endTime)
        val cutBtn = findViewById<Button>(R.id.cutBtn)

        // ⚠️ Input video path (change as needed)
        val inputVideo =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + "/input.mp4"

        // Output video path
        val outputVideo =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + "/cut_video.mp4"

        cutBtn.setOnClickListener {

            val start = startTime.text.toString()
            val end = endTime.text.toString()

            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Start aur End time dalo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val command =
                "-i \"$inputVideo\" -ss $start -to $end -c copy \"$outputVideo\""

            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode

                runOnUiThread {
                    if (ReturnCode.isSuccess(returnCode)) {
                        Toast.makeText(
                            this,
                            "Video cut ho gaya!\nSaved in Downloads",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Error while cutting video",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}