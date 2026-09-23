package com.example.despensadomestica.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferenciasDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "preferencias_despensa"
)

/**
 * Acceso a las preferencias simples de la app usando Jetpack DataStore
 * (reemplazo moderno de SharedPreferences): guarda la fecha/hora de la
 * última sincronización con el servidor y el filtro de categoría elegido.
 */
class PreferenciasDataStore(private val context: Context) {

    companion object {
        private val ULTIMA_SINCRONIZACION = longPreferencesKey("ultima_sincronizacion")
        private val CATEGORIA_FILTRO = stringPreferencesKey("categoria_filtro")
    }

    val ultimaSincronizacion: Flow<Long> = context.preferenciasDataStore.data
        .map { prefs -> prefs[ULTIMA_SINCRONIZACION] ?: 0L }

    val categoriaFiltro: Flow<String> = context.preferenciasDataStore.data
        .map { prefs -> prefs[CATEGORIA_FILTRO] ?: "Todas" }

    suspend fun guardarUltimaSincronizacion(timestamp: Long) {
        context.preferenciasDataStore.edit { prefs -> prefs[ULTIMA_SINCRONIZACION] = timestamp }
    }

    suspend fun guardarCategoriaFiltro(categoria: String) {
        context.preferenciasDataStore.edit { prefs -> prefs[CATEGORIA_FILTRO] = categoria }
    }
}
