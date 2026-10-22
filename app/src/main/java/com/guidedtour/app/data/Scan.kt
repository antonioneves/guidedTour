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
    /** For a text in several languages, where each one starts (see [encodeSpans]); null for one language. */
    val languages: String? = null,
    /** True when the user chose [languageTag] by hand: it's kept, instead of detected again, when the text changes. */
    @ColumnInfo(defaultValue = "0") val languageFixed: Boolean = false,
    /** A few words naming the text's topic, for its card (see [Headliner]); null until written, or where the phone can't. */
    val headline: String? = null,
    /** [headline] in [translationTag]'s language, shown while the translation is the one read; null until translated. */
    val translatedHeadline: String? = null,
) {
    /** The languages of the text in reading order, or none if unknown. */
    fun spans(): List<LanguageSpan> =
        decodeSpans(languages).ifEmpty { listOfNotNull(languageTag?.let { LanguageSpan(0, it) }) }

    /** Each language in the text once, in reading order. */
    fun allLanguages(): List<String> = spans().map { it.tag }.distinct()
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Scan>>

    @Query("SELECT * FROM scans ORDER BY timestamp")
    suspend fun all(): List<Scan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: Scan): Long

    @Insert
    suspend fun insertAll(scans: List<Scan>)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scans SET text = :text, languageTag = :languageTag, languages = :languages, translation = NULL, translationTag = NULL, headline = NULL, translatedHeadline = NULL WHERE id = :id")
    suspend fun updateText(id: Long, text: String, languageTag: String?, languages: String?)

    @Query("UPDATE scans SET languageTag = :languageTag, languages = :languages, languageFixed = :fixed, translation = NULL, translationTag = NULL, headline = NULL, translatedHeadline = NULL WHERE id = :id")
    suspend fun setLanguage(id: Long, languageTag: String?, languages: String?, fixed: Boolean)

    @Query("UPDATE scans SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE scans SET translation = :translation, translationTag = :tag, translatedHeadline = :headline WHERE id = :id")
    suspend fun setTranslation(id: Long, translation: String, tag: String, headline: String?)

    @Query("UPDATE scans SET translatedHeadline = :headline WHERE id = :id")
    suspend fun setTranslatedHeadline(id: Long, headline: String)

    /** A new headline: its translation, of the old one, goes with it. */
    @Query("UPDATE scans SET headline = :headline, translatedHeadline = NULL WHERE id = :id")
    suspend fun setHeadline(id: Long, headline: String)
}

@Database(entities = [Scan::class], version = 5, exportSchema = true)
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

        /** v3 adds the languages of bilingual texts, and languages chosen by hand. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN languages TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN languageFixed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 adds headlines. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN headline TEXT")
            }
        }

        /** v5 adds translated headlines. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN translatedHeadline TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tour.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
