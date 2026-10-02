package cm.coachemploi

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Élément enregistré hors ligne : type = "cv" ou "diag", json = réponse de l'API. */
@Entity(tableName = "saved")
data class Saved(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val json: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SavedDao {
    @Query("SELECT * FROM saved ORDER BY createdAt DESC") fun all(): Flow<List<Saved>>
    @Query("SELECT * FROM saved WHERE type = :t ORDER BY createdAt DESC LIMIT 1") suspend fun last(t: String): Saved?
    @Insert suspend fun insert(s: Saved)
    @Delete suspend fun delete(s: Saved)
}

@Database(entities = [Saved::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SavedDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "coach.db")
                .build().also { instance = it }
        }
    }
}
