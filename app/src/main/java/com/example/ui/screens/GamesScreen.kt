package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.GameCategory
import com.example.data.model.GameSortOption
import com.example.data.model.GamingProfile
import com.example.data.model.InstalledAppItem
import com.example.data.model.LanguageMode
import com.example.data.model.SystemGameUsageSummary
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@Composable
fun GamesScreen(
  games: List<GameEntity>,
  sessions: List<GameSessionEntity>,
  installedApps: List<InstalledAppItem>,
  isScanning: Boolean,
  isGridView: Boolean,
  viewModel: DrxMainViewModel,
  systemUsageMap: Map<String, SystemGameUsageSummary> = emptyMap(),
  notificationCountsByPackage: Map<String, Int> = emptyMap(),
  onOpenGameStats: (String) -> Unit = {},
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current
  val isIndo = strings.navSettings == "Setelan"

  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(GameCategory.ALL) }
  var favoritesOnly by remember { mutableStateOf(false) }
  var sortOption by remember { mutableStateOf(GameSortOption.PLAYTIME_DESC) }
  var showAddModal by remember { mutableStateOf(false) }
  var appSearchQuery by remember { mutableStateOf("") }
  var selectedGameForDetails by remember { mutableStateOf<GameEntity?>(null) }

  // Keep selected detail game synced with DB updates
  val activeDetailGame =
    selectedGameForDetails?.let { cur ->
      games.find { it.packageName == cur.packageName }
    }

  val playtimeByPkg =
    remember(games, sessions, systemUsageMap) {
      val sessMap = sessions.groupBy { it.gamePackageName }
      games.associate { g ->
        val drxTotal = maxOf(g.totalPlayTimeMs, sessMap[g.packageName].orEmpty().sumOf { it.durationMs })
        val sysTotal = systemUsageMap[g.packageName]?.last30DaysMs ?: 0L
        g.packageName to maxOf(drxTotal, sysTotal)
      }
    }

  val lastPlayedByPkg =
    remember(games, sessions, systemUsageMap) {
      val sessMap = sessions.groupBy { it.gamePackageName }
      games.associate { g ->
        val drxLast = maxOf(g.lastPlayedAt, sessMap[g.packageName].orEmpty().maxOfOrNull { it.endedAt } ?: 0L)
        val sysLast = systemUsageMap[g.packageName]?.lastTimeUsedMs ?: 0L
        g.packageName to maxOf(drxLast, sysLast)
      }
    }

  val recentlyPlayedGames =
    remember(games, sessions, lastPlayedByPkg) {
      games
        .filter { (lastPlayedByPkg[it.packageName] ?: 0L) > 0L }
        .sortedByDescending { lastPlayedByPkg[it.packageName] ?: 0L }
        .take(6)
    }

  val filteredGames =
    remember(games, searchQuery, selectedCategory, favoritesOnly, sortOption, playtimeByPkg, lastPlayedByPkg) {
      val filtered =
        games.filter { g ->
          val matchesQuery =
            searchQuery.isBlank() ||
              g.appName.contains(searchQuery, ignoreCase = true) ||
              g.packageName.contains(searchQuery, ignoreCase = true)
          val matchesCat =
            selectedCategory == GameCategory.ALL ||
              g.category.equals(selectedCategory.id, ignoreCase = true)
          val matchesFav = !favoritesOnly || g.isFavorite
          matchesQuery && matchesCat && matchesFav
        }
      when (sortOption) {
        GameSortOption.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
        GameSortOption.PLAYTIME_DESC ->
          filtered.sortedWith(
            compareByDescending<GameEntity> { playtimeByPkg[it.packageName] ?: 0L }
              .thenBy { it.appName.lowercase() }
          )
        GameSortOption.LAST_PLAYED_DESC ->
          filtered.sortedWith(
            compareByDescending<GameEntity> { lastPlayedByPkg[it.packageName] ?: 0L }
              .thenBy { it.appName.lowercase() }
          )
      }
    }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    DrxScreenHeader(
      title = strings.gameLibraryTitle,
      subtitle = "${games.size} Titles • Launcher",
      trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { viewModel.setGamesGridView(!isGridView) },
            modifier = Modifier.size(44.dp).testTag("toggle_games_layout_button"),
          ) {
            Icon(
              imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
              contentDescription = "Toggle Grid/List",
              tint = drxColors.silver,
            )
          }
          IconButton(
            onClick = { viewModel.scanInstalledGames(autoSeed = true) },
            modifier = Modifier.size(44.dp).testTag("scan_games_button"),
          ) {
            if (isScanning) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = drxColors.neonCyan)
            } else {
              Icon(Icons.Default.Refresh, contentDescription = strings.scanGamesBtn, tint = drxColors.neonCyan)
            }
          }
          Spacer(Modifier.width(4.dp))
          DrxPrimaryButton(
            label = strings.addGameManualBtn,
            icon = Icons.Default.Add,
            onClick = {
              viewModel.scanInstalledGames(autoSeed = false)
              showAddModal = true
            },
            modifier = Modifier.height(42.dp).testTag("add_game_manual_button"),
          )
        }
      },
    )

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text(strings.searchGamesPlaceholder) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = drxColors.electricBlue) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedBorderColor = drxColors.electricBlue,
          unfocusedBorderColor = drxColors.glassBorder,
          focusedContainerColor = drxColors.cardSurface,
          unfocusedContainerColor = drxColors.cardSurface,
        ),
      modifier = Modifier.fillMaxWidth().testTag("search_games_input"),
    )

    Spacer(Modifier.height(10.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        DrxFilterPill(
          label = strings.favoritesOnly,
          selected = favoritesOnly,
          onClick = { favoritesOnly = !favoritesOnly },
        )
      }
      items(GameCategory.entries) { cat ->
        DrxFilterPill(
          label = cat.name.replace('_', ' '),
          selected = selectedCategory == cat,
          onClick = { selectedCategory = cat },
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val sorts =
        listOf(
          GameSortOption.PLAYTIME_DESC to strings.sortByPlaytime,
          GameSortOption.LAST_PLAYED_DESC to strings.sortByLastPlayed,
          GameSortOption.NAME_ASC to strings.sortByName,
        )
      items(sorts) { (opt, label) ->
        DrxFilterPill(
          label = label,
          selected = sortOption == opt,
          onClick = { sortOption = opt },
          modifier = Modifier.testTag("sort_pill_${opt.id}"),
        )
      }
    }

    Spacer(Modifier.height(12.dp))

    if (recentlyPlayedGames.isNotEmpty() && searchQuery.isBlank()) {
      DrxSectionHeader(title = strings.recentlyPlayedTitle)
      Spacer(Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(recentlyPlayedGames, key = { "rec_${it.packageName}" }) { recent ->
          val iconBmp = remember(recent.packageName) { viewModel.loadAppIcon(recent.packageName) }
          DrxGlassCard(
            onClick = { selectedGameForDetails = recent },
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.width(138.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (iconBmp != null) {
                Image(
                  bitmap = iconBmp,
                  contentDescription = recent.appName,
                  modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                )
              } else {
                Icon(Icons.Default.SportsEsports, contentDescription = recent.appName, tint = drxColors.neonCyan, modifier = Modifier.size(32.dp))
              }
              Spacer(Modifier.width(8.dp))
              Text(
                text = recent.appName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }

    if (filteredGames.isEmpty()) {
      DrxGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            Icons.Default.SportsEsports,
            contentDescription = null,
            tint = drxColors.electricBlue,
            modifier = Modifier.size(42.dp),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = strings.noGamesYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      AnimatedContent(targetState = isGridView, label = "games_layout_switch") { grid ->
        if (grid) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(filteredGames, key = { it.packageName }) { game ->
              GameGridCard(
                game = game,
                playtimeMs = playtimeByPkg[game.packageName] ?: 0L,
                lastPlayedMs = lastPlayedByPkg[game.packageName] ?: 0L,
                notifCount = notificationCountsByPackage[game.packageName] ?: 0,
                isIndo = isIndo,
                viewModel = viewModel,
                onOpenDetails = { selectedGameForDetails = game },
                onOpenStats = { onOpenGameStats(game.packageName) },
                onPlay = { viewModel.startGameLaunch(game, activity) },
              )
            }
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(filteredGames, key = { it.packageName }) { game ->
              GameListCard(
                game = game,
                playtimeMs = playtimeByPkg[game.packageName] ?: 0L,
                lastPlayedMs = lastPlayedByPkg[game.packageName] ?: 0L,
                notifCount = notificationCountsByPackage[game.packageName] ?: 0,
                isIndo = isIndo,
                viewModel = viewModel,
                onOpenDetails = { selectedGameForDetails = game },
                onOpenStats = { onOpenGameStats(game.packageName) },
                onPlay = { viewModel.startGameLaunch(game, activity) },
              )
            }
          }
        }
      }
    }
  }

  // Animated Game Details Modal
  if (activeDetailGame != null) {
    val game = activeDetailGame
    val iconBmp = remember(game.packageName) { viewModel.loadAppIcon(game.packageName) }
    val currentProf =
      GamingProfile.entries.find { it.id == game.profileName } ?: GamingProfile.BALANCED
    val currentCat =
      GameCategory.entries.find { it.id.equals(game.category, ignoreCase = true) } ?: GameCategory.CUSTOM
    val totalMins = (game.totalPlayTimeMs / 60_000L).coerceAtLeast(0L)

    DrxCustomModal(
      title = strings.gameDetailsTitle,
      onDismiss = { selectedGameForDetails = null },
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (iconBmp != null) {
          Image(
            bitmap = iconBmp,
            contentDescription = game.appName,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
          )
        } else {
          Icon(
            Icons.Default.SportsEsports,
            contentDescription = game.appName,
            tint = drxColors.electricBlue,
            modifier = Modifier.size(54.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            game.appName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            game.packageName,
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(4.dp))
          val detailNotifs = notificationCountsByPackage[game.packageName] ?: 0
          DrxStatusPill(
            text = "${game.playCount} Sessions • ${totalMins} min" + if (detailNotifs > 0) " • $detailNotifs Notifs" else "",
            color = drxColors.neonCyan,
          )
        }
      }

      Spacer(Modifier.height(14.dp))
      Text(strings.selectProfile, style = MaterialTheme.typography.titleSmall, color = drxColors.silver)
      Spacer(Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GamingProfile.entries) { prof ->
          DrxFilterPill(
            label = strings.profileName(prof),
            selected = currentProf == prof,
            onClick = { viewModel.updateGameProfile(game.packageName, prof) },
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      Text(strings.selectCategory, style = MaterialTheme.typography.titleSmall, color = drxColors.silver)
      Spacer(Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameCategory.entries.filter { it != GameCategory.ALL }) { cat ->
          DrxFilterPill(
            label = cat.name.replace('_', ' '),
            selected = currentCat == cat,
            onClick = { viewModel.updateGameCategory(game.packageName, cat) },
          )
        }
      }

      Spacer(Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DrxSecondaryButton(
          label = if (game.isFavorite) "★ Fav" else "Fav",
          icon = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          onClick = { viewModel.toggleFavorite(game.packageName) },
          modifier = Modifier.weight(1f),
        )
        DrxSecondaryButton(
          label = strings.removeGame,
          icon = Icons.Default.DeleteOutline,
          onClick = {
            viewModel.removeGame(game.packageName)
            selectedGameForDetails = null
          },
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(10.dp))
      DrxSecondaryButton(
        label = strings.viewGameStatsBtn,
        icon = Icons.Default.BarChart,
        onClick = {
          selectedGameForDetails = null
          onOpenGameStats(game.packageName)
        },
        modifier = Modifier.fillMaxWidth().testTag("view_game_stats_${game.packageName}"),
      )

      Spacer(Modifier.height(10.dp))
      DrxPrimaryButton(
        label = strings.boostAndPlayBtn,
        icon = Icons.Default.PlayArrow,
        onClick = {
          selectedGameForDetails = null
          viewModel.startGameLaunch(game, activity)
        },
        modifier = Modifier.fillMaxWidth().testTag("details_boost_play_${game.packageName}"),
      )
    }
  }

  // Add Installed App Modal
  if (showAddModal) {
    val existingPackages = remember(games) { games.map { it.packageName }.toSet() }
    val availableApps =
      remember(installedApps, existingPackages, appSearchQuery) {
        installedApps.filter { app ->
          !existingPackages.contains(app.packageName) &&
            (appSearchQuery.isBlank() ||
              app.appName.contains(appSearchQuery, ignoreCase = true) ||
              app.packageName.contains(appSearchQuery, ignoreCase = true))
        }
      }

    DrxCustomModal(
      title = strings.addAppsSheetTitle,
      onDismiss = { showAddModal = false },
    ) {
      OutlinedTextField(
        value = appSearchQuery,
        onValueChange = { appSearchQuery = it },
        placeholder = { Text(strings.searchGamesPlaceholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(10.dp))
      LazyColumn(
        modifier = Modifier.height(340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(availableApps, key = { it.packageName }) { app ->
          val iconBmp = remember(app.packageName) { viewModel.loadAppIcon(app.packageName) }
          DrxGlassCard(
            onClick = {
              viewModel.addGameManually(app, app.suggestedCategory)
              showAddModal = false
            },
            contentPadding = PaddingValues(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (iconBmp != null) {
                Image(
                  bitmap = iconBmp,
                  contentDescription = app.appName,
                  modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                )
              } else {
                Icon(
                  Icons.Default.SportsEsports,
                  contentDescription = app.appName,
                  tint = drxColors.electricBlue,
                  modifier = Modifier.size(38.dp),
                )
              }
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  app.packageName,
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Icon(Icons.Default.Add, contentDescription = strings.addGameManualBtn, tint = drxColors.neonCyan)
            }
          }
        }
      }
    }
  }
}

@Composable
fun DrxFilterPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = if (selected) drxColors.electricBlue.copy(alpha = 0.22f) else drxColors.secondaryBg,
    border = BorderStroke(1.dp, if (selected) drxColors.neonCyan else drxColors.glassBorder),
    modifier = modifier.height(36.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) drxColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun GameListCard(
  game: GameEntity,
  playtimeMs: Long,
  lastPlayedMs: Long,
  notifCount: Int,
  isIndo: Boolean,
  viewModel: DrxMainViewModel,
  onOpenDetails: () -> Unit,
  onOpenStats: () -> Unit,
  onPlay: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val iconBmp = remember(game.packageName) { viewModel.loadAppIcon(game.packageName) }

  DrxGlassCard(
    accentColor = drxColors.electricBlue,
    highlighted = game.isFavorite,
    onClick = onOpenDetails,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (iconBmp != null) {
        Image(
          bitmap = iconBmp,
          contentDescription = game.appName,
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Default.SportsEsports,
          contentDescription = game.appName,
          tint = drxColors.electricBlue,
          modifier = Modifier.size(48.dp),
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = game.appName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val prof = GamingProfile.entries.find { it.id == game.profileName } ?: GamingProfile.BALANCED
        val playtimeLabel =
          if (playtimeMs > 0L) TimeAndSizeFormatters.formatDurationReadable(playtimeMs, isIndo)
          else strings.lastPlayedNever
        Text(
          text = "${strings.profileName(prof)} • $playtimeLabel",
          style = MaterialTheme.typography.labelMedium,
          color = drxColors.neonCyan,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val lastPlayedText =
          if (lastPlayedMs > 0L) TimeAndSizeFormatters.formatDate(lastPlayedMs, isIndo)
          else strings.lastPlayedNever
        val notifSuffix = if (notifCount > 0) " • $notifCount Notifs" else ""
        Text(
          text = "${game.category.uppercase()} • $lastPlayedText$notifSuffix",
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onOpenStats,
        modifier = Modifier.size(48.dp).testTag("card_stats_list_${game.packageName}"),
      ) {
        Icon(Icons.Default.BarChart, contentDescription = strings.viewGameStatsBtn, tint = drxColors.neonCyan)
      }
      IconButton(onClick = onOpenDetails, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.Tune, contentDescription = strings.gameDetailsTitle, tint = drxColors.silver)
      }
      IconButton(onClick = { viewModel.toggleFavorite(game.packageName) }, modifier = Modifier.size(48.dp)) {
        Icon(
          imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          contentDescription = strings.favoritesOnly,
          tint = if (game.isFavorite) drxColors.alertCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.height(10.dp))
    DrxPrimaryButton(
      label = strings.playBtn,
      icon = Icons.Default.PlayArrow,
      onClick = onPlay,
      modifier = Modifier.fillMaxWidth().height(42.dp).testTag("play_game_${game.packageName}"),
    )
  }
}

@Composable
private fun GameGridCard(
  game: GameEntity,
  playtimeMs: Long,
  lastPlayedMs: Long,
  notifCount: Int,
  isIndo: Boolean,
  viewModel: DrxMainViewModel,
  onOpenDetails: () -> Unit,
  onOpenStats: () -> Unit,
  onPlay: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val iconBmp = remember(game.packageName) { viewModel.loadAppIcon(game.packageName) }

  DrxGlassCard(
    accentColor = drxColors.electricBlue,
    highlighted = game.isFavorite,
    onClick = onOpenDetails,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      if (iconBmp != null) {
        Image(
          bitmap = iconBmp,
          contentDescription = game.appName,
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Default.SportsEsports,
          contentDescription = game.appName,
          tint = drxColors.electricBlue,
          modifier = Modifier.size(48.dp),
        )
      }
      IconButton(onClick = { viewModel.toggleFavorite(game.packageName) }, modifier = Modifier.size(42.dp)) {
        Icon(
          imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          contentDescription = strings.favoritesOnly,
          tint = if (game.isFavorite) drxColors.alertCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(
      text = game.appName,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    val prof = GamingProfile.entries.find { it.id == game.profileName } ?: GamingProfile.BALANCED
    val playText =
      if (playtimeMs > 0L) " • ${TimeAndSizeFormatters.formatDurationReadable(playtimeMs, isIndo)}"
      else ""
    Text(
      text = "${strings.profileName(prof)}$playText",
      style = MaterialTheme.typography.labelMedium,
      color = drxColors.neonCyan,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 2.dp),
    )
    val lastText =
      if (lastPlayedMs > 0L) TimeAndSizeFormatters.formatDate(lastPlayedMs, isIndo)
      else strings.lastPlayedNever
    val notifGrid = if (notifCount > 0) " • $notifCount Notifs" else ""
    Text(
      text = "$lastText$notifGrid",
      style = DrxTypographyTokens.caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DrxPrimaryButton(
        label = strings.playBtn,
        icon = Icons.Default.PlayArrow,
        onClick = onPlay,
        modifier = Modifier.weight(1f).height(38.dp).testTag("play_grid_${game.packageName}"),
      )
      IconButton(
        onClick = onOpenStats,
        modifier = Modifier.size(38.dp).testTag("card_stats_grid_${game.packageName}"),
      ) {
        Icon(Icons.Default.BarChart, contentDescription = strings.viewGameStatsBtn, tint = drxColors.neonCyan, modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = onOpenDetails, modifier = Modifier.size(38.dp)) {
        Icon(Icons.Default.Tune, contentDescription = strings.gameDetailsTitle, tint = drxColors.silver, modifier = Modifier.size(18.dp))
      }
    }
  }
}
