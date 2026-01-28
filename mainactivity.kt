package com.example.videoclipcutter

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.transformer.TransformationException
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.Transformer
import java.io.File

class MainActivity : AppCompatActivity() {

    // ---------------- UI ----------------
    private lateinit var txtVideoInfo: TextView
    private lateinit var txtClipList: TextView

    private var videoUri: Uri? = null
    private var videoDurationMs: Long = 0L

    private val clips = mutableListOf<Clip>()

    // ---------------- VIDEO PICKER ----------------
    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { handlePickedVideo(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickVideo = findViewById<Button>(R.id.btnPickVideo)
        val btnAddClip = findViewById<Button>(R.id.btnAddClip)
        val btnCut = findViewById<Button>(R.id.btnCut)

        val edtClipName = findViewById<EditText>(R.id.edtClipName)

        val sHH = findViewById<EditText>(R.id.startHH)
        val sMM = findViewById<EditText>(R.id.startMM)
        val sSS = findViewById<EditText>(R.id.startSS)

        val eHH = findViewById<EditText>(R.id.endHH)
        val eMM = findViewById<EditText>(R.id.endMM)
        val eSS = findViewById<EditText>(R.id.endSS)

        txtVideoInfo = findViewById(R.id.txtVideoInfo)
        txtClipList = findViewById(R.id.txtClipList)

        // ---------------- PICK VIDEO ----------------
        btnPickVideo.setOnClickListener {
            pickVideo.launch(arrayOf("video/*"))
        }

        // ---------------- ADD CLIP ----------------
        btnAddClip.setOnClickListener {

            if (videoUri == null) {
                showError("Please select a video first")
                return@setOnClickListener
            }

            val startSec = toSeconds(sHH, sMM, sSS)
            val endSec = toSeconds(eHH, eMM, eSS)

            if (startSec == null || endSec == null) {
                showError("Invalid time (MM & SS must be < 60)")
                return@setOnClickListener
            }

            if (endSec <= startSec) {
                showError("End time must be greater than start time")
                return@setOnClickListener
            }

            if (endSec * 1000L > videoDurationMs) {
                showError(
                    "End exceeds video length (${formatMs(videoDurationMs)})"
                )
                return@setOnClickListener
            }

            val name =
                if (edtClipName.text.isNullOrBlank())
                    "clip_${clips.size + 1}"
                else edtClipName.text.toString().trim()

            clips.add(
                Clip(
                    name = name,
                    startMs = startSec * 1000L,
                    endMs = endSec * 1000L
                )
            )

            updateClipList()

            edtClipName.text.clear()
            sHH.text.clear(); sMM.text.clear(); sSS.text.clear()
            eHH.text.clear(); eMM.text.clear(); eSS.text.clear()

            hideKeyboard()
        }

        // ---------------- LONG PRESS DELETE (DESIRED CLIP) ----------------
        txtClipList.setOnLongClickListener {
            if (clips.isEmpty()) return@setOnLongClickListener true

            val items = clips.map {
                "${it.name} (${formatMs(it.startMs)} → ${formatMs(it.endMs)})"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Delete clip")
                .setItems(items) { _, index ->
                    clips.removeAt(index)
                    updateClipList()
                }
                .setNegativeButton("Cancel", null)
                .show()

            true
        }

        // ---------------- EXPORT (REAL CUT) ----------------
        btnCut.setOnClickListener {
            when {
                videoUri == null ->
                    showError("No video selected")

                clips.isEmpty() ->
                    showError("No clips added")

                else ->
                    exportClipsSequentially()
            }
        }
    }

    // ---------------- VIDEO INFO ----------------
    private fun handlePickedVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            videoUri = uri

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            videoDurationMs =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
            retriever.release()

            txtVideoInfo.text =
                "Video selected\nDuration: ${formatMs(videoDurationMs)}"

        } catch (e: Exception) {
            showError("Failed to read video: ${e.message}")
        }
    }

