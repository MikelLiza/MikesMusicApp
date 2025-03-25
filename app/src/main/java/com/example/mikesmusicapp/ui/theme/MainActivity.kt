package com.example.mikesmusicapp.ui.theme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.mikesmusicapp.R
import com.example.mikesmusicapp.data.AppDatabase
import com.example.mikesmusicapp.data.Song
import com.example.mikesmusicapp.services.MusicService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Stack

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val songDao by lazy { database.songDao() }

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex: Int = -1
    var isPlaying: Boolean = false
    private val songs = mutableListOf<Song>()
    private val shuffleHistory = Stack<Int>()
    private val shuffleFuture = Stack<Int>()
    private var currentShuffleOrder = mutableListOf<Int>()
    private var isShuffleActive = false
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
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
            exoPlayer?.let {
                val position = it.currentPosition.toInt()
                seekBar.progress = position
                miniPlayerSeekBar.progress = position
                fullScreenSeekBar.progress = position
                fullScreenCurrentTime.text = formatDuration(position)
                handler.postDelayed(this, 1000)
            }
        }
    }


    private var playbackMode = PlaybackMode.NORMAL // Default mode

    enum class PlaybackMode {
        NORMAL, SHUFFLE, LOOP
    }

    companion object {

        @Volatile
        private var _instance: MainActivity? = null

        val instance: MainActivity?
            get() = _instance

        private const val PREFS_NAME = "PlayerPrefs"
        private const val KEY_LAST_SONG_INDEX = "last_song_index"
        private const val KEY_IS_PLAYING = "is_playing"
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

    private val sharedPrefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        _instance = this

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

        val lastSongIndex = sharedPrefs.getInt(KEY_LAST_SONG_INDEX, -1)
        val wasPlaying = sharedPrefs.getBoolean(KEY_IS_PLAYING, false)

        if (lastSongIndex != -1 && songs.isNotEmpty()) {
            currentSongIndex = lastSongIndex
            if (wasPlaying) {
                playSong(songs[currentSongIndex].path)
            } else {
                // Just prepare the player but don't start
                preparePlayer(songs[currentSongIndex].path)
                updateMiniPlayer(songs[currentSongIndex])
            }
        } else {
            // No previous song, hide mini-player
            hideMiniPlayer()
        }



        // Set up the "Select Folder" button
        val selectFolderButton = findViewById<Button>(R.id.selectFolderButton)
        selectFolderButton.setOnClickListener {
            // Launch the folder picker
            folderPickerLauncher.launch(null)
        }

        val shuffleFolderButton = findViewById<Button>(R.id.shuffleFolderButton)
        shuffleFolderButton.setOnClickListener {
            if (playbackMode != PlaybackMode.SHUFFLE) {
                // Initialize shuffle mode but don't automatically play
                initializeShuffleMode()
                updateModeButtonText()
            } else {
                // If already in shuffle mode, just play a new random song
                playRandomSong()
            }
        }

        songListView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        songListView.isVerticalScrollBarEnabled = true

        // Handle song clicks
        songListView.setOnItemClickListener { _, _, position, _ ->
            currentSongIndex = position
            playSong(songs[position].path)
        }

        // Expand button (mini-player -> full-screen)
        miniPlayerExpandButton.setOnClickListener {
            if (currentSongIndex != -1) {
                miniPlayer.visibility = View.GONE
                fullScreenPlayer.visibility = View.VISIBLE
                updateFullScreenPlayer(songs[currentSongIndex])
            } else {
                Toast.makeText(this, "No song selected", Toast.LENGTH_SHORT).show()
            }
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

        if (savedInstanceState != null) {
            currentSongIndex = savedInstanceState.getInt("currentSongIndex", -1)
            val currentPosition = savedInstanceState.getInt("currentPosition", 0)
            isPlaying = savedInstanceState.getBoolean("isPlaying", false)

            if (currentSongIndex != -1) {
                playSong(songs[currentSongIndex].path)
                mediaPlayer?.seekTo(currentPosition)
                if (isPlaying) {
                    mediaPlayer?.start()
                }
                updatePlayPauseButtonText()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the current song index, playback position, and playback state
        outState.putInt("currentSongIndex", currentSongIndex)
        outState.putInt("currentPosition", mediaPlayer?.currentPosition ?: 0)
        outState.putBoolean("isPlaying", isPlaying)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentSongIndex = savedInstanceState.getInt("currentSongIndex", -1)
        val currentPosition = savedInstanceState.getInt("currentPosition", 0)
        isPlaying = savedInstanceState.getBoolean("isPlaying", false)

        if (currentSongIndex != -1) {
            playSong(songs[currentSongIndex].path)
            mediaPlayer?.seekTo(currentPosition)
            if (isPlaying) {
                mediaPlayer?.start()
            }
            updatePlayPauseButtonText()
        }
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

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            } else {
                it.play()
                isPlaying = true
            }
            updatePlayPauseButtonText()
            (getSystemService(MusicService::class.java) as? MusicService)?.updatePlayer(it)
        }
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
        if (playbackMode == PlaybackMode.SHUFFLE && !isShuffleActive) {
            shuffleSongs()
            return
        }

        // Start the service first
        val serviceIntent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Release previous player
        exoPlayer?.release()

        // Create new player
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(path)))
            prepare()
            play()
            this@MainActivity.isPlaying = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_ENDED -> handlePlaybackEnded()
                        Player.STATE_READY -> updatePlayerUI()
                    }
                }
            })
        }

        // Update service with new player
        val service = getSystemService(MusicService::class.java) as? MusicService
        if (service == null) {
            // Service not running, start it again
            startService(Intent(this, MusicService::class.java))
        } else {
            service.updatePlayer(exoPlayer!!)
        }

        updatePlayerUI()
    }

    private fun handlePlaybackEnded() {
        when (playbackMode) {
            PlaybackMode.NORMAL -> playNextSong()
            PlaybackMode.SHUFFLE -> if (isShuffleActive) playNextSong() else playRandomSong()
            PlaybackMode.LOOP -> playSong(songs[currentSongIndex].path)
        }
    }

    private fun updatePlayerUI() {
        if (currentSongIndex == -1) return

        val currentSong = songs[currentSongIndex]
        miniPlayerTitle.text = currentSong.title
        miniPlayerTitle.isSelected = true

        // Setup seekbar updates
        exoPlayer?.let { player ->
            seekBar.max = player.duration.toInt()
            miniPlayerSeekBar.max = player.duration.toInt()
            fullScreenSeekBar.max = player.duration.toInt()

            handler.removeCallbacks(updateSeekBar)
            handler.post(updateSeekBar)
        }

        showMiniPlayer(currentSong.title)
        updateMiniPlayer(currentSong)
        updateFullScreenPlayer(currentSong)
        updatePlayPauseButtonText()
    }

    fun playNextSong() {
        when (playbackMode) {
            PlaybackMode.NORMAL -> {
                if (currentSongIndex < songs.size - 1) {
                    currentSongIndex++
                    playSong(songs[currentSongIndex].path)
                }
            }
            PlaybackMode.SHUFFLE -> {
                if (isShuffleActive && shuffleFuture.isNotEmpty()) {
                    // If we have future songs (from going back), play next in future
                    shuffleHistory.push(currentSongIndex)
                    currentSongIndex = shuffleFuture.pop()
                    playSong(songs[currentSongIndex].path)
                } else {
                    // Otherwise play new random song
                    playRandomSong()
                }
            }
            PlaybackMode.LOOP -> {
                playSong(songs[currentSongIndex].path)
            }
        }
    }

    fun playPreviousSong() {
        when (playbackMode) {
            PlaybackMode.NORMAL -> {
                if (currentSongIndex > 0) {
                    currentSongIndex--
                    playSong(songs[currentSongIndex].path)
                }
            }
            PlaybackMode.SHUFFLE -> {
                if (isShuffleActive && shuffleHistory.isNotEmpty()) {
                    // Move current song to future stack
                    shuffleFuture.push(currentSongIndex)
                    // Get previous song from history
                    currentSongIndex = shuffleHistory.pop()
                    playSong(songs[currentSongIndex].path)
                } else if (currentSongIndex != -1) {
                    // If no history, just replay current song
                    playSong(songs[currentSongIndex].path)
                }
            }
            PlaybackMode.LOOP -> {
                playSong(songs[currentSongIndex].path)
            }
        }
    }

    private fun playRandomSong() {
        if (songs.isEmpty()) return

        if (playbackMode == PlaybackMode.SHUFFLE) {
            if (!isShuffleActive) {
                initializeShuffleMode()
            }

            // Add current song to history before changing
            if (currentSongIndex != -1 && isPlaying) {
                shuffleHistory.push(currentSongIndex)
            }

            // Get next random song (not in history)
            val availableSongs = songs.indices.toMutableList().apply {
                removeAll(shuffleHistory) // Exclude history
                if (currentSongIndex != -1) remove(currentSongIndex) // Exclude current
            }

            if (availableSongs.isEmpty()) {
                // If all songs have been played, start over
                shuffleHistory.clear()
                currentSongIndex = songs.indices.random()
            } else {
                currentSongIndex = availableSongs.random()
            }

            playSong(songs[currentSongIndex].path)
        } else {
            // Regular random play (not shuffle mode)
            currentSongIndex = (0 until songs.size).random()
            playSong(songs[currentSongIndex].path)
        }
    }

    private fun cyclePlaybackMode() {
        playbackMode = when (playbackMode) {
            PlaybackMode.NORMAL -> {
                shuffleSongs() // Initialize shuffle when first entering shuffle mode
                PlaybackMode.SHUFFLE
            }
            PlaybackMode.SHUFFLE -> PlaybackMode.LOOP
            PlaybackMode.LOOP -> {
                isShuffleActive = false
                PlaybackMode.NORMAL
            }
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
        mediaPlayer?.release()
        exoPlayer?.release()
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
        if (currentSongIndex != -1) {
            miniPlayerTitle.text = title
            miniPlayer.visibility = View.VISIBLE
        }
    }

    private fun hideMiniPlayer() {
        miniPlayer.visibility = View.GONE
        fullScreenPlayer.visibility = View.GONE
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

    private fun preparePlayer(path: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.parse(path))
            prepareAsync()
            setOnPreparedListener {
                seekTo(0) // Reset to beginning
                updateMiniPlayer(songs[currentSongIndex])
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current state
        sharedPrefs.edit().apply {
            putInt(KEY_LAST_SONG_INDEX, currentSongIndex)
            putBoolean(KEY_IS_PLAYING, isPlaying)
            apply()
        }
    }

    private fun formatDuration(duration: Int): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun shuffleSongs() {
        initializeShuffleMode()

        // Only start playing if not already playing
        if (!isPlaying || currentSongIndex == -1) {
            currentSongIndex = currentShuffleOrder[0]
            playSong(songs[currentSongIndex].path)
        }
    }

    private fun initializeShuffleMode() {
        if (songs.isEmpty()) return

        // Clear history and future
        shuffleHistory.clear()
        shuffleFuture.clear()

        // Generate new shuffle order
        currentShuffleOrder = songs.indices.toMutableList().apply {
            shuffle()
        }

        isShuffleActive = true
        playbackMode = PlaybackMode.SHUFFLE

        // If there's a current song, add it to history
        if (currentSongIndex != -1) {
            shuffleHistory.push(currentSongIndex)
        }
    }

}