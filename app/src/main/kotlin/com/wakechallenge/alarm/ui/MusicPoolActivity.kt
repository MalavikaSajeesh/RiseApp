package com.wakechallenge.alarm.ui

import android.content.Intent
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wakechallenge.alarm.data.AppDatabase
import com.wakechallenge.alarm.data.MusicPoolEntity
import com.wakechallenge.alarm.databinding.ActivityMusicPoolBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MusicPoolActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicPoolBinding
    private lateinit var adapter: SongAdapter
    private var previewPlayer: MediaPlayer? = null

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) addSongs(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicPoolBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = SongAdapter(
            onPlayPause = { song -> togglePreview(song) },
            onDelete = { song -> deleteSong(song) }
        )
        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter

        binding.fabAddSong.setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        lifecycleScope.launch {
            AppDatabase.get(applicationContext).musicPoolDao().observeAll().collectLatest { songs ->
                adapter.submitList(songs)
                binding.textEmpty.visibility = if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun addSongs(uris: List<Uri>) {
        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            for (uri in uris) {
                try {
                    // Persist read access so the alarm can still play this track after reboot.
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Some providers don't support persistable permissions; the track will
                    // still work until the app process is killed, so keep going.
                }
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Untitled track"
                db.musicPoolDao().insert(MusicPoolEntity(uri = uri.toString(), displayName = name))
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun togglePreview(song: MusicPoolEntity) {
        if (adapter.currentlyPlayingId == song.id) {
            stopPreview()
            return
        }
        stopPreview()
        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(this@MusicPoolActivity, Uri.parse(song.uri))
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
            adapter.currentlyPlayingId = song.id
        } catch (e: Exception) {
            stopPreview()
        }
    }

    private fun stopPreview() {
        previewPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        previewPlayer = null
        adapter.currentlyPlayingId = null
    }

    private fun deleteSong(song: MusicPoolEntity) {
        if (adapter.currentlyPlayingId == song.id) stopPreview()
        lifecycleScope.launch {
            AppDatabase.get(applicationContext).musicPoolDao().delete(song)
        }
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }
}
