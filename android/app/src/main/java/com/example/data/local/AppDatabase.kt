package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CaseNoteDao
import com.example.data.local.dao.CaseSummaryDao
import com.example.data.local.dao.LocalDeadlineDao
import com.example.data.local.dao.ScannedDocumentDao
import com.example.data.local.entities.LocalCaseNoteEntity
import com.example.data.local.entities.LocalCaseSummaryEntity
import com.example.data.local.entities.LocalDeadlineEntity
import com.example.data.local.entities.LocalScannedDocumentEntity

@Database(
    entities = [
        LocalCaseSummaryEntity::class,
        LocalCaseNoteEntity::class,
        LocalScannedDocumentEntity::class,
        LocalDeadlineEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseSummaryDao(): CaseSummaryDao
    abstract fun caseNoteDao(): CaseNoteDao
    abstract fun scannedDocumentDao(): ScannedDocumentDao
    abstract fun deadlineDao(): LocalDeadlineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hoyaam_litigation.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
