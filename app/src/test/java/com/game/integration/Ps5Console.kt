package com.game.integration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class Ps5Console(private val baseUrl: String = "http://127.0.0.1:8765") {

    @Serializable
    data class Console(val host: String, val name: String? = null, val on: Boolean = false, val status: String? = null)

    @Serializable
    data class Session(
        val connected: Boolean,
        val frames: Int = 0,
        val frame_age: Double? = null,
        val session_error: String? = null,
    )

    @Serializable
    data class Status(val console: Console, val session: Session)

    @Serializable
    private data class Pressed(val pressed: List<String>)

    private val http = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun status(): Status = json.decodeFromString(get("/status").decodeToString())

    fun connect(): Session = json.decodeFromString(post("/connect", "{}").decodeToString())

    fun ensureConnected(): Session = status().session.takeIf { it.connected } ?: connect()

    fun press(sequence: String): List<String> {
        val body = buildJsonObject { put("sequence", sequence) }.toString()
        return json.decodeFromString<Pressed>(post("/press", body).decodeToString()).pressed
    }

    fun frame(): ByteArray = get("/frame.png")

    fun saveFrame(file: File): File = file.apply {
        parentFile?.mkdirs()
        writeBytes(frame())
    }

    private fun get(path: String) = call(Request.Builder().url(baseUrl + path).build())

    private fun post(path: String, body: String) =
        call(Request.Builder().url(baseUrl + path).post(body.toRequestBody("application/json".toMediaType())).build())

    private fun call(request: Request): ByteArray = http.newCall(request).execute().use { response ->
        val bytes = response.body.bytes()
        if (!response.isSuccessful) {
            throw IOException("${request.method} ${request.url.encodedPath} -> ${response.code}: ${bytes.decodeToString()}")
        }
        bytes
    }
}
