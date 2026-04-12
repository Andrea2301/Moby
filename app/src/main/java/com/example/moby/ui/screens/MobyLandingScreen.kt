package com.example.moby.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.moby.R
import kotlinx.coroutines.delay

@Composable
fun MobyLandingScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(1200) 
        visible = false
        delay(1000) 
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(800)),
            exit = fadeOut(animationSpec = tween(1000))
        ) {
            Image(
                painter = painterResource(id = R.drawable.moby_logo_new),
                contentDescription = "Moby Logo",
                modifier = Modifier.width(280.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
