package com.example.moby
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.moby.data.db.MobyDatabase
import com.example.moby.logic.BookMetadataExtractor
import com.example.moby.data.PreferencesManager
import com.example.moby.ui.components.MobyDrawerContent
import com.example.moby.ui.components.MobyTopBar
import com.example.moby.ui.screens.*
import com.example.moby.ui.theme.MobyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: MobyDatabase
    private lateinit var metadataExtractor: BookMetadataExtractor

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Animación de salida instantánea para pasar de inmediato a tu transición
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.remove()
        }
        super.onCreate(savedInstanceState)
        
        preferencesManager = PreferencesManager(this)
        database = MobyDatabase.getDatabase(this)
        metadataExtractor = BookMetadataExtractor(this)
        
        enableEdgeToEdge()
        setContent {
            // Collect theme preference from DataStore
            val isAbisal by preferencesManager.isAbisalFlow.collectAsState(initial = false)
            
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            var currentScreen: MobyScreen by remember { mutableStateOf(MobyScreen.Home) }
            var showLanding by remember { mutableStateOf(true) }

            if (showLanding) {
                MobyLandingScreen(onFinished = { showLanding = false })
            } else {
                // Intercept system Back gestures
                androidx.activity.compose.BackHandler(enabled = currentScreen != MobyScreen.Home) {
                    if (currentScreen is MobyScreen.Reader) {
                        currentScreen = MobyScreen.Library
                    } else {
                        currentScreen = MobyScreen.Home
                    }
                }

                MobyTheme(darkTheme = isAbisal) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        MobyDrawerContent(
                            onNavigate = { screen ->
                                currentScreen = screen
                                scope.launch { drawerState.close() }
                            }
                        )
                    },
                    gesturesEnabled = currentScreen !is MobyScreen.Reader
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (currentScreen !is MobyScreen.Reader) {
                                MobyTopBar(
                                    isAbisal = isAbisal,
                                    currentScreen = currentScreen,
                                    onThemeToggle = { 
                                        scope.launch { 
                                            preferencesManager.setAbisal(!isAbisal) 
                                        }
                                    },
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                    onTitleClick = { currentScreen = MobyScreen.Home }
                                )
                            }
                        }
                    ) { innerPadding ->
                        // 🚀 IMMERSIVE FIX: Do NOT apply innerPadding if we are in the Reader
                        val modifier = if (currentScreen is MobyScreen.Reader) {
                            Modifier.fillMaxSize() 
                        } else {
                            Modifier.padding(innerPadding).fillMaxSize()
                        }

                        Box(modifier = modifier) {
                            when (currentScreen) {
                                MobyScreen.Home -> HomeScreen(
                                    isAbisal = isAbisal,
                                    publicationDao = database.publicationDao(),
                                    onNavigate = { screen -> currentScreen = screen }
                                )
                                MobyScreen.Recent -> RecentScreen(
                                    publicationDao = database.publicationDao(),
                                    onNavigate = { screen -> currentScreen = screen }
                                )
                                MobyScreen.Library -> LibraryScreen(
                                    publicationDao = database.publicationDao(),
                                    metadataExtractor = metadataExtractor,
                                    preferencesManager = preferencesManager,
                                    onNavigate = { screen -> currentScreen = screen }
                                )
                                MobyScreen.Bookmarks -> BookmarksScreen()
                                MobyScreen.Journal -> JournalScreen()
                                is MobyScreen.Reader -> ReaderScreen(
                                    publicationId = (currentScreen as MobyScreen.Reader).publicationId,
                                    onBack = { currentScreen = MobyScreen.Library },
                                    isAbisal = isAbisal,
                                    preferencesManager = preferencesManager)//  PASS MEMORY
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
