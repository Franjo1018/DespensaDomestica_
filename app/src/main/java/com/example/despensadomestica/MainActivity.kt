package com.example.despensadomestica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DespensaScreen()
                }
            }
        }
    }
}

@Composable
fun DespensaScreen(viewModel: DespensaViewModel = viewModel()) {
    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.cargarProductos()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Gestión de Despensa Doméstica", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre del Producto") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = categoria, onValueChange = { categoria = it }, label = { Text("Categoría (Lácteos, Frutas, etc.)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cantidad, onValueChange = { cantidad = it }, label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = fecha, onValueChange = { fecha = it }, label = { Text("Fecha Vencimiento (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val idEditando = viewModel.productoEditandoId
                if (idEditando != null) {
                    viewModel.editarProducto(idEditando, nombre, categoria, cantidad, fecha)
                } else {
                    viewModel.registrarProducto(nombre, categoria, cantidad, fecha)
                }
                nombre = ""; categoria = ""; cantidad = ""; fecha = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.productoEditandoId != null) "Actualizar Alimento" else "Registrar Alimento")
        }

        if (viewModel.mensajeEstado.value.isNotEmpty()) {
            Text(text = viewModel.mensajeEstado.value, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Productos en Despensa:", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(viewModel.productos.value) { prod ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${prod.nombre} - Cantidad: ${prod.cantidad}", style = MaterialTheme.typography.bodyLarge)
                        Text("Categoría: ${prod.categoria} | Vence: ${prod.fecha_vencimiento}", style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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