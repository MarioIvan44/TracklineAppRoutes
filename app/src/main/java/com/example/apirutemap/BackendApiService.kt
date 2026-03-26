package com.example.apirutemap

import DTOViajeResponse
import com.example.apirutemap.requests.DTOViajePatchRequest
import com.example.apirutemap.requests.DTOViajeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BackendApiService {

    @POST("/apiViaje/crear")
    suspend fun crearViaje(
        @Body request: DTOViajeRequest
    ): Response<ApiResponse<DTOViajeResponse>>

    @PATCH("/apiViaje/actualizarParcial/{id}")
    suspend fun patchViaje(
        @Path("id") id: Long,
        @Body request: DTOViajePatchRequest
    ): Response<Unit>


    @GET("/apiViaje/buscarPorId/{id}")
    suspend fun getViajeById(@Path("id") id: Long): Response<ApiResponse<DTOViajeResponse>>

    // Usamos DTOViajeRequest para el contenido a actualizar (ajusta si en backend espera otro DTO)
    @PUT("/apiViaje/actualizar/{id}")
    suspend fun updateViaje(
        @Path("id") id: Long,
        @Body dto: DTOViajeRequest
    ): Response<ApiResponse<DTOViajeResponse>>

    @GET("/apiViaje/datosViaje/userId/{idUsuario}")
    suspend fun getViajesPaginados(
        @Path("idUsuario") idUsuario: Long,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<PageResponse<DTOViajeResponse>>


}