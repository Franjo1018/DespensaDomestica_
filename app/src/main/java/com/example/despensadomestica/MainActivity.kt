package com.example.despensadomestica

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import coil.compose.AsyncImage
import com.example.despensadomestica.auth.AuthViewModel
import com.example.despensadomestica.auth.LoginScreen
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe llamarse antes de super.onCreate(): instala la pantalla de
        // arranque del sistema (tema Theme.DespensaDomestica.Arranque) y,
        // apenas termina, cambia solo al tema normal automáticamente
        // (gracias a postSplashScreenTheme) para que arranque la
        // SplashScreen propia de Compose con el GIF.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Canal de notificaciones de "producto por vencer" (obligatorio
        // desde Android 8 para poder mostrar cualquier notificación) y
        // programación del chequeo diario en segundo plano con
        // WorkManager. Se hace una sola vez al crear la Activity.
        crearCanalNotificacionesVencimiento(applicationContext)
        programarRevisionDeVencimientos(applicationContext)

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
 * Punto de entrada de la UI: primero se muestra una breve pantalla de carga
 * (SplashScreen) mientras se comprueba si ya hay una sesión de Firebase
 * iniciada; luego, si no hay sesión, se muestra LoginScreen, y si ya hay
 * una cuenta autenticada, se muestra directo la pantalla de la despensa.
 */
@Composable
fun AppRaiz(authViewModel: AuthViewModel = viewModel()) {
    var mostrandoSplash by remember { mutableStateOf(true) }

    // Apenas se abre la app se piden de una vez los permisos de cámara,
    // ubicación y notificaciones (Android muestra su propio pop-up del
    // sistema para cada uno): así, cuando el usuario llegue a "Nuevo
    // Alimento" o al Mapa, ya están concedidos y no hace falta pedirlos de
    // nuevo ahí, y las notificaciones de "producto por vencer" ya pueden
    // mostrarse. Si el sistema ya los tenía concedidos de antes, este
    // pop-up simplemente no aparece.
    val lanzadorPermisosIniciales = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* cada pantalla vuelve a comprobar el permiso puntual que necesita */ }

    // Se deja la pantalla de carga visible al menos 1.2 segundos, aunque
    // todo (la comprobación de sesión) esté listo antes, para que no
    // parpadee ni se sienta demasiado brusca.
    LaunchedEffect(Unit) {
        val permisos = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // POST_NOTIFICATIONS solo existe desde Android 13 (API 33); en
        // versiones anteriores pedirlo no hace falta (y el permiso ni
        // siquiera existe como constante utilizable en tiempo de compilación
        // en dispositivos viejos, por eso se comprueba el SDK del equipo).
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        lanzadorPermisosIniciales.launch(permisos.toTypedArray())
        delay(1200)
        mostrandoSplash = false
    }

    when {
        mostrandoSplash -> SplashScreen()
        authViewModel.usuario == null -> LoginScreen(authViewModel)
        else -> DespensaScreen(authViewModel = authViewModel)
    }
}

/** Categorías predefinidas para el combo box (evita errores de tipeo y datos inconsistentes). */
private val CATEGORIAS = listOf(
    "Lácteos", "Frutas", "Verduras", "Carnes", "Granos y cereales",
    "Panadería", "Bebidas", "Congelados", "Enlatados y conservas",
    "Condimentos y especias", "Snacks", "Otros"
)

/** Mismo crema de fondo que el login y la pantalla de carga. */
private val FondoCrema = Color(0xFFFBF3E3)

/** Crema un poco más oscuro que el fondo, para la barra superior. */
private val BarraCrema = Color(0xFFEFDFC0)

/** Convierte los millis (UTC) que devuelve el DatePicker de Compose a "AAAA-MM-DD". */
private fun formatearFecha(millis: Long): String {
    val formato = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date(millis))
}

