package com.wmu.keban.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wmu.keban.ui.browser.BrowserScreen
import com.wmu.keban.ui.profile.ProfileScreen
import com.wmu.keban.ui.timetable.TimetableScreen
import com.wmu.keban.ui.today.TodayScreen
import com.wmu.keban.ui.todos.TodosScreen

object Routes {
    const val TODAY = "today"
    const val TIMETABLE = "timetable"
    const val TODOS = "todos"
    const val PROFILE = "profile"
    const val BROWSER = "browser"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun KebanApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val items = listOf(
        BottomItem(Routes.TODAY, "今天", Icons.Filled.Home),
        BottomItem(Routes.TIMETABLE, "课表", Icons.Filled.DateRange),
        BottomItem(Routes.TODOS, "待办", Icons.AutoMirrored.Filled.List),
        BottomItem(Routes.PROFILE, "我的", Icons.Filled.Person)
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in items.map { it.route }) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.TODAY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.TODAY) { TodayScreen() }
            composable(Routes.TIMETABLE) {
                TimetableScreen(
                    onOpenBrowser = { navController.navigate(Routes.BROWSER) }
                )
            }
            composable(Routes.TODOS) { TodosScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenBrowser = { navController.navigate(Routes.BROWSER) })
            }
            composable(Routes.BROWSER) {
                BrowserScreen(
                    onSwitchToTimetable = {
                        navController.navigate(Routes.TIMETABLE) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
