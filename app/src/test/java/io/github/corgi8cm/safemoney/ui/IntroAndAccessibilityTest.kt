package io.github.corgi8cm.safemoney.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.ui.common.AppMessenger
import io.github.corgi8cm.safemoney.ui.common.GoalAvatar
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.home.HomeScreen
import io.github.corgi8cm.safemoney.ui.intro.INTRO_PAGES
import io.github.corgi8cm.safemoney.ui.intro.IntroPager
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h720dp")
class IntroAndAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nextGoesThroughAllPagesThenFinishes() {
        var finished = 0
        compose.setContent { SafeMoneyTheme { IntroPager(onFinish = { finished++ }) } }

        INTRO_PAGES.dropLast(1).forEachIndexed { i, page ->
            compose.onNodeWithText(page.title).assertIsDisplayed()
            compose.onNodeWithContentDescription("第 ${i + 1} 頁，共 ${INTRO_PAGES.size} 頁").assertExists()
            compose.onNodeWithText("下一步").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText(INTRO_PAGES.last().title).assertIsDisplayed()
        compose.onNodeWithText("略過").assertDoesNotExist()
        assertEquals(0, finished)
        compose.onNodeWithText("開始使用").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun skipFinishesRightAway() {
        var finished = 0
        compose.setContent { SafeMoneyTheme { IntroPager(onFinish = { finished++ }) } }
        compose.onNodeWithText("略過").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun largestFontStillShowsButtons() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SafeMoneyTheme { IntroPager(onFinish = {}) }
            }
        }
        compose.onNodeWithText("下一步").assertIsDisplayed()
        compose.onNodeWithText("略過").assertIsDisplayed()
        compose.onNodeWithText(INTRO_PAGES.first().title).assertIsDisplayed()
    }

    @Test
    fun goalAvatarIsSkippedByScreenReaders() {
        compose.setContent { SafeMoneyTheme { GoalAvatar(name = "外套", emoji = null, color = null, size = 40.dp) } }
        // 讀螢幕軟體看到的是合併後的語意樹：圓圈裡的字不應該被唸出來
        compose.onNodeWithText("外").assertDoesNotExist()
    }

    @Test
    fun firstLaunchShowsIntroThenEmptyHome() {
        compose.setContent {
            val app = LocalContext.current.applicationContext as SafeMoneyApp
            val scope = rememberCoroutineScope()
            val messenger = remember { AppMessenger(SnackbarHostState(), scope, app.appScope) }
            CompositionLocalProvider(LocalAppMessenger provides messenger) {
                SafeMoneyTheme {
                    HomeScreen(
                        onAddGoal = {}, onOpenGoal = {}, onAddSaving = {},
                        onOpenStats = {}, onOpenSettings = {}, onOpenRecords = {},
                    )
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(INTRO_PAGES.first().title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("略過").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("還沒有回本目標")).fetchSemanticsNodes().isNotEmpty() }
    }
}
