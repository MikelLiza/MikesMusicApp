package com.example.mikesmusicapp.data

import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()

    suspend fun insert(song: Song) {
        songDao.insert(song)
    }

    suspend fun insertAll(songs: List<Song>) {
        songDao.insertAll(songs)
    }

    suspend fun deleteAll() {
        songDao.deleteAll()
    }
}