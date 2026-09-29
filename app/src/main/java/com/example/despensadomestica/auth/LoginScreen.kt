package com.example.despensadomestica.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.despensadomestica.R

/** Verde principal de la marca (logo, títulos y enlaces). */
private val VerdeDespensa = Color(0xFF1B5E3F)

/** Verde del botón principal, un poco más vivo que el de la marca. */
private val VerdeBoton = Color(0xFF2E9E4F)

/** Fondo crema de toda la pantalla de login. */
private val FondoCrema = Color(0xFFFBF3E3)

/** Pantalla de inicio de sesión, registro y recuperación de contraseña (Firebase Authentication). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    var correo by remember { mutableStateOf("") }
    var nombreUsuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var contrasenaVisible by remember { mutableStateOf(false) }
    var modoRegistro by remember { mutableStateOf(false) }
    var mostrarRecuperar by remember { mutableStateOf(false) }
    var correoRecuperar by remember { mutableStateOf("") }

    val formaCampos = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoCrema)
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(id = R.drawable.imagen_login),
            contentDescription = "Despensa Doméstica",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(220.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (modoRegistro) "Crear Cuenta" else "Iniciar Sesión",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VerdeDespensa
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (modoRegistro) {
            OutlinedTextField(
                value = nombreUsuario,
                onValueChange = { nombreUsuario = it },
                label = { Text("Nombre de usuario") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = VerdeDespensa) },
                singleLine = true,
                shape = formaCampos,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            // En registro se pide el correo real; en inicio de sesión se
            // acepta tanto el nombre de usuario como el correo.
            label = { Text(if (modoRegistro) "Correo electrónico" else "Usuario o correo electrónico") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = VerdeDespensa) },
            singleLine = true,
            shape = formaCampos,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = VerdeDespensa) },
            trailingIcon = {
                IconButton(onClick = { contrasenaVisible = !contrasenaVisible }) {
                    Icon(
                        if (contrasenaVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (contrasenaVisible) "Ocultar contraseña" else "Mostrar contraseña"
                    )
                }
            },
            visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            shape = formaCampos,
            modifier = Modifier.fillMaxWidth()
        )

        if (!modoRegistro) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "¿Olvidaste tu contraseña?",
                color = VerdeDespensa,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable {
                        correoRecuperar = correo
                        mostrarRecuperar = true
                    }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (modoRegistro) viewModel.registrarse(correo, contrasena, nombreUsuario)
                else viewModel.iniciarSesion(correo, contrasena)
            },
            enabled = !viewModel.cargando,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerdeBoton),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (modoRegistro) "Crear Cuenta" else "Iniciar Sesión", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val textoAlternar = buildAnnotatedString {
            if (modoRegistro) {
                append("¿Ya tienes cuenta? ")
                withStyle(SpanStyle(color = VerdeDespensa, fontWeight = FontWeight.Bold)) {
                    append("Inicia sesión")
                }
            } else {
                append("¿Aún no tienes cuenta? ")
                withStyle(SpanStyle(color = VerdeDespensa, fontWeight = FontWeight.Bold)) {
                    append("Regístrate aquí")
                }
            }
        }
        ClickableText(
            text = textoAlternar,
            style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
            onClick = { modoRegistro = !modoRegistro }
        )

        viewModel.mensajeError?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        viewModel.mensajeExito?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = VerdeDespensa, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (mostrarRecuperar) {
        AlertDialog(
            onDismissRequest = { mostrarRecuperar = false },
            title = { Text("Recuperar contraseña") },
            text = {
                Column {
                    Text("Ingresa tu correo y te enviaremos un enlace para restablecer tu contraseña.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correoRecuperar,
                        onValueChange = { correoRecuperar = it },
                        label = { Text("Correo electrónico") },
                        shape = formaCampos,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restablecerContrasena(correoRecuperar)
                    mostrarRecuperar = false
                }) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarRecuperar = false }) { Text("Cancelar") }
            }
        )
    }
}
