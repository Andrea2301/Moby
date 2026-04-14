package com.example.moby.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moby.models.Publication

@Composable
fun FeaturedBookCard(
    publication: Publication,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    // ── Animación de entrada ─────────────────────────────────────────────────
    // El libro arranca rotado (-35°) y se endereza al valor de reposo (0°).
    // Cuando el usuario presiona, inclina levemente hacia atrás (-8°).
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }

    val entranceRotation by animateFloatAsState(
        targetValue = if (hasEntered) 0f else -35f,
        animationSpec = spring(
            dampingRatio = 0.6f,          // Rebote suave — como el gif
            stiffness    = Spring.StiffnessLow
        ),
        label = "entranceRotation"
    )

    // Rotación al presionar — se suma a la de entrada
    val pressRotation by animateFloatAsState(
        targetValue = if (isPressed) -8f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "pressRotation"
    )

    // Elevación de entrada: el libro "sube" ligeramente al aparecer
    val entranceTranslateY by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 40f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness    = Spring.StiffnessLow
        ),
        label = "entranceTranslateY"
    )

    // La sombra se intensifica cuando el libro está más rotado (más volumen)
    val shadowElevation by animateDpAsState(
        targetValue = when {
            isPressed   -> 55.dp
            !hasEntered -> 10.dp
            else        -> 40.dp
        },
        animationSpec = tween(300),
        label = "shadowElevation"
    )

    // Luz especular en la portada — más visible al presionar
    val lightOpacity by animateFloatAsState(
        targetValue = if (isPressed) 0.18f else 0.07f,
        animationSpec = tween(250),
        label = "lightOpacity"
    )

    // Opacidad del borde de páginas — más visible con más rotación
    val pageEdgeOpacity by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 0.3f,
        animationSpec = tween(250),
        label = "pageEdgeOpacity"
    )

    // Ancho del lomo — crece al girar
    val spineWidth by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 8.dp,
        animationSpec = tween(250),
        label = "spineWidth"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Contenedor del libro con lomo lateral ────────────────────────────
        // El Row permite colocar el lomo a la izquierda del libro,
        // simulando que tiene profundidad real.
        Row(
            modifier = Modifier
                .width(200.dp + spineWidth)
                .height(320.dp)
                .graphicsLayer(
                    rotationY        = entranceRotation + pressRotation,
                    cameraDistance   = 10f * with(density) { 1.dp.toPx() },
                    translationY     = entranceTranslateY,
                    transformOrigin  = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── LOMO DEL LIBRO ────────────────────────────────────────────────
            // Gradiente de luz: blanco casi transparente en el borde exterior
            // (donde la luz incide) y se desvanece hacia la portada.
            // Simula el canto iluminado de un libro físico.
            Box(
                modifier = Modifier
                    .width(spineWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.55f),  // borde exterior: luz
                                0.3f to Color.White.copy(alpha = 0.18f),  // transición suave
                                1.0f to Color.White.copy(alpha = 0.04f)   // funde hacia portada
                            )
                        )
                    )
            )

            // ── PORTADA ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .shadow(
                        elevation   = shadowElevation,
                        shape       = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor   = Color.Black.copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication        = null
                    ) { onContinueReading() }
            ) {
                // Imagen de portada
                AsyncImage(
                    model              = publication.coverUrl,
                    contentDescription = "Portada de ${publication.title}",
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop
                )

                // Luz especular — gradiente desde la izquierda al presionar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = lightOpacity),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX   = 220f
                            )
                        )
                )

                // Sombra de encuadernación — línea sutil en el borde izquierdo
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                )

            }

            // ── CANTO DE HOJAS ────────────────────────────────────────────────
            // Fuera de la portada — son el borde derecho real del libro.
            // Líneas blancas apiladas que simulan las páginas vistas de canto.
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
            ) {
                // Sombra base que da cuerpo al canto
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight(0.96f)
                        .align(Alignment.Center)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFDDDDDD).copy(alpha = 0.6f),
                                    Color(0xFFBBBBBB).copy(alpha = 0.3f)
                                )
                            )
                        )
                )
                // Líneas de hojas — blancas y visibles
                repeat(6) { index ->
                    val heightFraction = 0.92f - index * 0.013f
                    Box(
                        modifier = Modifier
                            .width(1.2.dp)
                            .fillMaxHeight(heightFraction)
                            .align(Alignment.Center)
                            .offset(x = (index * 1.4f).dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = pageEdgeOpacity * 0.5f),
                                        Color.White.copy(alpha = pageEdgeOpacity),
                                        Color.White.copy(alpha = pageEdgeOpacity * 0.5f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = publication.title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.fillMaxWidth(0.85f).wrapContentHeight()
        )

        Text(
            text     = publication.author,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        if (publication.totalPages > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(0.85f).height(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress   = { publication.progress },
                    modifier   = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "${(publication.progress * 100).toInt()}%",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}