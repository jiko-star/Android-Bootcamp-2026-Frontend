package ru.sicampus.bootcamp2026.data.source

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.sicampus.bootcamp2026.data.CredentialsHolder
import ru.sicampus.bootcamp2026.data.dto.InvitationDto
import ru.sicampus.bootcamp2026.data.dto.UserDto
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class MeetingDto(
    val id: Long? = null,
    val title: String,
    val description: String,
    val organizerId: Long,
    val startTime: String,
    val endTime: String,
    val createdAt: String
)

open class UserInfoDataSource {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun HttpURLConnection.readResponseText(): String {
        return try {
            val code = responseCode
            if (code in 200..299) {
                inputStream.bufferedReader().use { it.readText() }
            } else {
                errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun prepareConnection(
        url: URL,
        method: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeout: Int = 15000,
        readTimeout: Int = 15000,
        doOutput: Boolean = false
    ): HttpURLConnection {
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = method
        conn.doOutput = doOutput
        conn.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        return conn
    }

    private fun <T> performRequest(
        url: URL,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeout: Int = 15000,
        readTimeout: Int = 15000,
        expectCodeRange: IntRange = 200..299,
        parse: (String) -> T
    ): T {
        val conn = prepareConnection(url, method, headers, connectTimeout, readTimeout, body != null)

        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val code = conn.responseCode
        val text = conn.readResponseText()
        conn.disconnect()

        if (code !in expectCodeRange) error("HTTP $code: $text")

        return parse(text)
    }

    open suspend fun login(username: String, password: String): Result<UserDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(Network.HOST + "/api/person/login")

                val auth = "$username:$password"
                val encoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)

                val headers = mapOf("Authorization" to "Basic $encoded")

                performRequest(
                    url = url,
                    method = "GET",
                    headers = headers,
                    connectTimeout = 5000,
                    readTimeout = 5000,
                    expectCodeRange = 200..200,
                    parse = { json.decodeFromString<UserDto>(it) }
                )
            }
        }

    open suspend fun getUserByUsername(username: String): Result<UserDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedName = URLEncoder.encode(username, "UTF-8")
                val url = URL(Network.HOST + "/api/persons/username/$encodedName")

                performRequest(
                    url = url,
                    method = "GET",
                    connectTimeout = 5000,
                    readTimeout = 5000,
                    expectCodeRange = 200..200,
                    parse = { json.decodeFromString<UserDto>(it) }
                )
            }
        }

    open suspend fun getAllUsers(): Result<List<UserDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Network.HOST + "/api/person")

            performRequest(
                url = url,
                method = "GET",
                connectTimeout = 15000,
                readTimeout = 15000,
                parse = { json.decodeFromString<List<UserDto>>(it) }
            )
        }
    }

    open suspend fun getUserById(id: Long): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Network.HOST + "/api/person/" + id)

            performRequest(
                url = url,
                method = "GET",
                connectTimeout = 15000,
                readTimeout = 15000,
                parse = { json.decodeFromString<UserDto>(it) }
            )
        }
    }

    open suspend fun registerUser(user: UserDto): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Network.HOST + "/api/person/register")
            val body = json.encodeToString(user)

            performRequest(
                url = url,
                method = "POST",
                body = body,
                connectTimeout = 15000,
                readTimeout = 15000,
                parse = { json.decodeFromString<UserDto>(it) }
            )
        }
    }

    open suspend fun updateUser(id: Long, user: UserDto): Result<UserDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(Network.HOST + "/api/person/" + id)
                val body = json.encodeToString(user)

                performRequest(
                    url = url,
                    method = "PUT",
                    body = body,
                    connectTimeout = 15000,
                    readTimeout = 15000,
                    parse = { json.decodeFromString<UserDto>(it) }
                )
            }
        }

    open suspend fun deleteUser(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Network.HOST + "/api/person/" + id)

            performRequest(
                url = url,
                method = "DELETE",
                connectTimeout = 15000,
                readTimeout = 15000,
                parse = { }
            )
        }
    }

    open suspend fun getAllMeetings(): Result<List<MeetingDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(Network.HOST + "/api/meetings")

            performRequest(
                url = url,
                method = "GET",
                connectTimeout = 15000,
                readTimeout = 15000,
                parse = { json.decodeFromString<List<MeetingDto>>(it) }
            )
        }
    }

    open suspend fun getMeetingsByDate(date: String): Result<List<MeetingDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(date, "UTF-8")
                val url = URL(Network.HOST + "/api/meetings?date=$encoded")

                performRequest(
                    url = url,
                    method = "GET",
                    connectTimeout = 15000,
                    readTimeout = 15000,
                    parse = { json.decodeFromString<List<MeetingDto>>(it) }
                )
            }
        }

    open suspend fun getInvitationsByPersonId(id: Long): Result<List<InvitationDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(Network.HOST + "/api/invitations/person/" + id)

                val auth = "${CredentialsHolder.username}:${CredentialsHolder.password}"
                val encoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                val headers = mapOf("Authorization" to "Basic $encoded")

                performRequest(
                    url = url,
                    method = "GET",
                    headers = headers,
                    connectTimeout = 5000,
                    readTimeout = 5000,
                    expectCodeRange = 200..200,
                    parse = { json.decodeFromString<List<InvitationDto>>(it) }
                )
            }
        }
}