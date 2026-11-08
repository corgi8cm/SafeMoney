package io.github.corgi8cm.safemoney.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 把同步檔存在 Google 雲端硬碟的 App 專用資料夾（appDataFolder），用 Drive REST API v3。
 * 使用者在雲端硬碟裡看不到這個檔案，但可以在「管理應用程式」刪除它。
 */
class DriveSyncStore(
    private val token: suspend () -> String,
    private val invalidate: suspend (String) -> Unit,
) : SyncStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class DriveFile(val id: String)

    private class Response(val code: Int, val body: String)

    override suspend fun read(): String? {
        val id = findFileId() ?: return null
        val r = call("GET", "$API/files/$id?alt=media")
        return when (r.code) {
            200 -> r.body
            404 -> null
            else -> throw IOException("讀取雲端資料失敗（${r.code}）")
        }
    }

    override suspend fun write(text: String) {
        val id = findFileId()
        val r = if (id == null) {
            // 新建：multipart 上傳（檔案資訊 + 內容）
            val boundary = "safemoney-${System.nanoTime()}"
            val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$text\r\n--$boundary--"
            call("POST", "$UPLOAD/files?uploadType=multipart", body, "multipart/related; boundary=$boundary")
        } else {
            call("PATCH", "$UPLOAD/files/$id?uploadType=media", text, "application/json; charset=UTF-8")
        }
        if (r.code !in 200..299) throw IOException("上傳到雲端失敗（${r.code}）")
    }

    override suspend fun delete() {
        val id = findFileId() ?: return
        val r = call("DELETE", "$API/files/$id")
        if (r.code !in 200..299 && r.code != 404) throw IOException("刪除雲端資料失敗（${r.code}）")
    }

    private suspend fun findFileId(): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val r = call("GET", "$API/files?spaces=appDataFolder&q=$q&fields=files(id)&pageSize=10")
        if (r.code != 200) throw IOException("查詢雲端資料失敗（${r.code}）")
        return json.decodeFromString(FileList.serializer(), r.body).files.firstOrNull()?.id
    }

    /** 送出請求；token 過期（401）時清掉快取、換新 token 重試一次。 */
    private suspend fun call(method: String, url: String, body: String? = null, contentType: String? = null): Response {
        val first = token()
        val r = send(method, url, body, contentType, first)
        if (r.code != 401) return r
        invalidate(first)
        return send(method, url, body, contentType, token())
    }

    private suspend fun send(method: String, url: String, body: String?, contentType: String?, token: String): Response =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                if (method == "PATCH") {
                    // HttpURLConnection 不支援 PATCH，用 Google API 支援的覆寫標頭
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    connection.requestMethod = method
                }
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", contentType)
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val FILE_NAME = "safemoney-sync.json"
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
