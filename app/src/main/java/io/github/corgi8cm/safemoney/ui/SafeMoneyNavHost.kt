package io.github.corgi8cm.safemoney.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.ui.common.AppMessenger
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.records.RecordsScreen
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.corgi8cm.safemoney.ui.goal.GoalDetailScreen
import io.github.corgi8cm.safemoney.ui.goal.GoalEditScreen
import io.github.corgi8cm.safemoney.ui.home.HomeScreen
import io.github.corgi8cm.safemoney.ui.saving.SavingEditScreen
import io.github.corgi8cm.safemoney.ui.settings.QuickReasonsScreen
import io.github.corgi8cm.safemoney.ui.settings.SettingsScreen
import io.github.corgi8cm.safemoney.ui.stats.StatsScreen

object Routes {
    const val ARG_GOAL_ID = "goalId"
    const val ARG_SAVING_ID = "savingId"

    const val HOME = "home"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val QUICK_REASONS = "quickReasons"
    const val RECORDS = "records"
    const val GOAL_DETAIL = "goalDetail/{$ARG_GOAL_ID}"
    const val GOAL_EDIT = "goalEdit?$ARG_GOAL_ID={$ARG_GOAL_ID}"
    const val SAVING_EDIT = "savingEdit?$ARG_SAVING_ID={$ARG_SAVING_ID}&$ARG_GOAL_ID={$ARG_GOAL_ID}"

    fun goalDetail(goalId: Long) = "goalDetail/$goalId"
    fun goalEdit(goalId: Long? = null) = if (goalId == null) "goalEdit" else "goalEdit?$ARG_GOAL_ID=$goalId"
    fun savingNew(goalId: Long? = null) = if (goalId == null) "savingEdit" else "savingEdit?$ARG_GOAL_ID=$goalId"
    fun savingEdit(savingId: Long) = "savingEdit?$ARG_SAVING_ID=$savingId"
}

private fun optionalLongArg(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = -1L
}

@Composable
fun SafeMoneyNavHost(
    pendingAction: String? = null,
    onPendingActionHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val back: () -> Unit = { navController.popBackStack() }

    val app = LocalContext.current.applicationContext as SafeMoneyApp
    val uiScope = rememberCoroutineScope()
    val messenger = remember { AppMessenger(SnackbarHostState(), uiScope, app.appScope) }

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            QuickActions.ACTION_NEW_SAVING -> navController.navigate(Routes.savingNew()) { launchSingleTop = true }
            else -> return@LaunchedEffect
        }
        onPendingActionHandled()
    }

    CompositionLocalProvider(LocalAppMessenger provides messenger) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddGoal = { navController.navigate(Routes.goalEdit()) },
                    onOpenGoal = { navController.navigate(Routes.goalDetail(it)) },
                    onAddSaving = { navController.navigate(Routes.savingNew()) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenRecords = { navController.navigate(Routes.RECORDS) },
                )
            }
            composable(Routes.RECORDS) {
                RecordsScreen(onBack = back, onOpenSaving = { navController.navigate(Routes.savingEdit(it)) })
            }
            composable(Routes.STATS) {
                StatsScreen(onBack = back)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = back, onManageReasons = { navController.navigate(Routes.QUICK_REASONS) })
            }
            composable(Routes.QUICK_REASONS) {
                QuickReasonsScreen(onBack = back)
            }
            composable(
                Routes.GOAL_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_GOAL_ID) { type = NavType.LongType }),
            ) {
                GoalDetailScreen(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.goalEdit(it)) },
                    onAddSaving = { navController.navigate(Routes.savingNew(it)) },
                    onEditSaving = { navController.navigate(Routes.savingEdit(it)) },
                )
            }
            composable(Routes.GOAL_EDIT, arguments = listOf(optionalLongArg(Routes.ARG_GOAL_ID))) {
                GoalEditScreen(onBack = back)
            }
            composable(
                Routes.SAVING_EDIT,
                arguments = listOf(optionalLongArg(Routes.ARG_SAVING_ID), optionalLongArg(Routes.ARG_GOAL_ID)),
            ) {
                SavingEditScreen(onBack = back, onManageReasons = { navController.navigate(Routes.QUICK_REASONS) })
            }
        }
    }
}
