package io.github.kiraherk.smarttodo.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.kiraherk.smarttodo.MainActivity
import io.github.kiraherk.smarttodo.NavRequest
import io.github.kiraherk.smarttodo.R
import io.github.kiraherk.smarttodo.ui.screens.AssistantScreen
import io.github.kiraherk.smarttodo.ui.screens.HistoryScreen
import io.github.kiraherk.smarttodo.ui.screens.InboxScreen
import io.github.kiraherk.smarttodo.ui.screens.LocalVoice
import io.github.kiraherk.smarttodo.ui.screens.ReviewScreen
import io.github.kiraherk.smarttodo.ui.screens.Routes
import io.github.kiraherk.smarttodo.ui.screens.SettingsScreen
import io.github.kiraherk.smarttodo.ui.screens.SetupScreen
import io.github.kiraherk.smarttodo.ui.screens.TaskDetailScreen
import io.github.kiraherk.smarttodo.ui.screens.TodayScreen
import io.github.kiraherk.smarttodo.ui.screens.VoiceActions
import io.github.kiraherk.smarttodo.ui.screens.appContainer
import io.github.kiraherk.smarttodo.ui.theme.SmartTodoTheme
import io.github.kiraherk.smarttodo.ui.voice.VoiceOverlay
import io.github.kiraherk.smarttodo.ui.voice.VoiceViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppRoot(navRequests: StateFlow<NavRequest?>, onNavHandled: () -> Unit) {
    val c = appContainer()
    val ctx = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)

    SmartTodoTheme(textScale = settings?.textScale ?: 1.15f) {
        val voiceVm: VoiceViewModel = viewModel { VoiceViewModel(c) }
        val voiceState by voiceVm.state.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        var pendingVoice by remember { mutableStateOf<VoiceViewModel.Request?>(null) }
        var speakSummary by remember { mutableStateOf(false) }
        var openUpdate by remember { mutableStateOf(false) }
        val micDenied = stringResource(R.string.mic_denied)

        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingVoice
            pendingVoice = null
            if (granted && request != null) voiceVm.start(request) else if (!granted) Toast.makeText(ctx, micDenied, Toast.LENGTH_LONG).show()
        }
        val speechPopup = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            voiceVm.onPopupResult(text)
        }
        val popupPrompt = stringResource(R.string.listening_hint)
        LaunchedEffect(voiceVm) {
            voiceVm.popupRequests.collect { language ->
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, popupPrompt)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                try {
                    speechPopup.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    voiceVm.onPopupUnavailable()
                }
            }
        }
        val voiceActions = remember(voiceVm) {
            VoiceActions(
                start = { request ->
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        voiceVm.start(request)
                    } else {
                        pendingVoice = request
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                processText = { text, request -> voiceVm.processText(text, request) },
                ask = { voiceVm.ask(it) },
                searchText = voiceVm.searchText,
            )
        }

        // Notifications, the home-screen shortcut and the widget ask to open a screen.
        val navRequest by navRequests.collectAsStateWithLifecycle()
        LaunchedEffect(navRequest) {
            val r = navRequest ?: return@LaunchedEffect
            runCatching {
                when {
                    r.taskId != null -> nav.navigate(Routes.task(r.taskId))
                    r.screen == MainActivity.SCREEN_REVIEW -> nav.navigate(Routes.review(false))
                    r.screen == MainActivity.SCREEN_WEEKLY -> nav.navigate(Routes.review(true))
                    r.screen == MainActivity.SCREEN_INBOX -> nav.navigate(Routes.INBOX)
                    r.screen == MainActivity.SCREEN_UPDATE -> { openUpdate = true; navigateTop(nav, Routes.SETTINGS) }
                    r.screen == MainActivity.SCREEN_TODAY_SPEAK -> { navigateTop(nav, Routes.TODAY); speakSummary = true }
                    r.screen == MainActivity.SCREEN_VOICE -> { navigateTop(nav, Routes.TODAY); voiceActions.start(VoiceViewModel.Request()) }
                }
            }
            onNavHandled()
        }

        // First launch: show the setup checklist.
        LaunchedEffect(settings?.setupDone) {
            if (settings?.setupDone == false && nav.currentDestination?.route != Routes.SETUP) {
                runCatching { nav.navigate(Routes.SETUP) }
            }
        }

        CompositionLocalProvider(LocalVoice provides voiceActions) {
            val backStack by nav.currentBackStackEntryAsState()
            val route = backStack?.destination?.route
            val topLevel = route in listOf(Routes.TODAY, Routes.HISTORY, Routes.ASSISTANT, Routes.SETTINGS)

            Scaffold(
                bottomBar = {
                    if (topLevel) {
                        NavigationBar {
                            listOf(
                                Triple(Routes.TODAY, Icons.Filled.Today, R.string.nav_today),
                                Triple(Routes.HISTORY, Icons.Filled.History, R.string.nav_history),
                                Triple(Routes.ASSISTANT, Icons.AutoMirrored.Filled.Chat, R.string.nav_assistant),
                                Triple(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
                            ).forEach { (dest, icon, label) ->
                                NavigationBarItem(
                                    selected = route == dest,
                                    onClick = { navigateTop(nav, dest) },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }
                },
            ) { inner ->
                NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(inner).consumeWindowInsets(inner)) {
                    composable(Routes.TODAY) {
                        TodayScreen(
                            onOpenTask = { nav.navigate(Routes.task(it)) },
                            onNavigate = { if (it == Routes.SETTINGS) navigateTop(nav, it) else nav.navigate(it) },
                            speakSummaryNow = speakSummary,
                            onSummarySpoken = { speakSummary = false },
                        )
                    }
                    composable(Routes.HISTORY) { HistoryScreen(onOpenTask = { nav.navigate(Routes.task(it)) }) }
                    composable(Routes.ASSISTANT) { AssistantScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            openUpdate = openUpdate,
                            onUpdateOpened = { openUpdate = false },
                            onOpenSetup = { nav.navigate(Routes.SETUP) },
                            onOpenInbox = { nav.navigate(Routes.INBOX) },
                        )
                    }
                    composable(Routes.TASK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        TaskDetailScreen(taskId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.REVIEW, arguments = listOf(navArgument("weekly") { type = NavType.BoolType })) { entry ->
                        ReviewScreen(
                            weekly = entry.arguments?.getBoolean("weekly") ?: false,
                            onBack = { nav.popBackStack() },
                            onOpenTask = { nav.navigate(Routes.task(it)) },
                        )
                    }
                    composable(Routes.INBOX) { InboxScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.SETUP) {
                        SetupScreen(
                            onDone = { navigateTop(nav, Routes.TODAY) },
                            // Opens on top of setup, so "back" returns to the checklist.
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                }
            }

            VoiceOverlay(
                state = voiceState,
                onFinish = { voiceVm.finishListening() },
                onConfirm = { voiceVm.confirm() },
                onReject = { voiceVm.reject() },
                onRetry = { voiceVm.retry() },
                onSpeakAgain = { voiceVm.speakAgain() },
                onDismiss = { voiceVm.dismiss() },
            )
        }
    }
}

/**
 * Goes to a main tab and shows it from its start, never a screen that was open on top of it before.
 * (Android's usual save/restore tab pattern brings back the old screen when going to the first tab,
 * which kept him on the setup screen after "Finish". Simple and predictable is better here.)
 */
private fun navigateTop(nav: NavHostController, route: String) {
    if (route == Routes.TODAY) {
        if (!nav.popBackStack(Routes.TODAY, inclusive = false)) nav.navigate(Routes.TODAY) { launchSingleTop = true }
        return
    }
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id)
        launchSingleTop = true
    }
}
