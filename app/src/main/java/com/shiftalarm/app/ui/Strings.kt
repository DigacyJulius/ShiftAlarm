package com.shiftalarm.app.ui

import androidx.compose.runtime.compositionLocalOf

data class AppStrings(
    val home: String,
    val locations: String,
    val alarms: String,
    val settings: String,
    val diagnostics: String,
    val syncNow: String,
    val testAlarm: String,
    val delete: String,
    val restore: String,
    val restoreAll: String,
    val language: String,
    val followSystem: String,
    val light: String,
    val dark: String,
    val noAlarms: String,
    val nextAlarm: String
)

val ChineseStrings = AppStrings(
    home = "首頁",
    locations = "地點設定檔",
    alarms = "一般鬧鐘",
    settings = "設定",
    diagnostics = "診斷",
    syncNow = "立即同步",
    testAlarm = "測試鬧鐘（15秒後）",
    delete = "刪除",
    restore = "還原",
    restoreAll = "還原全部",
    language = "語言",
    followSystem = "跟隨系統",
    light = "淺色",
    dark = "深色",
    noAlarms = "暫時未有排程鬧鐘。",
    nextAlarm = "下一個鬧鐘"
)

val EnglishStrings = AppStrings(
    home = "Home",
    locations = "Locations",
    alarms = "Alarms",
    settings = "Settings",
    diagnostics = "Diagnostics",
    syncNow = "Sync Now",
    testAlarm = "Test Alarm (15s)",
    delete = "Delete",
    restore = "Restore",
    restoreAll = "Restore All",
    language = "Language",
    followSystem = "System",
    light = "Light",
    dark = "Dark",
    noAlarms = "No alarms scheduled.",
    nextAlarm = "Next Alarm"
)

val LocalStrings = compositionLocalOf { ChineseStrings }
