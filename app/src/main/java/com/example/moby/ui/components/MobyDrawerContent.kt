package com.example.moby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moby.MobyScreen
import com.example.moby.R

data class DrawerItem(val label: String, val icon: ImageVector, val screen: MobyScreen)

@Composable
fun MobyDrawerContent(
    currentScreen: MobyScreen,
    onNavigate: (MobyScreen) -> Unit
) {
    val items = remember {
        listOf(
            DrawerItem("Recent", Icons.Default.History, MobyScreen.Recent),
            DrawerItem("Library", Icons.Default.LocalLibrary, MobyScreen.Library),
            DrawerItem("Bookmarks", Icons.Default.Bookmarks, MobyScreen.Bookmarks),
            DrawerItem("Journal", Icons.Default.MenuBook, MobyScreen.Journal)
        )
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.background
        )
    )

    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent, // We use the gradient instead
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier.width(300.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(60.dp))

                // --- PREMIUM HEADER ---
                DrawerHeader(onClick = { onNavigate(MobyScreen.Home) })

                Spacer(Modifier.height(32.dp))
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                )

                Spacer(Modifier.height(24.dp))

                // --- MENU ITEMS ---
                items.forEach { item ->
                    val isSelected = when (currentScreen) {
                        is MobyScreen.Home -> false
                        is MobyScreen.Reader -> false
                        else -> currentScreen.name == item.screen.name
                    }

                    CustomDrawerItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.screen) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // --- FOOTER ---
                Text(
                    "MOBY READER • v1.1.0",
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun DrawerHeader(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_moby_logo_white),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "MOBY",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "DIVE INTO STORIES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun CustomDrawerItem(
    item: DrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor
            )
            
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
