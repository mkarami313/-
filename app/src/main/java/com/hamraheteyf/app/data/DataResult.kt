package com.hamraheteyf.app.data

/**
 * A small result wrapper so the UI can show friendly Persian error messages instead of
 * leaking exceptions. Repository actions return this; observers use plain [kotlinx.coroutines.flow.Flow].
 */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val message: String) : DataResult<Nothing>
}

inline fun <T> DataResult<T>.onSuccess(block: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) block(data)
    return this
}

inline fun <T> DataResult<T>.onError(block: (String) -> Unit): DataResult<T> {
    if (this is DataResult.Error) block(message)
    return this
}
