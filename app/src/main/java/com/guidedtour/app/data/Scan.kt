package com.guidedtour.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scans")
data class Scan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    val languageTag: String?,
    /** Path of the plaque thumbnail inside the app's private storage. */
    val thumbnail: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val translation: String? = null,
    val translationTag: String? = null,
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Scan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: Scan): Long

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scans SET text = :text, languageTag = :languageTag, translation = NULL, translationTag = NULL WHERE id = :id")
    suspend fun updateText(id: Long, text: String, languageTag: String?)

    @Query("UPDATE scans SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE scans SET translation = :translation, translationTag = :tag WHERE id = :id")
    suspend fun setTranslation(id: Long, translation: String, tag: String)
}

@Database(entities = [Scan::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        /** v2 adds plaque thumbnails, favourites and translations. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN thumbnail TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scans ADD COLUMN translation TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN translationTag TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tour.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
