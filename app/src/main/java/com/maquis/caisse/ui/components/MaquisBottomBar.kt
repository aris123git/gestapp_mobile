package com.maquis.caisse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maquis.caisse.navigation.Routes

/**
 * Barre de navigation basse — cibles tactiles larges (Material3),
 * cohérent avec la contrainte UX "≥ 48dp / usage à une main".
 *
 * Sprint 1 : Caisse + Produits. Les autres onglets arriveront au fil
 * des sprints pour éviter une barre surchargée.
 */
@Composable
fun MaquisBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.CAISSE,
            onClick = {
                if (currentRoute != Routes.CAISSE) {
                    navController.navigate(Routes.CAISSE) {
                        popUpTo(Routes.CAISSE) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
            label = { Text("Caisse") },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.PRODUITS,
            onClick = {
                if (currentRoute != Routes.PRODUITS) {
                    navController.navigate(Routes.PRODUITS) {
                        popUpTo(Routes.CAISSE) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            icon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
            label = { Text("Produits") },
        )
    }
}
