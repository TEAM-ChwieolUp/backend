package com.cheerup.demo.global.response

data class ApiResponse<T>(
    val data: T?,
    val meta: Meta = Meta(),
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(data)

        fun empty(): ApiResponse<Unit> = ApiResponse(Unit)
    }
}
