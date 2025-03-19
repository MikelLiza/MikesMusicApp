package com.example.mikesmusicapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert
    suspend fun insert(song: Song)

    @Insert
    suspend fun insertAll(songs: List<Song>)

    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}