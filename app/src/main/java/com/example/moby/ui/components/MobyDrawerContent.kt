package com.example.moby.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.moby.MobyScreen

data class DrawerItem(val label: String, val icon: ImageVector, val screen: MobyScreen)

@Composable
fun MobyDrawerContent(onNavigate: (MobyScreen) -> Unit) {
    val items = remember {
        listOf(
            DrawerItem("Recent", Icons.Default.History, MobyScreen.Recent),
            DrawerItem("Library", Icons.Default.LocalLibrary, MobyScreen.Library),
            DrawerItem("Bookmarks", Icons.Default.Bookmarks, MobyScreen.Bookmarks),
            DrawerItem("Journal", Icons.Default.MenuBook, MobyScreen.Journal)
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        TextButton(onClick = { onNavigate(MobyScreen.Home) }) {
            Text(
                "MOBY",
                modifier = Modifier.padding(start = 18.dp, bottom = 12.dp),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )

        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label, style = MaterialTheme.typography.bodyLarge) },
                selected = false,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .height(56.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.background,
                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = RoundedCornerShape(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f, fill = false))
        
        Text(
            "v1.1.0",
            modifier = Modifier.padding(28.dp).align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
