package com.maquis.caisse.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maquis.caisse.ui.assistant.AssistantScreen
import com.maquis.caisse.ui.avoirs.AvoirsScreen
import com.maquis.caisse.ui.caisse.CaisseScreen
import com.maquis.caisse.ui.caissesession.CaisseSessionScreen
import com.maquis.caisse.ui.categories.CategoriesScreen
import com.maquis.caisse.ui.commandes.CommandesScreen
import com.maquis.caisse.ui.commandes.HistoriqueScreen
import com.maquis.caisse.ui.commandes.OrderDetailScreen
import com.maquis.caisse.ui.components.MaquisSideBar
import com.maquis.caisse.ui.components.SideBarViewModel
import com.maquis.caisse.ui.dashboard.DashboardScreen
import com.maquis.caisse.ui.dettes.DettesScreen
import com.maquis.caisse.ui.inventaire.InventaireScreen
import com.maquis.caisse.ui.mouvements.MouvementsScreen
import com.maquis.caisse.ui.parametres.ParametresScreen
import com.maquis.caisse.ui.produits.ProduitsScreen
import com.maquis.caisse.ui.rapports.RapportsScreen
import com.maquis.caisse.ui.stock.StockScreen
import com.maquis.caisse.ui.users.UsersScreen

@Composable
fun MaquisNavGraph(navController: NavHostController = rememberNavController()) {
    val sideBarVm: SideBarViewModel = hiltViewModel()
    val currentUser by sideBarVm.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == "ADMIN"

    Scaffold(containerColor = Color.Transparent) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MaquisSideBar(navController, viewModel = sideBarVm)
            NavHost(
                navController = navController,
                startDestination = Routes.CAISSE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                composable(Routes.CAISSE) {
                    // Après enregistrement : on reste en Caisse.
                    CaisseScreen()
                }
                composable(Routes.COMMANDES) {
                    CommandesScreen(
                        onOpenOrder = { id -> navController.navigate(Routes.orderDetail(id)) },
                    )
                }
                composable(Routes.HISTORIQUE) {
                    HistoriqueScreen(
                        onOpenOrder = { id -> navController.navigate(Routes.orderDetail(id)) },
                    )
                }
                composable(
                    route = Routes.ORDER_DETAIL,
                    arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
                ) { entry ->
                    val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                    OrderDetailScreen(
                        orderId = orderId,
                        onBack = { navController.popBackStack() },
                        onPaidGoToOpenOrders = {
                            if (!navController.popBackStack(Routes.COMMANDES, inclusive = false)) {
                                navController.navigate(Routes.COMMANDES) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
                composable(Routes.DASHBOARD) { DashboardScreen() }
                composable(Routes.ASSISTANT) { AssistantScreen() }
                composable(Routes.PRODUITS) {
                    AdminOnlyRoute(isAdmin = isAdmin, navController = navController) {
                        ProduitsScreen()
                    }
                }
                composable(Routes.CATEGORIES) {
                    AdminOnlyRoute(isAdmin = isAdmin, navController = navController) {
                        CategoriesScreen()
                    }
                }
                composable(Routes.STOCK) {
                    StockScreen(
                        onOpenInventaire = {
                            navController.navigate(Routes.INVENTAIRE) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Routes.INVENTAIRE) { InventaireScreen() }
                composable(Routes.CAISSE_SESSION) { CaisseSessionScreen() }
                composable(Routes.DETTES) { DettesScreen() }
                composable(Routes.AVOIRS) { AvoirsScreen() }
                composable(Routes.MOUVEMENTS) { MouvementsScreen() }
                composable(Routes.RAPPORTS) { RapportsScreen() }
                composable(Routes.UTILISATEURS) {
                    AdminOnlyRoute(isAdmin = isAdmin, navController = navController) {
                        UsersScreen()
                    }
                }
                composable(Routes.PARAMETRES) { ParametresScreen() }
            }
        }
    }
}

@Composable
private fun AdminOnlyRoute(
    isAdmin: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (!isAdmin) {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.CAISSE) {
                popUpTo(Routes.CAISSE) { inclusive = false }
                launchSingleTop = true
            }
        }
    } else {
        content()
    }
}
