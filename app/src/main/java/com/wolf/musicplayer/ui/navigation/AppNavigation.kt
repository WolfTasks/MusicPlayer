package com.wolf.musicplayer.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wolf.musicplayer.domain.model.QueueContext
import com.wolf.musicplayer.domain.model.Song
import com.wolf.musicplayer.ui.component.AddToPlaylistSheet
import com.wolf.musicplayer.ui.screen.album.AlbumDetailScreen
import com.wolf.musicplayer.ui.screen.album.AlbumScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistDetailScreen
import com.wolf.musicplayer.ui.screen.artist.ArtistScreen
import com.wolf.musicplayer.ui.screen.library.LibraryScreen
import com.wolf.musicplayer.ui.screen.player.PlayerScreen
import com.wolf.musicplayer.ui.screen.player.PlayerViewModel
import com.wolf.musicplayer.ui.screen.playlist.PlaylistDetailScreen
import com.wolf.musicplayer.ui.screen.playlist.PlaylistScreen

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Library : Screen("library", "Bibliothek", Icons.Default.LibraryMusic)

    object Playlists : Screen("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic)

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
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val addToPlaylistSong by playerViewModel.addToPlaylistSong.collectAsStateWithLifecycle()
    val addToPlaylistSongPlaylists by playerViewModel.addToPlaylistSongPlaylists.collectAsStateWithLifecycle()
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
        val onMoreClick: (Song) -> Unit = { song ->
            playerViewModel.setAddToPlaylistSong(song)
        }
        NavHost(navController = navController, startDestination = Screen.Library.route) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    onSongClick = { songs, index ->
                        playerViewModel.playSongs(songs, index, QueueContext.AllSongs)
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistScreen(
                    contentPadding = innerPadding,
                    onPlaylistClick = { playlistId ->
                        navController.navigate("playlists/$playlistId")
                    },
                    onPlaySongs = { songs, playlistId ->
                        playerViewModel.playSongs(songs, 0, QueueContext.PlaylistCtx(playlistId))
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
                        val playlistId = it.arguments?.getLong("playlistId") ?: 0L
                        playerViewModel.playSongs(songs, index, QueueContext.PlaylistCtx(playlistId))
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
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
                        val artistName = it.arguments?.getString("artistName") ?: ""
                        playerViewModel.playSongs(songs, index, QueueContext.ArtistCtx(artistName))
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
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
                        val albumName = it.arguments?.getString("albumName") ?: ""
                        playerViewModel.playSongs(songs, index, QueueContext.AlbumCtx(albumName))
                        navController.navigate(Screen.Player.route)
                    },
                    onMoreClick = onMoreClick,
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    contentPadding = innerPadding,
                )
            }
        }
        addToPlaylistSong?.let { song ->
            AddToPlaylistSheet(
                song = song,
                playlists = playlists,
                songPlaylistIds = addToPlaylistSongPlaylists,
                onAddToPlaylist = { playlist -> playerViewModel.addSongToPlaylist(song, playlist) },
                onCreateAndAdd = { name -> playerViewModel.createPlaylistAndAddSong(name, song) },
                onDismiss = { playerViewModel.setAddToPlaylistSong(null) },
            )
        }
    }
}
