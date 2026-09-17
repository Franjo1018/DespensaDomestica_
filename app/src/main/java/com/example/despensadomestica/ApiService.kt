package com.example.despensadomestica

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("listar_productos.php")
    suspend fun listarProductos(): List<Producto>

    @POST("agregar_producto.php")
    suspend fun agregarProducto(@Body producto: Producto): Map<String, String>

    @POST("editar_producto.php")
    suspend fun editarProducto(@Body producto: Producto): Map<String, String>

    @POST("eliminar_producto.php")
    suspend fun eliminarProducto(@Body request: EliminarRequest): Map<String, String>
}