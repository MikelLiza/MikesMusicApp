package com.example.mikesmusicapp.ui.theme

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import com.example.mikesmusicapp.data.Song

class PlayerViewModel : ViewModel() {
    var mediaPlayer: MediaPlayer? = null
    var currentSongIndex: Int = -1
    var isPlaying: Boolean = false
    val songs = mutableListOf<Song>()


}
