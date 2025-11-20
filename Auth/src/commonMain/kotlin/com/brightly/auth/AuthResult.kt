package com.brightly.auth

sealed class AuthResult {
    object Success : AuthResult()
    object Cancelled : AuthResult()
    data class CryptoSuccess(val data: ByteArray) : AuthResult()
    data class Failure(val error: String?) : AuthResult()
}
