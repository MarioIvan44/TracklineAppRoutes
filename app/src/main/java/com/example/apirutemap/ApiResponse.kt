package com.example.apirutemap

data class ApiResponse<T>(
    val status: String,
    val data: T
)
