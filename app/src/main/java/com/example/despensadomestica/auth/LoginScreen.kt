package com.example.despensadomestica.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Pantalla de inicio de sesión / registro con Firebase Authentication
 * (correo y contraseña). Se muestra cuando AuthViewModel.usuario es null.
 */
@Composable
fun LoginScreen(authViewModel: AuthViewModel) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Despensa Doméstica", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Inicia sesión o crea una cuenta para respaldar tu despensa en la nube",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            label = { Text("Correo electrónico") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (authViewModel.mensajeError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = authViewModel.mensajeError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (authViewModel.mensajeExito != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = authViewModel.mensajeExito ?: "",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { authViewModel.iniciarSesion(correo.trim(), contrasena) },
            enabled = !authViewModel.cargando && correo.isNotBlank() && contrasena.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar sesión")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { authViewModel.registrarse(correo.trim(), contrasena) },
            enabled = !authViewModel.cargando && correo.isNotBlank() && contrasena.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear cuenta")
        }

        if (authViewModel.cargando) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
