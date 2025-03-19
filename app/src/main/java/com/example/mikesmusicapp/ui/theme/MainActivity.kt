package com.example.mikesmusicapp.ui.theme

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
import androidx.lifecycle.lifecycleScope
import com.example.mikesmusicapp.R
import com.example.mikesmusicapp.data.AppDatabase
import com.example.mikesmusicapp.data.Song
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val songDao by lazy { database.songDao() }

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex: Int = -1
    private var isPlaying: Boolean = false
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
            // Save the folder URI to SharedPreferences
            saveFolderUri(uri.toString())
            // Scan the folder for songs and insert them into the database
            lifecycleScope.launch {
                scanFolderForSongs(uri)
            }
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
        fullScreenNextButton = findViewById(R.id.fullScreenNextButton)
        fullScreenPrevButton = findViewById(R.id.fullScreenPrevButton)
        fullScreenShuffleButton = findViewById(R.id.fullScreenShuffleButton)
        fullScreenLoopButton = findViewById(R.id.fullScreenLoopButton)

        // Load the last opened folder URI
        val lastFolderUri = loadFolderUri()
        if (lastFolderUri != null) {
            val uri = Uri.parse(lastFolderUri)
            takePersistableUriPermission(uri)
            lifecycleScope.launch {
                songs.addAll(loadSongs()) // Load songs from the database
                observeSongs() // Start observing changes
            }
        }

        // Set up the "Select Folder" button
        val selectFolderButton = findViewById<Button>(R.id.selectFolderButton)
        selectFolderButton.setOnClickListener {
            // Launch the folder picker
            folderPickerLauncher.launch(null)
        }

        val shuffleFolderButton = findViewById<Button>(R.id.shuffleFolderButton)
        shuffleFolderButton.setOnClickListener {
            playbackMode = PlaybackMode.SHUFFLE // Set playback mode to SHUFFLE
            playRandomSong() // Play a random song
            updateModeButtonText() // Update the mode button text
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
        val miniPlayerPlayPauseButton = findViewById<Button>(R.id.miniPlayerPlayPauseButton)
        val nextButton = findViewById<Button>(R.id.miniPlayerNextButton)
        val prevButton = findViewById<Button>(R.id.miniPlayerPrevButton)

        miniPlayerPlayPauseButton.setOnClickListener {
            togglePlayPause()
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
        shuffleButton.setOnClickListener {
            playbackMode = PlaybackMode.SHUFFLE
            playRandomSong()
        }

        val loopButton = findViewById<Button>(R.id.miniPlayerLoopButton)

        shuffleButton.setOnClickListener {
            cyclePlaybackMode()
        }

        loopButton.setOnClickListener {
            mediaPlayer?.isLooping = !mediaPlayer!!.isLooping
            loopButton.text = if (mediaPlayer!!.isLooping) "Loop: On" else "Loop: Off"
        }

        val fullScreenPlayPauseButton = findViewById<Button>(R.id.fullScreenPlayPauseButton)
        fullScreenPlayPauseButton.setOnClickListener {
            togglePlayPause()
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

    private fun saveFolderUri(uri: String) {
        val sharedPreferences = getSharedPreferences("MusicAppPrefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("lastFolderUri", uri)
        editor.apply()
    }

    private fun loadFolderUri(): String? {
        val sharedPreferences = getSharedPreferences("MusicAppPrefs", MODE_PRIVATE)
        return sharedPreferences.getString("lastFolderUri", null)
    }

    private suspend fun scanFolderForSongs(folderUri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (documentFile != null && documentFile.isDirectory) {
            val songs = mutableListOf<Song>()
            for (file in documentFile.listFiles()) {
                if (file.isFile && isAudioFile(file.name)) {
                    // Create a Song object for each audio file
                    val song = Song(
                        title = file.name ?: "Unknown",
                        artist = "Unknown", // You can extract metadata (e.g., artist) later
                        path = file.uri.toString()
                    )
                    songs.add(song)
                }
            }

            if (songs.isNotEmpty()) {
                // Insert all songs into the database
                songDao.insertAll(songs)
            }
        }
    }

    private suspend fun loadSongs(): List<Song> {
        return songDao.getAllSongs().first()
    }

    private fun observeSongs() {
        lifecycleScope.launch {
            songDao.getAllSongs().collect { songs ->
                // Update the ListView with the new list of songs
                val songListView = findViewById<ListView>(R.id.songListView)
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, songs.map { it.title })
                songListView.adapter = adapter
            }
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            mediaPlayer?.start()
            isPlaying = true
        }
        updatePlayPauseButtonText()
    }

    private fun updatePlayPauseButtonText() {
        val miniPlayerPlayPauseButton = findViewById<Button>(R.id.miniPlayerPlayPauseButton)
        val fullScreenPlayPauseButton = findViewById<Button>(R.id.fullScreenPlayPauseButton)

        if (isPlaying) {
            miniPlayerPlayPauseButton.text = "Pause"
            fullScreenPlayPauseButton.text = "Pause"
        } else {
            miniPlayerPlayPauseButton.text = "Play"
            fullScreenPlayPauseButton.text = "Play"
        }
    }

    private fun playSong(path: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.parse(path))
            prepare()
            start()
            updatePlayPauseButtonText()

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
        when (playbackMode) {
            PlaybackMode.NORMAL -> {
                if (currentSongIndex < songs.size - 1) {
                    currentSongIndex++
                    playSong(songs[currentSongIndex].path)
                }
            }
            PlaybackMode.SHUFFLE -> {
                playRandomSong()
            }
            PlaybackMode.LOOP -> {
                playSong(songs[currentSongIndex].path)
            }
        }
    }

    private fun playPreviousSong() {
        if (currentSongIndex > 0) {
            currentSongIndex--
            playSong(songs[currentSongIndex].path)
        }
    }

    private fun playRandomSong() {
        if (songs.isNotEmpty()) {
            currentSongIndex = (0 until songs.size).random()
            playSong(songs[currentSongIndex].path)
        }
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
}