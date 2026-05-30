package com.example.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes using @Serializable objects.
 * Pass these directly to navController.navigate(Route(...)).
 *
 * The composable() destination receives the route via savedStateHandle:
 *   composable<FilteredLedger> { backStackEntry ->
 *       val route: FilteredLedger = backStackEntry.toRoute()
 *       FilteredLedgerScreen(filter = route, ...)
 *   }
 */
sealed interface AppRoute

@Serializable
data object Home : AppRoute

@Serializable
data class FilteredLedger(
    val entity: String? = null,
    val direction: String? = null,
    val categoryId: String? = null,
    val timeRangeStart: Long? = null,
    val timeRangeEnd: Long? = null
) : AppRoute