/** Color para "Pronto a vencer" en el inventario (mismo límite que la notificación: 7 días o menos). */
private val NaranjaPorVencer = Color(0xFFE08E45)

/**
 * Días que faltan para que venza un producto (negativo si ya venció), o
 * null si la fecha guardada no se pudo interpretar (formato "yyyy-MM-dd",
 * el mismo que guarda el selector de fecha). Se usa para resaltar en el
 * inventario los productos próximos a vencer o ya vencidos.
 */
private fun diasParaVencer(fechaVencimiento: String): Long? {
    return try {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(fechaVencimiento))
    } catch (e: DateTimeParseException) {
        null
    }
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

/** Las tres secciones de la despensa, mostradas desde el menú principal. */
private enum class PantallaDespensa { MENU, NUEVO, MAPA, INVENTARIO }

/**
 * Pantalla principal de la despensa: un menú con tres opciones (Nuevo
 * Alimento, Mapa e Inventario). El formulario y la lista de productos vivían
 * antes juntos en una sola pantalla; ahora cada uno es su propia sección, y
 * este composable solo decide cuál mostrar (sin librería de navegación
 * aparte, con un enum simple, igual que ya se hacía para el mapa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespensaScreen(
    authViewModel: AuthViewModel,
    // key = uid del usuario actual: obliga a crear un DespensaViewModel
    // (y su Repository/Room) NUEVO cada vez que cambia el usuario con
    // sesión iniciada. Sin esta key, viewModel() reutilizaría la MISMA
    // instancia entre una cuenta y otra (porque ambas comparten la misma
    // Activity), y con ella se arrastraban datos ya cargados de la
    // cuenta anterior aunque las consultas a Room ya estén filtradas por
    // uid.
    viewModel: DespensaViewModel = viewModel(key = authViewModel.usuario?.uid ?: "sin_sesion")
) {
    var pantalla by remember { mutableStateOf(PantallaDespensa.MENU) }

    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }
    var imagenSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var categoriaExpandida by remember { mutableStateOf(false) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    // Al tocar la foto del producto se pregunta primero cámara o galería.
    var mostrarSelectorFuenteImagen by remember { mutableStateOf(false) }

    val contexto = LocalContext.current
    // Uri temporal (creada justo antes de abrir la cámara) donde queda
    // guardada la foto recién tomada.
    var uriCamaraPendiente by remember { mutableStateOf<Uri?>(null) }

    val selectorImagen = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imagenSeleccionada = uri }

    val selectorCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { exito -> if (exito) imagenSeleccionada = uriCamaraPendiente }

    fun lanzarCamara() {
        // Se crea un archivo temporal nuevo cada vez (dentro del caché
        // propio de la app) y se comparte su Uri con la app de cámara a
        // través del FileProvider declarado en el Manifest.
        val archivo = File.createTempFile("foto_producto_", ".jpg", contexto.cacheDir)
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
        uriCamaraPendiente = uri
        selectorCamara.launch(uri)
    }

    val lanzadorPermisoCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido -> if (concedido) lanzarCamara() }

    fun abrirCamara() {
        val permisoConcedido = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (permisoConcedido) {
            lanzarCamara()
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    fun limpiarFormulario() {
        nombre = ""; categoria = ""; cantidad = ""; fecha = ""; imagenSeleccionada = null
        viewModel.productoEditandoId = null
    }

    // El ViewModel ya carga los productos (Room + sincronización con el
    // servidor) automáticamente al crearse, no hace falta pedirlo aquí.

    // Botón de "atrás" del sistema (el de la barra de navegación de
    // Android, no la flecha de la barra superior): mientras se esté en
    // cualquier pantalla que no sea el menú, en vez de cerrar la app hace
    // lo mismo que la flecha de "volver" de arriba. Se desactiva
    // (enabled = false) estando en el menú, así ahí sí se usa el
    // comportamiento normal de Android (salir/minimizar la app).
    BackHandler(enabled = pantalla != PantallaDespensa.MENU) {
        if (pantalla == PantallaDespensa.NUEVO) limpiarFormulario()
        pantalla = PantallaDespensa.MENU
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (pantalla) {
                                PantallaDespensa.MENU -> "Despensa Doméstica"
                                PantallaDespensa.NUEVO ->
                                    if (viewModel.productoEditandoId != null) "Editar Alimento" else "Nuevo Alimento"
                                PantallaDespensa.MAPA -> "Puntos de Referencia"
                                PantallaDespensa.INVENTARIO -> "Inventario"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        // El nombre de usuario se guarda como displayName en el
                        // propio perfil de Firebase Authentication (no requiere
                        // una tabla ni colección aparte en la base de datos).
                        // Se muestra en todas las pantallas, no solo en el menú.
                        val nombreUsuario = authViewModel.usuario?.displayName
                        Text(
                            if (!nombreUsuario.isNullOrBlank()) "Bienvenido, $nombreUsuario"
                            else authViewModel.usuario?.email ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    if (pantalla != PantallaDespensa.MENU) {
                        IconButton(onClick = {
                            if (pantalla == PantallaDespensa.NUEVO) limpiarFormulario()
                            pantalla = PantallaDespensa.MENU
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al menú")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { authViewModel.cerrarSesion() }) {
                        Text("Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BarraCrema)
            )
        }
    ) { paddingInterno ->
        Box(
            modifier = Modifier
                .padding(paddingInterno)
                .fillMaxSize()
                .background(FondoCrema)
        ) {
            when (pantalla) {
                PantallaDespensa.MENU -> MenuPrincipal(
                    onNuevoAlimento = { limpiarFormulario(); pantalla = PantallaDespensa.NUEVO },
                    onMapa = { pantalla = PantallaDespensa.MAPA },
                    onInventario = { pantalla = PantallaDespensa.INVENTARIO }
                )

                PantallaDespensa.NUEVO -> NuevoAlimentoFormulario(
                    viewModel = viewModel,
                    nombre = nombre,
                    onNombreChange = { nombre = it },
                    categoria = categoria,
                    onCategoriaChange = { categoria = it },
                    cantidad = cantidad,
                    onCantidadChange = { cantidad = it },
                    fecha = fecha,
                    onFechaChange = { fecha = it },
                    imagenSeleccionada = imagenSeleccionada,
                    onSeleccionarImagen = { mostrarSelectorFuenteImagen = true },
                    categoriaExpandida = categoriaExpandida,
                    onCategoriaExpandidaChange = { categoriaExpandida = it },
                    mostrarSelectorFecha = mostrarSelectorFecha,
                    onMostrarSelectorFechaChange = { mostrarSelectorFecha = it },
                    onGuardar = {
                        val idEditando = viewModel.productoEditandoId
                        if (idEditando != null) {
                            viewModel.editarProducto(idEditando, nombre, categoria, cantidad, fecha, imagenSeleccionada)
                        } else {
                            viewModel.registrarProducto(nombre, categoria, cantidad, fecha, imagenSeleccionada)
                        }
                        limpiarFormulario()
                        pantalla = PantallaDespensa.MENU
                    }
                )

                PantallaDespensa.MAPA -> MapaScreen()

                PantallaDespensa.INVENTARIO -> InventarioScreen(
                    viewModel = viewModel,
                    onEditar = { prod ->
                        nombre = prod.nombre
                        categoria = prod.categoria
                        cantidad = prod.cantidad.toString()
                        fecha = prod.fecha_vencimiento
                        viewModel.productoEditandoId = prod.id
                        pantalla = PantallaDespensa.NUEVO
                    }
                )
            }
        }
    }

    if (mostrarSelectorFuenteImagen) {
        AlertDialog(
            onDismissRequest = { mostrarSelectorFuenteImagen = false },
            title = { Text("Foto del producto") },
            text = { Text("¿Cómo quieres agregar la foto?") },
            confirmButton = {
                TextButton(onClick = {
                    mostrarSelectorFuenteImagen = false
                    abrirCamara()
                }) { Text("Tomar foto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarSelectorFuenteImagen = false
                    selectorImagen.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("Elegir de galería") }
            }
        )
    }
}

/**
 * Menú principal de la despensa: tres tarjetas grandes (una por sección),
 * inspiradas en la plantilla de tiles de colores que mandó el usuario, pero
 * en una sola columna para que sean fáciles de tocar con una mano.
 */
