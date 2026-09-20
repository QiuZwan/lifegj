package com.lifebutler.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.ReminderScheduler
import com.lifebutler.app.ui.components.LbBottomBar
import com.lifebutler.app.ui.screens.ChatScreen
import com.lifebutler.app.ui.screens.ExpenseScreen
import com.lifebutler.app.ui.screens.FamilyScreen
import com.lifebutler.app.ui.screens.GuardScreen
import com.lifebutler.app.ui.screens.MemoScreen
import com.lifebutler.app.ui.screens.MineScreen
import com.lifebutler.app.ui.screens.MonthReportScreen
import com.lifebutler.app.ui.screens.ObligationsScreen
import com.lifebutler.app.ui.screens.ScanScreen
import com.lifebutler.app.ui.screens.StatesScreen
import com.lifebutler.app.ui.screens.SubscriptionDetailScreen
import com.lifebutler.app.ui.screens.TodayScreen
import com.lifebutler.app.ui.screens.VaultScreen
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LifeButlerTheme

class MainActivity : ComponentActivity() {
    private val tabRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ButlerStore.get(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
        )
        ReminderScheduler.ensureScheduled(applicationContext)
        val initialTab = intent?.getStringExtra("open_tab")
        setContent {
            LifeButlerTheme(dark = store.darkMode.value) {
                LbApp(tabRequest = tabRequest, initialTab = initialTab)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open_tab")?.let { tabRequest.value = it }
    }
}

@Composable
fun LbApp(tabRequest: MutableState<String?> = mutableStateOf(null), initialTab: String? = null) {
    var tab by rememberSaveable { mutableStateOf(initialTab ?: "今日") }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var detailSubId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(tabRequest.value) {
        tabRequest.value?.let { want ->
            overlay = null
            detailSubId = null
            // 备忘录是浮层页(不是底部 tab),通知点进来也走这里
            if (want == "memo") overlay = "memo" else tab = want
            tabRequest.value = null
        }
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(LbBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = overlay ?: tab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { key ->
                when (key) {
                    "detail" -> SubscriptionDetailScreen(subId = detailSubId, onBack = { overlay = null })
                    "scan" -> ScanScreen(onBack = { overlay = null })
                    "duties" -> ObligationsScreen(onBack = { overlay = null })
                    "ledger" -> ExpenseScreen(onBack = { overlay = null })
                    "memo" -> MemoScreen(onBack = { overlay = null })
                    "report" -> MonthReportScreen(onBack = { overlay = null })
                    "states" -> StatesScreen(onBack = { overlay = null }, onOpenScan = { overlay = "scan" })
                    "vault" -> VaultScreen(onOpenStates = { overlay = "states" })
                    "今日" -> TodayScreen(
                        onOpenDuties = { overlay = "duties" },
                        onOpenGuard = { tab = "守护" },
                        onOpenLedger = { overlay = "ledger" },
                        onOpenReport = { overlay = "report" },
                    )
                    "守护" -> GuardScreen(
                        onOpenDetail = { id ->
                            detailSubId = id
                            overlay = "detail"
                        },
                        onOpenDuties = { overlay = "duties" },
                        onOpenScan = { overlay = "scan" },
                    )
                    "对话" -> ChatScreen()
                    "家庭" -> FamilyScreen()
                    "我的" -> MineScreen(
                        onOpenVault = { overlay = "vault" },
                        onOpenFamily = { tab = "家庭" },
                        onOpenReport = { overlay = "report" },
                        onOpenMemo = { overlay = "memo" },
                    )
                    else -> {}
                }
            }
        }
        if (overlay == null) {
            LbBottomBar(current = tab, onSelect = { tab = it })
        }
    }
}
