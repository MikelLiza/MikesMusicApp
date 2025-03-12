package com.example.mikesmusicapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile

class MainActivity : ComponentActivity() {

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

        // Set up the "Select Folder" button
        val selectFolderButton = findViewById<Button>(R.id.selectFolderButton)
        selectFolderButton.setOnClickListener {
            // Launch the folder picker
            folderPickerLauncher.launch(null)
        }
    }

    private fun takePersistableUriPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun scanFolderForSongs(folderUri: Uri) {
        val songs = mutableListOf<Song>()
        val documentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (documentFile != null && documentFile.isDirectory) {
            // List all files in the folder
            for (file in documentFile.listFiles()) {
                if (file.isFile && isAudioFile(file.name)) {
                    // Add the file to the songs list
                    songs.add(Song(file.name ?: "Unknown", "Unknown", file.uri.toString()))
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, "No audio files found in the selected folder.", Toast.LENGTH_SHORT).show()
        }

        // Display songs in the ListView
        val songListView = findViewById<ListView>(R.id.songListView)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songs.map { it.title })
        songListView.adapter = adapter

        // Handle song clicks
        songListView.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            Toast.makeText(this, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
            // TODO: Add code to play the song
        }
    }

    private fun isAudioFile(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("mp3", "wav", "ogg", "m4a") // Add more formats if needed
    }

    data class Song(val title: String, val artist: String, val path: String)
}