@Composable
private fun MenuPrincipal(
    onNuevoAlimento: () -> Unit,
    onMapa: () -> Unit,
    onInventario: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoCrema)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TarjetaMenu(
            titulo = "Nuevo Alimento",
            subtitulo = "Registra un producto en tu despensa",
            icono = Icons.Default.Add,
            colorFondo = Color(0xFF2E9E4F),
            onClick = onNuevoAlimento
        )
        TarjetaMenu(
            titulo = "Mapa",
            subtitulo = "Puntos de referencia con productos baratos",
            icono = Icons.Default.Place,
            colorFondo = Color(0xFFE08E45),
            onClick = onMapa
        )
        TarjetaMenu(
            titulo = "Inventario",
            subtitulo = "Busca y revisa todos tus productos",
            icono = Icons.Default.List,
            colorFondo = Color(0xFF3E7CB1),
            onClick = onInventario
        )
    }
}

/** Una tarjeta del menú principal: icono en un cuadro de color + título y subtítulo. */
@Composable
private fun TarjetaMenu(
    titulo: String,
    subtitulo: String,
    icono: ImageVector,
    colorFondo: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorFondo),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

/**
 * Formulario para registrar o editar un producto, con la misma paleta
 * crema clara/oscura del resto de la app: la foto elegida hace de "imagen
 * del producto" (como los íconos de fruta de la plantilla), la cantidad se
 * elige con un contador +/- en vez de escribirla, y termina con un botón
 * grande y redondeado, igual que el "Checkout" de la plantilla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuevoAlimentoFormulario(
    viewModel: DespensaViewModel,
    nombre: String,
    onNombreChange: (String) -> Unit,
    categoria: String,
    onCategoriaChange: (String) -> Unit,
    cantidad: String,
    onCantidadChange: (String) -> Unit,
    fecha: String,
    onFechaChange: (String) -> Unit,
    imagenSeleccionada: Uri?,
    onSeleccionarImagen: () -> Unit,
    categoriaExpandida: Boolean,
    onCategoriaExpandidaChange: (Boolean) -> Unit,
    mostrarSelectorFecha: Boolean,
    onMostrarSelectorFechaChange: (Boolean) -> Unit,
    onGuardar: () -> Unit
) {
    val formaCampos = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Foto del producto: hace de "imagen" del alimento, igual que los
        // íconos de fruta de la plantilla, sobre un cuadro crema oscuro.
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(24.dp))
                .background(BarraCrema)
                .clickable(onClick = onSeleccionarImagen),
            contentAlignment = Alignment.Center
        ) {
            if (imagenSeleccionada != null) {
                AsyncImage(
                    model = imagenSeleccionada,
                    contentDescription = "Foto del producto",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color(0xFF8A7A5C))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Agregar foto", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A7A5C))
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = onNombreChange,
            label = { Text("Nombre del Producto") },
            shape = formaCampos,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Combo box de categoría: el usuario elige de la lista, no escribe libremente.
        ExposedDropdownMenuBox(
            expanded = categoriaExpandida,
            onExpandedChange = onCategoriaExpandidaChange
        ) {
            OutlinedTextField(
                value = categoria,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaExpandida) },
                shape = formaCampos,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = categoriaExpandida,
                onDismissRequest = { onCategoriaExpandidaChange(false) }
            ) {
                CATEGORIAS.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion) },
                        onClick = {
                            onCategoriaChange(opcion)
                            onCategoriaExpandidaChange(false)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Fecha de vencimiento con selector de calendario (DatePicker de Material3).
        OutlinedTextField(
            value = fecha,
            onValueChange = {},
            readOnly = true,
            label = { Text("Fecha de vencimiento") },
            trailingIcon = {
                IconButton(onClick = { onMostrarSelectorFechaChange(true) }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Elegir fecha")
                }
            },
            shape = formaCampos,
            modifier = Modifier.fillMaxWidth()
        )

        if (mostrarSelectorFecha) {
            val estadoFecha = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { onMostrarSelectorFechaChange(false) },
                confirmButton = {
                    TextButton(onClick = {
                        estadoFecha.selectedDateMillis?.let { millis -> onFechaChange(formatearFecha(millis)) }
                        onMostrarSelectorFechaChange(false)
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { onMostrarSelectorFechaChange(false) }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = estadoFecha)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Cantidad como contador +/- (en vez de un campo de texto suelto),
        // con los mismos botones verdes redondeados de la plantilla.
        Card(
            shape = formaCampos,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cantidad", style = MaterialTheme.typography.bodyLarge)
                val valorActual = cantidad.toIntOrNull() ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (valorActual > 0) onCantidadChange((valorActual - 1).toString()) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BarraCrema)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Quitar uno", tint = Color(0xFF5C4A2A))
                    }
                    Text(
                        text = valorActual.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(
                        onClick = { onCantidadChange((valorActual + 1).toString()) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E9E4F))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar uno", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGuardar,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E9E4F)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                if (viewModel.productoEditandoId != null) "Actualizar Alimento" else "Registrar Alimento",
                fontSize = 16.sp
            )
        }

        if (viewModel.mensajeEstado.value.isNotEmpty()) {
            Text(
                text = viewModel.mensajeEstado.value,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Inventario de productos con buscador: filtra en vivo tanto por nombre
 * como por categoría (un solo campo de texto que busca en los dos a la
 * vez), sin necesidad de un dropdown aparte.
 */
