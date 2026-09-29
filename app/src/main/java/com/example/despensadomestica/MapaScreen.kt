package com.example.despensadomestica

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Mapa con dos tipos de puntos de referencia:
 *  - Puntos de TIENDA: privados, solo los ve quien los creó (lugares con
 *    productos de despensa a buen precio), marcados con el ícono de tienda.
 *  - Puntos de DONACIÓN: globales, los ve cualquier usuario de la app
 *    (lugares para donar insumos), marcados con un ícono distinto.
 *
 * Al abrir la pantalla se pide permiso de ubicación y, si se concede, la
 * cámara se centra automáticamente en la posición actual del usuario,
 * mostrando además el punto azul de "mi ubicación". Mantener presionado
 * el mapa agrega un punto nuevo, eligiendo el tipo en el diálogo.
 */
@SuppressLint("MissingPermission") // se llama solo después de comprobar permisoUbicacionConcedido
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(viewModel: MapaViewModel = viewModel(), modifier: Modifier = Modifier) {
    val contexto = LocalContext.current

    // Se comprueba primero si el permiso ya fue concedido en un uso anterior.
    var permisoUbicacionConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                contexto,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lanzadorPermiso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido -> permisoUbicacionConcedido = concedido }

    // Se pide el permiso una sola vez, al entrar a la pantalla, si todavía no se tiene.
    LaunchedEffect(Unit) {
        if (!permisoUbicacionConcedido) {
            lanzadorPermiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Posición de partida mientras se obtiene la ubicación real (o si el
    // usuario no concede el permiso, se queda mostrando esta por defecto).
    val posicionPorDefecto = LatLng(-12.0464, -77.0428) // Lima, Perú
    val estadoCamara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(posicionPorDefecto, 12f)
    }

    // En cuanto se tiene el permiso, se pide la última ubicación conocida y
    // se centra la cámara ahí automáticamente, sin que el usuario tenga que
    // buscarse manualmente en el mapa.
    LaunchedEffect(permisoUbicacionConcedido) {
        if (permisoUbicacionConcedido) {
            LocationServices.getFusedLocationProviderClient(contexto).lastLocation
                .addOnSuccessListener { ubicacion ->
                    if (ubicacion != null) {
                        estadoCamara.position = CameraPosition.fromLatLngZoom(
                            LatLng(ubicacion.latitude, ubicacion.longitude),
                            16f
                        )
                    }
                }
        }
    }

    var ubicacionNueva by remember { mutableStateOf<LatLng?>(null) }
    var nombreNuevo by remember { mutableStateOf("") }
    var descripcionNueva by remember { mutableStateOf("") }
    // Tipo elegido en el diálogo de nuevo punto: false = tienda (privado), true = donación (global).
    var esDonacionNueva by remember { mutableStateOf(false) }

    val uidActual = viewModel.uidActual

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Mantén presionado el mapa para guardar un punto de tienda (privado) o de donación (para todos)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )
        if (!permisoUbicacionConcedido) {
            Text(
                "Sin permiso de ubicación no se puede centrar el mapa en dónde estás. Actívalo desde Ajustes del sistema si lo rechazaste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cameraPositionState = estadoCamara,
            properties = MapProperties(isMyLocationEnabled = permisoUbicacionConcedido),
            uiSettings = MapUiSettings(myLocationButtonEnabled = permisoUbicacionConcedido),
            onMapLongClick = { latLng -> ubicacionNueva = latLng }
        ) {
            // Los íconos solo se pueden crear una vez que el mapa nativo ya
            // está inicializado (por eso van aquí adentro, y no antes del
            // GoogleMap): crearlos antes provoca
            // "IBitmapDescriptorFactory is not initialized".
            val iconoTienda = remember { BitmapDescriptorFactory.fromResource(R.drawable.ic_tienda_marcador) }
            val iconoDonacion = remember { BitmapDescriptorFactory.fromResource(R.drawable.ic_donacion_marcador) }

            viewModel.puntos.value.forEach { punto ->
                // key() + remember evita crear un MarkerState nuevo en cada
                // recomposición (la advertencia de "creating a state object
                // during composition without using remember").
                key("tienda-${punto.id}") {
                    val estadoMarcador = remember(punto.id) {
                        MarkerState(position = LatLng(punto.latitud, punto.longitud))
                    }
                    Marker(
                        state = estadoMarcador,
                        title = punto.nombre,
                        snippet = punto.descripcion,
                        icon = iconoTienda
                    )
                }
            }

            viewModel.puntosDonacion.value.forEach { punto ->
                key("donacion-${punto.id}") {
                    val estadoMarcador = remember(punto.id) {
                        MarkerState(position = LatLng(punto.latitud, punto.longitud))
                    }
                    Marker(
                        state = estadoMarcador,
                        title = punto.nombre,
                        snippet = punto.descripcion,
                        icon = iconoDonacion
                    )
                }
            }
        }

        viewModel.mensajeError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        Text(
            "Tus puntos de tienda (privados):",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
            items(viewModel.puntos.value) { punto ->
                FilaPunto(
                    punto = punto,
                    puedeBorrar = true,
                    onEliminar = { viewModel.eliminarPunto(punto.id, esDonacion = false) }
                )
            }
        }

        Text(
            "Puntos de donación (de todos los usuarios):",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
            items(viewModel.puntosDonacion.value) { punto ->
                FilaPunto(
                    punto = punto,
                    puedeBorrar = uidActual != null && punto.creadoPor == uidActual,
                    onEliminar = { viewModel.eliminarPunto(punto.id, esDonacion = true) }
                )
            }
        }
    }

    ubicacionNueva?.let { ubicacion ->
        AlertDialog(
            onDismissRequest = { ubicacionNueva = null },
            title = { Text("Nuevo punto de referencia") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !esDonacionNueva,
                            onClick = { esDonacionNueva = false },
                            label = { Text("Tienda (privado)") }
                        )
                        FilterChip(
                            selected = esDonacionNueva,
                            onClick = { esDonacionNueva = true },
                            label = { Text("Donación (para todos)") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nombreNuevo,
                        onValueChange = { nombreNuevo = it },
                        label = { Text("Nombre del lugar") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = descripcionNueva,
                        onValueChange = { descripcionNueva = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.guardarPunto(
                        nombreNuevo,
                        descripcionNueva,
                        ubicacion.latitude,
                        ubicacion.longitude,
                        esDonacion = esDonacionNueva
                    )
                    nombreNuevo = ""
                    descripcionNueva = ""
                    esDonacionNueva = false
                    ubicacionNueva = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { ubicacionNueva = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun FilaPunto(punto: PuntoReferencia, puedeBorrar: Boolean, onEliminar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(punto.nombre, style = MaterialTheme.typography.bodyMedium)
            if (punto.descripcion.isNotBlank()) {
                Text(punto.descripcion, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (puedeBorrar) {
            IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar punto")
            }
        }
    }
}
