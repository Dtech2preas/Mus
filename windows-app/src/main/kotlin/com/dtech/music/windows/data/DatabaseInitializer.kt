package com.dtech.music.windows.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseInitializer {
    fun init() {
        val dbFile = File("dtech_music.db")
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(
                SongsTable,
                StreamSongsTable,
                PlaylistsTable,
                PlaylistEntriesTable,
                StreamCacheTable,
                FavoriteSongsTable,
                PlayHistoryTable
            )
        }
    }
}
