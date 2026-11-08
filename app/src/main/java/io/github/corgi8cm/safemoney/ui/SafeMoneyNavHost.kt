package io.github.corgi8cm.safemoney.ui

import androidx.compose.runtime.Composable
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

object Routes {
    const val ARG_GOAL_ID = "goalId"
    const val ARG_SAVING_ID = "savingId"

    const val HOME = "home"
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
fun SafeMoneyNavHost(navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddGoal = { navController.navigate(Routes.goalEdit()) },
                onOpenGoal = { navController.navigate(Routes.goalDetail(it)) },
                onAddSaving = { navController.navigate(Routes.savingNew()) },
            )
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
            SavingEditScreen(onBack = back)
        }
    }
}
