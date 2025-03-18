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
    private lateinit var miniPlayerExpandButton: Button
    private lateinit var fullScreenPlayer: LinearLayout
    private lateinit var fullScreenMinimizeButton: Button
    private lateinit var fullScreenTitle: TextView
    private lateinit var fullScreenCurrentTime: TextView
    private lateinit var fullScreenDuration: TextView
    private lateinit var fullScreenSeekBar: SeekBar
    private lateinit var fullScreenPlayButton: Button
    private lateinit var fullScreenPauseButton: Button
    private lateinit var fullScreenNextButton: Button
    private lateinit var fullScreenPrevButton: Button
    private lateinit var fullScreenShuffleButton: Button
    private lateinit var fullScreenLoopButton: Button
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

    private var playbackMode = PlaybackMode.NORMAL // Default mode

    enum class PlaybackMode {
        NORMAL, SHUFFLE, LOOP
    }

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

        // Initialize views
        val songListView = findViewById<ListView>(R.id.songListView)
        seekBar = findViewById(R.id.miniPlayerSeekBar)
        miniPlayer = findViewById(R.id.miniPlayer)
        fullScreenPlayer = findViewById(R.id.fullScreenPlayer)
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle)
        miniPlayerDuration = findViewById(R.id.miniPlayerDuration)
        miniPlayerSeekBar = findViewById(R.id.miniPlayerSeekBar)
        miniPlayerThumbnail = findViewById(R.id.miniPlayerThumbnail)
        miniPlayerExpandButton = findViewById(R.id.miniPlayerExpandButton)
        fullScreenMinimizeButton = findViewById(R.id.fullScreenMinimizeButton)
        fullScreenTitle = findViewById(R.id.fullScreenTitle)
        fullScreenCurrentTime = findViewById(R.id.fullScreenCurrentTime)
        fullScreenDuration = findViewById(R.id.fullScreenDuration)
        fullScreenSeekBar = findViewById(R.id.fullScreenSeekBar)
        fullScreenPlayButton = findViewById(R.id.fullScreenPlayButton)
        fullScreenPauseButton = findViewById(R.id.fullScreenPauseButton)
        fullScreenNextButton = findViewById(R.id.fullScreenNextButton)
        fullScreenPrevButton = findViewById(R.id.fullScreenPrevButton)
        fullScreenShuffleButton = findViewById(R.id.fullScreenShuffleButton)
        fullScreenLoopButton = findViewById(R.id.fullScreenLoopButton)

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

        // Expand button (mini-player -> full-screen)
        miniPlayerExpandButton.setOnClickListener {
            miniPlayer.visibility = View.GONE
            fullScreenPlayer.visibility = View.VISIBLE
            updateFullScreenPlayer(songs[currentSongIndex])
        }

        // Minimize button (full-screen -> mini-player)
        fullScreenMinimizeButton.setOnClickListener {
            fullScreenPlayer.visibility = View.GONE
            miniPlayer.visibility = View.VISIBLE
            updateMiniPlayer(songs[currentSongIndex])
        }

        // Playback controls
        val playButton = findViewById<Button>(R.id.miniPlayerPlayButton)
        val pauseButton = findViewById<Button>(R.id.miniPlayerPauseButton)
        val nextButton = findViewById<Button>(R.id.miniPlayerNextButton)
        val prevButton = findViewById<Button>(R.id.miniPlayerPrevButton)

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

        // Initialize shuffle and loop buttons
        val shuffleButton = findViewById<Button>(R.id.miniPlayerShuffleButton)
        val loopButton = findViewById<Button>(R.id.miniPlayerLoopButton)

        shuffleButton.setOnClickListener {
            cyclePlaybackMode()
        }

        loopButton.setOnClickListener {
            mediaPlayer?.isLooping = !mediaPlayer!!.isLooping
            loopButton.text = if (mediaPlayer!!.isLooping) "Loop: On" else "Loop: Off"
        }

        fullScreenPlayButton.setOnClickListener {
            mediaPlayer?.start()
            handler.post(updateFullScreenSeekBar)
        }

        fullScreenPauseButton.setOnClickListener {
            mediaPlayer?.pause()
        }

        fullScreenNextButton.setOnClickListener {
            playNextSong()
        }

        fullScreenPrevButton.setOnClickListener {
            playPreviousSong()
        }

        fullScreenShuffleButton.setOnClickListener {
            cyclePlaybackMode()
        }

        fullScreenLoopButton.setOnClickListener {
            mediaPlayer?.isLooping = !mediaPlayer!!.isLooping
            fullScreenLoopButton.text = if (mediaPlayer!!.isLooping) "Loop: On" else "Loop: Off"
        }

        fullScreenSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
                when (playbackMode) {
                    PlaybackMode.NORMAL -> playNextSong()
                    PlaybackMode.SHUFFLE -> playRandomSong()
                    PlaybackMode.LOOP -> playSong(songs[currentSongIndex].path)
                }
            }
        }
        seekBar.max = mediaPlayer!!.duration
        miniPlayerSeekBar.max = mediaPlayer!!.duration
        fullScreenSeekBar.max = mediaPlayer!!.duration
        handler.post(updateSeekBar)
        handler.post(updateFullScreenSeekBar)
        showMiniPlayer(songs[currentSongIndex].title)
        updateMiniPlayer(songs[currentSongIndex])
        updateFullScreenPlayer(songs[currentSongIndex])
    }

    private fun playNextSong() {
        if (currentSongIndex < songs.size - 1) {
            currentSongIndex++
            playSong(songs[currentSongIndex].path)
        }
    }

    private fun playPreviousSong() {
        if (currentSongIndex > 0) {
            currentSongIndex--
            playSong(songs[currentSongIndex].path)
        }
    }

    private fun playRandomSong() {
        currentSongIndex = (0 until songs.size).random()
        playSong(songs[currentSongIndex].path)
    }

    private fun cyclePlaybackMode() {
        playbackMode = when (playbackMode) {
            PlaybackMode.NORMAL -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.LOOP
            PlaybackMode.LOOP -> PlaybackMode.NORMAL
        }
        updateModeButtonText()
    }

    private fun updateModeButtonText() {
        val modeButton = findViewById<Button>(R.id.miniPlayerShuffleButton)
        val fullScreenModeButton = findViewById<Button>(R.id.fullScreenShuffleButton)
        modeButton.text = when (playbackMode) {
            PlaybackMode.NORMAL -> "Normal"
            PlaybackMode.SHUFFLE -> "Shuffle"
            PlaybackMode.LOOP -> "Loop"
        }
        fullScreenModeButton.text = modeButton.text
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        handler.removeCallbacks(updateSeekBar)
        handler.removeCallbacks(updateFullScreenSeekBar)
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

    private fun updateFullScreenPlayer(song: Song) {
        fullScreenTitle.text = song.title
        fullScreenDuration.text = formatDuration(mediaPlayer?.duration ?: 0)
        fullScreenSeekBar.max = mediaPlayer?.duration ?: 0
        fullScreenSeekBar.progress = mediaPlayer?.currentPosition ?: 0

        // Update the current time periodically
        handler.post(updateFullScreenSeekBar)
    }

    private val updateFullScreenSeekBar = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                fullScreenSeekBar.progress = it.currentPosition
                fullScreenCurrentTime.text = formatDuration(it.currentPosition)
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }



    private fun formatDuration(duration: Int): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    data class Song(val title: String, val artist: String, val path: String)
}