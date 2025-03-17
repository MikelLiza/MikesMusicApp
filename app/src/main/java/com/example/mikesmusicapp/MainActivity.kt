package com.example.mikesmusicapp

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex: Int = -1
    private val songs = mutableListOf<Song>()
    private lateinit var seekBar: SeekBar
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerDuration: TextView
    private lateinit var miniPlayerSeekBar: SeekBar
    private lateinit var miniPlayerThumbnail: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                seekBar.progress = it.currentPosition
                miniPlayerSeekBar.progress = it.currentPosition
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }

    private var isLooping = false
    private var isShuffling = false

    // Register the folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Take persistable URI permissions
            takePersistableUriPermission(uri)
            // Scan the folder for songs
            scanFolderForSongs(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        val songListView = findViewById<ListView>(R.id.songListView)
        seekBar = findViewById(R.id.seekBar)
        val playButton = findViewById<Button>(R.id.playButton)
        val pauseButton = findViewById<Button>(R.id.pauseButton)
        val nextButton = findViewById<Button>(R.id.nextButton)
        val prevButton = findViewById<Button>(R.id.prevButton)

        // Initialize mini-player components
        miniPlayer = findViewById(R.id.miniPlayer)
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle)
        miniPlayerDuration = findViewById(R.id.miniPlayerDuration)
        miniPlayerSeekBar = findViewById(R.id.miniPlayerSeekBar)
        miniPlayerThumbnail = findViewById(R.id.miniPlayerThumbnail)

        // Set up the "Select Folder" button
        val selectFolderButton = findViewById<Button>(R.id.selectFolderButton)
        selectFolderButton.setOnClickListener {
            // Launch the folder picker
            folderPickerLauncher.launch(null)
        }

        // Handle song clicks
        songListView.setOnItemClickListener { _, _, position, _ ->
            currentSongIndex = position
            playSong(songs[position].path)
        }

        // Playback controls
        playButton.setOnClickListener {
            mediaPlayer?.start()
            handler.post(updateSeekBar)
        }
        pauseButton.setOnClickListener {
            mediaPlayer?.pause()
        }
        nextButton.setOnClickListener {
            playNextSong()
        }
        prevButton.setOnClickListener {
            playPreviousSong()
        }

        // Seek bar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Mini-player seek bar listener
        miniPlayerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize shuffle and loop buttons
        val shuffleButton = findViewById<Button>(R.id.miniPlayerShuffleButton)
        val loopButton = findViewById<Button>(R.id.miniPlayerLoopButton)

        shuffleButton.setOnClickListener {
            isShuffling = !isShuffling
            shuffleButton.text = if (isShuffling) "Shuffle: On" else "Shuffle: Off"
        }

        loopButton.setOnClickListener {
            isLooping = !isLooping
            mediaPlayer?.isLooping = isLooping
            loopButton.text = if (isLooping) "Loop: On" else "Loop: Off"
        }

        // Initialize mini-player buttons
        val miniPlayerPlayButton = findViewById<Button>(R.id.miniPlayerPlayButton)
        val miniPlayerPauseButton = findViewById<Button>(R.id.miniPlayerPauseButton)
        val miniPlayerNextButton = findViewById<Button>(R.id.miniPlayerNextButton)
        val miniPlayerPrevButton = findViewById<Button>(R.id.miniPlayerPrevButton)

        // Mini-player play/pause buttons
        miniPlayerPlayButton.setOnClickListener {
            mediaPlayer?.start()
            handler.post(updateSeekBar)
        }
        miniPlayerPauseButton.setOnClickListener {
            mediaPlayer?.pause()
        }

        // Mini-player next/previous buttons
        miniPlayerNextButton.setOnClickListener {
            playNextSong()
        }
        miniPlayerPrevButton.setOnClickListener {
            playPreviousSong()
        }
    }

    private fun takePersistableUriPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun scanFolderForSongs(folderUri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (documentFile != null && documentFile.isDirectory) {
            // Clear the existing songs list
            songs.clear()

            // List all files in the folder
            for (file in documentFile.listFiles()) {
                if (file.isFile && isAudioFile(file.name)) {
                    // Add the file to the songs list
                    songs.add(Song(file.name ?: "Unknown", "Unknown", file.uri.toString()))
                }
            }

            if (songs.isEmpty()) {
                Toast.makeText(this, "No audio files found in the selected folder.", Toast.LENGTH_SHORT).show()
            }

            // Display songs in the ListView
            val songListView = findViewById<ListView>(R.id.songListView)
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songs.map { it.title })
            songListView.adapter = adapter
        }
    }

    private fun playSong(path: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.parse(path))
            prepare()
            start()
            setOnCompletionListener {
                if (isLooping) {
                    playSong(songs[currentSongIndex].path) // Loop the same song
                } else {
                    playNextSong() // Play the next song
                }
            }
        }
        seekBar.max = mediaPlayer!!.duration
        miniPlayerSeekBar.max = mediaPlayer!!.duration
        handler.post(updateSeekBar)
        showMiniPlayer(songs[currentSongIndex].title)
        updateMiniPlayer(songs[currentSongIndex])
    }

    private fun playNextSong() {
        if (isShuffling) {
            currentSongIndex = (0 until songs.size).random() // Play a random song
        } else if (currentSongIndex < songs.size - 1) {
            currentSongIndex++
        } else {
            currentSongIndex = 0 // Loop back to the first song
        }
        playSong(songs[currentSongIndex].path)
    }

    private fun playPreviousSong() {
        if (isShuffling) {
            currentSongIndex = (0 until songs.size).random() // Play a random song
        } else if (currentSongIndex > 0) {
            currentSongIndex--
        } else {
            currentSongIndex = songs.size - 1 // Loop back to the last song
        }
        playSong(songs[currentSongIndex].path)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        handler.removeCallbacks(updateSeekBar)
    }

    private fun isAudioFile(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("mp3", "wav", "ogg", "m4a") // Add more formats if needed
    }

    private fun showMiniPlayer(title: String) {
        miniPlayerTitle.text = title
        miniPlayer.visibility = View.VISIBLE
    }

    private fun hideMiniPlayer() {
        miniPlayer.visibility = View.GONE
    }

    private fun updateMiniPlayer(song: Song) {
        miniPlayerTitle.text = song.title
        miniPlayerDuration.text = formatDuration(mediaPlayer?.duration ?: 0)
        miniPlayerSeekBar.max = mediaPlayer?.duration ?: 0
    }

    private fun formatDuration(duration: Int): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    data class Song(val title: String, val artist: String, val path: String)
}