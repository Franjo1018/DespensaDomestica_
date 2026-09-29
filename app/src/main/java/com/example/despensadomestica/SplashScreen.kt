package com.example.despensadomestica

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

/** Mismo crema de fondo que la pantalla de login (LoginScreen.FondoCrema). */
private val FondoCrema = Color(0xFFFBF3E3)

/**
 * Pantalla de carga que se muestra brevemente al abrir la app (mientras se
 * comprueba si ya hay una sesión de Firebase iniciada), antes de pasar al
 * login o directo a la despensa. Reproduce el GIF animado del logo,
 * centrado sobre el mismo fondo crema del login.
 */
@Composable
fun SplashScreen() {
    val contexto = LocalContext.current

    // ImageDecoderDecoder anima GIFs en Android 9+ (más eficiente); en
    // versiones anteriores se usa GifDecoder como respaldo.
    val cargadorImagenes = remember(contexto) {
        ImageLoader.Builder(contexto)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoCrema),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(contexto)
                .data(R.raw.animacion_carga)
                .build(),
            imageLoader = cargadorImagenes,
            contentDescription = "Cargando Despensa Doméstica",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(220.dp)
        )
    }
}
