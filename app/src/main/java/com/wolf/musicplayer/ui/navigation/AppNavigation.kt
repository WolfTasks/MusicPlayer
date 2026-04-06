package com.wolf.musicplayer.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wolf.musicplayer.ui.screen.album.AlbumDetailScreen
import com.wolf.musicplayer.ui.screen.album.AlbumScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistDetailScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistScreen
import com.wolf.musicplayer.ui.screen.library.LibraryScreen
import com.wolf.musicplayer.ui.screen.player.PlayerScreen
import com.wolf.musicplayer.ui.screen.player.PlayerViewModel
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailScreen
import com.wolf.musicplayer.ui.screen.playlist.PlaylistScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Library : Screen("library", "Bibliothek", Icons.Default.LibraryMusic)

    object Playlists : Screen("playlists", "Playlists", Icons.Default.QueueMusic)

    object Artists : Screen("artists", "Künstler", Icons.Default.Person)

    object Albums : Screen("albums", "Alben", Icons.Default.Album)

    object Player : Screen("player", "Jetzt läuft", Icons.Default.PlayCircle)
}

private val tabs =
    listOf(
        Screen.Library,
        Screen.Playlists,
        Screen.Artists,
        Screen.Albums,
        Screen.Player,
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
                        },
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
                    },
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistScreen(
                    contentPadding = innerPadding,
                    onPlaylistClick = { playlistId ->
                        navController.navigate("playlists/$playlistId")
                    },
                    onPlaySongs = { songs ->
                        playerViewModel.playSongs(songs, 0)
                        navController.navigate(Screen.Player.route)
                    },
                )
            }
            composable(
                route = "playlists/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                )
            }
            composable(Screen.Artists.route) {
                ArtistScreen(
                    contentPadding = innerPadding,
                    onItemClick = { artistName ->
                        navController.navigate("artists/${Uri.encode(artistName)}")
                    },
                )
            }
            composable(
                route = "artists/{artistName}",
                arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
            ) {
                ArtistDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                )
            }
            composable(Screen.Albums.route) {
                AlbumScreen(
                    contentPadding = innerPadding,
                    onItemClick = { albumName ->
                        navController.navigate("albums/${Uri.encode(albumName)}")
                    },
                )
            }
            composable(
                route = "albums/{albumName}",
                arguments = listOf(navArgument("albumName") { type = NavType.StringType }),
            ) {
                AlbumDetailScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index)
                        navController.navigate(Screen.Player.route)
                    },
                )
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
