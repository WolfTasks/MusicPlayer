package com.wolf.musicplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wolf.musicplayer.ui.component.MiniPlayer
import com.wolf.musicplayer.ui.screen.album.AlbumScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistScreen
import com.wolf.musicplayer.ui.screen.library.LibraryScreen
import com.wolf.musicplayer.ui.screen.player.PlayerScreen
import com.wolf.musicplayer.ui.screen.player.PlayerViewModel
import com.wolf.musicplayer.ui.screen.playlist.PlaylistScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Library : Screen("library", "Bibliothek", Icons.Default.LibraryMusic)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.QueueMusic)
    object Artists : Screen("artists", "Künstler", Icons.Default.Person)
    object Albums : Screen("albums", "Alben", Icons.Default.Album)
    object Player : Screen("player", "Jetzt läuft", Icons.Default.PlayCircle)
}

private val tabs = listOf(
    Screen.Library, Screen.Playlists, Screen.Artists, Screen.Albums, Screen.Player
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Library.route) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    }
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistScreen(contentPadding = innerPadding)
            }
            composable(Screen.Artists.route) {
                ArtistScreen(contentPadding = innerPadding)
            }
            composable(Screen.Albums.route) {
                AlbumScreen(contentPadding = innerPadding)
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}
