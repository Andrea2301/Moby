package com.example.moby.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebCoverBrowserDialog(
    initialQuery: String,
    onImageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf("https://www.google.com/search?tbm=isch&q=${URLEncoder.encode(initialQuery, "UTF-8")}") }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Descargar Portada de Libro", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2D3E3E), // Color oscuro similar a la captura
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                Column(modifier = Modifier.background(Color(0xFF2D3E3E))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Consejo: Realice una pulsación larga para seleccionar una imagen",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)) // Color cian
                        ) {
                            Text("Cancelar", color = Color.White)
                        }
                        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = Color.White.copy(alpha = 0.2f))
                        Button(
                            onClick = { selectedImageUrl?.let { onImageSelected(it) } },
                            enabled = selectedImageUrl != null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))
                        ) {
                            Text("Aceptar", color = Color.White)
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                            }

                            setOnLongClickListener {
                                val result = hitTestResult
                                if (result.type == WebView.HitTestResult.IMAGE_TYPE || 
                                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                    selectedImageUrl = result.extra
                                    // Podemos dar feedback visual si queremos
                                    true
                                } else {
                                    false
                                }
                            }

                            loadUrl(currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                
                if (selectedImageUrl != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Imagen seleccionada",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
