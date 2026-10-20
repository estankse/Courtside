package cn.courtside.client

import android.content.Context
import androidx.room.*
import cn.courtside.shared.Models.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "local_records")
data class LocalRecord(
    @PrimaryKey val id: String,
    val owner: String,
    val kind: String,
    val payload: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM local_records WHERE id = :id") suspend fun get(id: String): LocalRecord?

    @Query("SELECT * FROM local_records WHERE owner = :owner AND kind = :kind ORDER BY updatedAt")
    suspend fun list(owner: String, kind: String): List<LocalRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(record: LocalRecord)

    @Query("DELETE FROM local_records WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [LocalRecord::class], version = 1, exportSchema = false)
abstract class LocalDb : RoomDatabase() {
    abstract fun records(): RecordDao
}

class ApiFailure(val status: Int, val code: String, message: String) : Exception(message)

class Repository(context: Context) {
    val gson = Gson()
    val prefs = context.getSharedPreferences("courtside", Context.MODE_PRIVATE)
    val db = Room.databaseBuilder(context, LocalDb::class.java, "courtside.db").build()
    val dao = db.records()
    var baseUrl: String = prefs.getString("baseUrl", "http://10.0.2.2:8080/api")!!
    val deviceId: String =
        prefs.getString("deviceId", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    var login: Login? =
        prefs.getString("login", null)?.let {
            runCatching { gson.fromJson(it, Login::class.java) }.getOrNull()
        }
    val owner: String
        get() = "${baseUrl.hashCode()}:${login?.user?.id.orEmpty()}"

    fun setUrl(value: String) {
        val uri = URI(value.trim().trimEnd('/'))
        require(
            uri.scheme in listOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        ) {
            "请输入完整的 http 或 https 服务地址"
        }
        baseUrl = uri.toString()
        prefs.edit().putString("baseUrl", baseUrl).apply()
    }

    fun persistLogin(value: Login?) {
        login = value
        prefs.edit().putString("login", value?.let(gson::toJson)).apply()
    }

    suspend fun <T> request(method: String, path: String, type: Class<T>, body: Any? = null): T =
        withContext(Dispatchers.IO) {
            val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 7000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/json")
                login
                    ?.token
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.outputStream.use {
                        it.write(gson.toJson(body).toByteArray(Charsets.UTF_8))
                    }
                }
                val code = connection.responseCode
                val stream =
                    if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val error =
                        runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                    throw ApiFailure(
                        code,
                        error?.get("code")?.asString ?: "HTTP_$code",
                        error?.get("message")?.asString ?: "请求失败（$code）",
                    )
                }
                gson.fromJson(text, type)
            } finally {
                connection.disconnect()
            }
        }

    suspend fun cache(kind: String, value: Any) {
        dao.save(LocalRecord("$owner:$kind", owner, "CACHE", gson.toJson(value)))
    }

    suspend fun <T> cached(kind: String, type: Class<T>): T? =
        dao.get("$owner:$kind")?.let { gson.fromJson(it.payload, type) }

    suspend fun session(matchId: String): ScoreSession? =
        dao.get("$owner:score:$matchId")?.let {
            gson.fromJson(it.payload, ScoreSession::class.java)
        }

    suspend fun saveSession(score: ScoreSession) {
        dao.save(LocalRecord("$owner:score:${score.matchId}", owner, "SCORE", gson.toJson(score)))
    }

    suspend fun queue(score: ScoreSession) {
        db.withTransaction {
            saveSession(score)
            dao.save(
                LocalRecord(
                    "$owner:pending:${score.submissionId}",
                    owner,
                    "PENDING",
                    gson.toJson(score),
                )
            )
        }
    }

    fun copy(score: ScoreSession): ScoreSession =
        gson.fromJson(gson.toJson(score), ScoreSession::class.java)
}
