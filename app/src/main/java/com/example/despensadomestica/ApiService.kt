package com.example.despensadomestica

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    // El uid va como parámetro de la URL (?uid=...) porque una petición
    // GET no lleva cuerpo JSON: el servidor PHP filtra por esa columna
    // (firebase_uid) para devolver solo los productos de esta cuenta.
    @GET("listar_productos.php")
    suspend fun listarProductos(@Query("uid") uid: String): List<Producto>

    @POST("agregar_producto.php")
    suspend fun agregarProducto(@Body producto: Producto): Map<String, String>

    @POST("editar_producto.php")
    suspend fun editarProducto(@Body producto: Producto): Map<String, String>

    @POST("eliminar_producto.php")
    suspend fun eliminarProducto(@Body request: EliminarRequest): Map<String, String>
}