@Composable
private fun InventarioScreen(viewModel: DespensaViewModel, onEditar: (Producto) -> Unit) {
    var busqueda by remember { mutableStateOf("") }

    val productos = viewModel.productos.value
    val productosFiltrados = remember(busqueda, productos) {
        val consulta = busqueda.trim()
        if (consulta.isEmpty()) {
            productos
        } else {
            productos.filter { prod ->
                prod.nombre.contains(consulta, ignoreCase = true) ||
                    prod.categoria.contains(consulta, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar por nombre o categoría") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (productosFiltrados.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (productos.isEmpty()) "Todavía no registraste ningún producto"
                    else "No se encontró ningún producto con \"$busqueda\"",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(productosFiltrados) { prod ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (prod.imagenBase64 != null) {
                                ImagenProducto(base64 = prod.imagenBase64, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${prod.nombre} - Cantidad: ${prod.cantidad}", style = MaterialTheme.typography.bodyLarge)
                                val diasRestantes = diasParaVencer(prod.fecha_vencimiento)
                                Text(
                                    "Categoría: ${prod.categoria} | Vence: ${prod.fecha_vencimiento}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        diasRestantes == null -> Color.Unspecified
                                        diasRestantes < 0 -> Color.Red
                                        diasRestantes <= 7 -> NaranjaPorVencer
                                        else -> Color.Unspecified
                                    },
                                    fontWeight = if (diasRestantes != null && diasRestantes <= 7) FontWeight.Bold else FontWeight.Normal
                                )
                                if (diasRestantes != null && diasRestantes < 0) {
                                    Text("Venció", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else if (diasRestantes != null && diasRestantes <= 7) {
                                    Text("Pronto a vencer", style = MaterialTheme.typography.labelSmall, color = NaranjaPorVencer, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { onEditar(prod) }) {
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
