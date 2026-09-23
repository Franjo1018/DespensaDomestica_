package com.example.despensadomestica

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DateRange
import coil.compose.AsyncImage
import com.example.despensadomestica.auth.AuthViewModel
import com.example.despensadomestica.auth.LoginScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRaiz()
                }
            }
        }
    }
}

/**
 * Punto de entrada de la UI: mientras no haya sesión iniciada (Firebase
 * Authentication) se muestra LoginScreen; una vez autenticado, se
 * muestra la pantalla principal de la despensa.
 */
@Composable
fun AppRaiz(authViewModel: AuthViewModel = viewModel()) {
    if (authViewModel.usuario == null) {
        LoginScreen(authViewModel)
    } else {
        DespensaScreen(authViewModel = authViewModel)
    }
}

/** Categorías predefinidas para el combo box (evita errores de tipeo y datos inconsistentes). */
private val CATEGORIAS = listOf(
    "Lácteos", "Frutas", "Verduras", "Carnes", "Granos y cereales",
    "Panadería", "Bebidas", "Congelados", "Enlatados y conservas",
    "Condimentos y especias", "Snacks", "Otros"
)

/** Convierte los millis (UTC) que devuelve el DatePicker de Compose a "AAAA-MM-DD". */
private fun formatearFecha(millis: Long): String {
    val formato = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date(millis))
}

/** Decodifica y muestra una foto guardada como texto Base64 (sin Firebase Storage). */
@Composable
fun ImagenProducto(base64: String?, modifier: Modifier = Modifier) {
    if (base64 == null) return
    val bitmap = remember(base64) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Foto del producto",
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespensaScreen(viewModel: DespensaViewModel = viewModel(), authViewModel: AuthViewModel) {
    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }
    var imagenSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var categoriaExpandida by remember { mutableStateOf(false) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }

    val selectorImagen = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imagenSeleccionada = uri }

    // El ViewModel ya carga los productos (Room + sincronización con el
    // servidor) automáticamente al crearse, no hace falta pedirlo aquí.

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Despensa Doméstica", style = MaterialTheme.typography.titleLarge)
                        Text(
                            authViewModel.usuario?.email ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { authViewModel.cerrarSesion() }) {
                        Text("Cerrar sesión")
                    }
                }
            )
        }
    ) { paddingInterno ->
        Column(
            modifier = Modifier
                .padding(paddingInterno)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre del Producto") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            // Combo box de categoría: el usuario elige de la lista, no escribe libremente.
            ExposedDropdownMenuBox(
                expanded = categoriaExpandida,
                onExpandedChange = { categoriaExpandida = it }
            ) {
                OutlinedTextField(
                    value = categoria,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaExpandida) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoriaExpandida,
                    onDismissRequest = { categoriaExpandida = false }
                ) {
                    CATEGORIAS.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                categoria = opcion
                                categoriaExpandida = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = cantidad, onValueChange = { cantidad = it }, label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            // Fecha de vencimiento con selector de calendario (DatePicker de Material3).
            OutlinedTextField(
                value = fecha,
                onValueChange = {},
                readOnly = true,
                label = { Text("Fecha de vencimiento") },
                trailingIcon = {
                    IconButton(onClick = { mostrarSelectorFecha = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (mostrarSelectorFecha) {
                val estadoFecha = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { mostrarSelectorFecha = false },
                    confirmButton = {
                        TextButton(onClick = {
                            estadoFecha.selectedDateMillis?.let { millis -> fecha = formatearFecha(millis) }
                            mostrarSelectorFecha = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") }
                    }
                ) {
                    DatePicker(state = estadoFecha)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    selectorImagen.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = "Agregar foto")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (imagenSeleccionada != null) "Foto seleccionada" else "Agregar foto")
                }
                if (imagenSeleccionada != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Vista previa local del archivo elegido (todavía no se ha comprimido/guardado).
                    AsyncImage(
                        model = imagenSeleccionada,
                        contentDescription = "Vista previa",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val idEditando = viewModel.productoEditandoId
                    if (idEditando != null) {
                        viewModel.editarProducto(idEditando, nombre, categoria, cantidad, fecha, imagenSeleccionada)
                    } else {
                        viewModel.registrarProducto(nombre, categoria, cantidad, fecha, imagenSeleccionada)
                    }
                    nombre = ""; categoria = ""; cantidad = ""; fecha = ""; imagenSeleccionada = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (viewModel.productoEditandoId != null) "Actualizar Alimento" else "Registrar Alimento")
            }

            if (viewModel.mensajeEstado.value.isNotEmpty()) {
                Text(text = viewModel.mensajeEstado.value, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Productos en Despensa:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            // weight(1f) hace que la lista ocupe todo el espacio restante de la
            // pantalla (y se pueda desplazar), en vez de dejar un hueco vacío
            // abajo cuando hay pocos productos.
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.productos.value) { prod ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (prod.imagenBase64 != null) {
                                ImagenProducto(base64 = prod.imagenBase64, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${prod.nombre} - Cantidad: ${prod.cantidad}", style = MaterialTheme.typography.bodyLarge)
                                Text("Categoría: ${prod.categoria} | Vence: ${prod.fecha_vencimiento}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                nombre = prod.nombre
                                categoria = prod.categoria
                                cantidad = prod.cantidad.toString()
                                fecha = prod.fecha_vencimiento
                                viewModel.productoEditandoId = prod.id
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { viewModel.eliminarProducto(prod.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }
}
