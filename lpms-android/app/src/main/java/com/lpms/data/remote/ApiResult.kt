package com.lpms.data.remote

import com.lpms.data.remote.dto.ErrorResponse
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException as KotlinCancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException as RetrofitHttpException

/** Result of an API call: failures carry enough structure to pick a message. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

fun <T> ApiResult<T>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error

sealed interface ApiError {

    /** The backend answered with an error status. */
    data class Http(
        val status: Int,
        val serverCode: String?,
        val message: String?,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : ApiError

    /** No response at all — offline, DNS failure, connection refused. */
    data object Network : ApiError

    /** The backend answered, but not in the shape we expect. */
    data class Serialization(val detail: String) : ApiError

    data class Unknown(val detail: String?) : ApiError
}

/**
 * Runs an API call and converts every failure mode into an [ApiError]
 * instead of letting exceptions escape into the UI layer.
 *
 * `CancellationException` is always rethrown so structured concurrency keeps
 * working.
 */
@Singleton
class ApiExecutor @Inject constructor(private val json: Json) {

    suspend fun <T> run(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: RetrofitHttpException) {
        ApiResult.Failure(parseHttp(e))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network)
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.Serialization(e.message ?: "unparseable response"))
    } catch (e: KotlinCancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiResult.Failure(ApiError.Unknown(e.message))
    }

    private fun parseHttp(e: RetrofitHttpException): ApiError.Http {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = raw
            ?.takeIf { it.isNotBlank() }
            ?.let { body -> runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull() }
        return ApiError.Http(
            status = e.code(),
            serverCode = parsed?.code,
            message = parsed?.message,
            fieldErrors = parsed?.errors.orEmpty().associate { it.field to it.message },
        )
    }
}