    // ---------------- MEDIA3 REAL EXPORT ----------------
    private fun exportClipsSequentially() {

        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            ),
            "VideoClipCutter"
        )

        if (!outputDir.exists()) outputDir.mkdirs()

        var index = 0

        fun exportNext() {
            if (index >= clips.size) {
                AlertDialog.Builder(this)
                    .setTitle("Done")
                    .setMessage("Clips saved in:\nMovies/VideoClipCutter")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val clip = clips[index]
            val outFile = File(outputDir, "${clip.name}.mp4")

            val mediaItem = MediaItem.Builder()
                .setUri(videoUri!!)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {

                    override fun onTransformationCompleted(
                        mediaItem: MediaItem,
                        result: TransformationResult
                    ) {
                        index++
                        exportNext()
                    }

                    override fun onTransformationError(
                        mediaItem: MediaItem,
                        exception: TransformationException
                    ) {
                        showError("Export failed: ${exception.message}")
                    }
                })
                .build()

            transformer.start(mediaItem, outFile.absolutePath)
        }

        exportNext()
    }

    // ---------------- UTILS ----------------
    private fun toSeconds(
        hh: EditText,
        mm: EditText,
        ss: EditText
    ): Int? {
        val h = hh.text.toString().toIntOrNull() ?: 0
        val m = mm.text.toString().toIntOrNull() ?: 0
        val s = ss.text.toString().toIntOrNull() ?: 0
        return if (m >= 60 || s >= 60) null else h * 3600 + m * 60 + s
    }

    private fun updateClipList() {
        txtClipList.text =
            if (clips.isEmpty())
                "No clips added"
            else
                clips.joinToString("\n") {
                    "${it.name} (${formatMs(it.startMs)} - ${formatMs(it.endMs)})"
                }
    }

    private fun formatMs(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private fun hideKeyboard() {
        val imm =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}

// ---------------- MODEL ----------------
data class Clip(
    val name: String,
    val startMs: Long,
    val endMs: Long
)


































<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:background="#000000">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- HEADER WITH TITLE AND THEME TOGGLE -->
        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingBottom="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_centerInParent="true"
                android:text="Video Cutter"
                android:textSize="22sp"
                android:textStyle="bold"
                android:textColor="#ffffff"/>

            <Button
                android:id="@+id/btnThemeToggle"
                android:layout_width="wrap_content"
                android:layout_height="40dp"
                android:layout_alignParentEnd="true"
                android:text="Theme"
                android:textSize="13sp"
                android:textColor="#ffffff"
                android:backgroundTint="#2d2d2d"
                android:paddingHorizontal="16dp"
                style="?android:attr/borderlessButtonStyle"/>
        </RelativeLayout>

        <!-- SELECT VIDEO -->
        <Button
            android:id="@+id/btnPickVideo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Select Video"
            android:backgroundTint="#2d2d2d"
            android:textColor="#ffffff"/>

        <TextView
            android:id="@+id/txtVideoInfo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="No video selected"
            android:textColor="#999999"
            android:paddingTop="8dp"
            android:textSize="14sp"/>

        <!-- DIVIDER -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginVertical="16dp"
            android:background="#333333"/>

        <!-- CLIP NAME -->
        <EditText
            android:id="@+id/edtClipName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Clip name (optional)"
            android:inputType="text"
            android:textColor="#ffffff"
            android:textColorHint="#666666"
            android:backgroundTint="#444444"/>

        <!-- START TIME -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Start Time (HH : MM : SS)"
            android:textColor="#ffffff"
            android:paddingTop="12dp"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="3">

            <EditText
                android:id="@+id/startHH"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="HH"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>

            <EditText
                android:id="@+id/startMM"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="MM"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>

            <EditText
                android:id="@+id/startSS"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="SS"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>
        </LinearLayout>

        <!-- END TIME -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="End Time (HH : MM : SS)"
            android:textColor="#ffffff"
            android:paddingTop="12dp"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:weightSum="3">

            <EditText
                android:id="@+id/endHH"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="HH"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>

            <EditText
                android:id="@+id/endMM"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="MM"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>

            <EditText
                android:id="@+id/endSS"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="SS"
                android:inputType="number"
                android:gravity="center"
                android:textColor="#ffffff"
                android:textColorHint="#666666"
                android:backgroundTint="#444444"/>
        </LinearLayout>

        <!-- ADD CLIP -->
        <Button
            android:id="@+id/btnAddClip"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Add Clip"
            android:layout_marginTop="12dp"
            android:backgroundTint="#34a853"
            android:textColor="#ffffff"/>

        <!-- CLIP LIST -->
        <TextView
            android:id="@+id/txtClipList"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="No clips added"
            android:textColor="#999999"
            android:paddingTop="12dp"/>

        <!-- CUT BUTTON -->
        <Button
            android:id="@+id/btnCut"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Cut &amp; Save to Gallery"
            android:layout_marginTop="16dp"
            android:backgroundTint="#ea4335"
            android:textColor="#ffffff"/>

        <!-- CLEAR CACHE -->
        <Button
            android:id="@+id/btnClearCache"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Clear Cache"
            android:layout_marginTop="12dp"
            android:backgroundTint="#333333"
            android:textColor="#ffffff"/>

        <!-- FOOTER -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Long press clip list to delete"
            android:textSize="12sp"
            android:textColor="#666666"
            android:gravity="center"
            android:paddingTop="16dp"/>

    </LinearLayout>
</ScrollView>
