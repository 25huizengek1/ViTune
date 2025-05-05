package app.vitune.providers.piped

import app.vitune.providers.piped.models.CreatedPlaylist
import app.vitune.providers.piped.models.Instance
import app.vitune.providers.piped.models.Playlist
import app.vitune.providers.piped.models.PlaylistPreview
import app.vitune.providers.piped.models.Session
import app.vitune.providers.piped.models.authenticatedWith
import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

operator fun Url.div(path: String) = URLBuilder(this).apply { appendPathSegments(path) }.build()
operator fun JsonElement.div(key: String) = jsonObject[key]!!

object Piped {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    }
                )
            }

            install(HttpRequestRetry) {
                exponentialDelay()
                maxRetries = 2
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 1000L
                requestTimeoutMillis = 5000L
            }

            expectSuccess = true

            defaultRequest {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }
    }

    private val mutex = Mutex()

    private suspend fun request(
        session: Session,
        endpoint: String,
        block: HttpRequestBuilder.() -> Unit = { }
    ) = mutex.withLock {
        client.request(url = session.apiBaseUrl / endpoint) {
            block()
            header("Authorization", session.token)
        }
    }

    private suspend fun HttpResponse.isOk() =
        (body<JsonElement>() / "message").jsonPrimitive.content == "ok"

    suspend fun getInstances() = runCatchingCancellable {
        client.get("https://piped-instances.kavin.rocks/").body<List<Instance>>()
    }

    suspend fun login(apiBaseUrl: Url, username: String, password: String) =
        runCatchingCancellable {
            apiBaseUrl authenticatedWith (
                    client.post(apiBaseUrl / "login") {
                        setBody(
                            mapOf(
                                "username" to username,
                                "password" to password
                            )
                        )
                    }.body<JsonElement>() / "token"
                    ).jsonPrimitive.content
        }

    val playlist = Playlists()

    class Playlists internal constructor() {
        suspend fun list(session: Session) = runCatchingCancellable {
            request(session, "user/playlists").body<List<PlaylistPreview>>()
        }

        suspend fun create(session: Session, name: String) = runCatchingCancellable {
            request(session, "user/playlists/create") {
                method = HttpMethod.Post
                setBody(mapOf("name" to name))
            }.body<CreatedPlaylist>()
        }

        suspend fun rename(session: Session, id: UUID, name: String) = runCatchingCancellable {
            request(session, "user/playlists/rename") {
                method = HttpMethod.Post
                setBody(
                    mapOf(
                        "playlistId" to id.toString(),
                        "newName" to name
                    )
                )
            }.isOk()
        }

        suspend fun delete(session: Session, id: UUID) = runCatchingCancellable {
            request(session, "user/playlists/delete") {
                method = HttpMethod.Post
                setBody(mapOf("playlistId" to id.toString()))
            }.isOk()
        }

        suspend fun add(session: Session, id: UUID, videos: List<String>) = runCatchingCancellable {
            request(session, "user/playlists/add") {
                method = HttpMethod.Post
                setBody(
                    mapOf(
                        "playlistId" to id.toString(),
                        "videoIds" to videos
                    )
                )
            }.isOk()
        }

        suspend fun remove(session: Session, id: UUID, idx: Int) = runCatchingCancellable {
            request(session, "user/playlists/remove") {
                method = HttpMethod.Post
                setBody(
                    mapOf(
                        "playlistId" to id.toString(),
                        "index" to idx
                    )
                )
            }.isOk()
        }

        suspend fun songs(session: Session, id: UUID) = runCatchingCancellable {
            request(session, "playlists/$id").body<Playlist>()
        }
    }
}
