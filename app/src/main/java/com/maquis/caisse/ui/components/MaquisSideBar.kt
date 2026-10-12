package com.maquis.caisse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import com.maquis.caisse.kiosk.KioskManager
import com.maquis.caisse.navigation.Routes
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.SidebarAccent
import com.maquis.caisse.ui.theme.SidebarEnd
import com.maquis.caisse.ui.theme.SidebarStart
import com.maquis.caisse.ui.theme.SidebarText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private data class NavItem(
    val route: String,
    val label: String,
    val adminOnly: Boolean = false,
)

@HiltViewModel
class SideBarViewModel @Inject constructor(
    private val session: SessionManager,
    private val sessionRepository: CaisseSessionRepository,
    private val kioskManager: KioskManager,
    settings: SettingsRepository,
) : ViewModel() {
    val currentUser = session.currentUser

    /** Option Paramètres : sans suivi de stock, les écrans Stock/Inventaire sont masqués. */
    val stockTrackingEnabled = settings.observe(SettingsKeys.STOCK_TRACKING_ENABLED)
        .map { it != "false" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun isAdmin(): Boolean = session.userOrNull()?.role == "ADMIN"

    fun logout() {
        viewModelScope.launch {
            sessionRepository.closeCurrentSession()
            session.logout()
            // Tablette dédiée : re-verrouille dès la déconnexion.
            kioskManager.onSessionEnded()
        }
    }
}

@Composable
fun MaquisSideBar(
    navController: NavHostController,
    viewModel: SideBarViewModel = hiltViewModel(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = viewModel.isAdmin()
    val stockTracking by viewModel.stockTrackingEnabled.collectAsStateWithLifecycle()

    val items = listOf(
        NavItem(Routes.CAISSE, "Caisse"),
        NavItem(Routes.COMMANDES, "Commandes"),
        NavItem(Routes.HISTORIQUE, "Historique"),
        NavItem(Routes.ASSISTANT, "Assistant"),
        NavItem(Routes.DASHBOARD, "Tableau de bord"),
        NavItem(Routes.PRODUITS, "Produits", adminOnly = true),
        NavItem(Routes.CATEGORIES, "Catégories", adminOnly = true),
        NavItem(Routes.STOCK, "Stock"),
        NavItem(Routes.INVENTAIRE, "Inventaire"),
        NavItem(Routes.CAISSE_SESSION, "Relève"),
        NavItem(Routes.DETTES, "Dettes"),
        NavItem(Routes.AVOIRS, "Avoirs"),
        NavItem(Routes.MOUVEMENTS, "Mouvements"),
        NavItem(Routes.RAPPORTS, "Rapports"),
        NavItem(Routes.UTILISATEURS, "Utilisateurs", adminOnly = true),
        NavItem(Routes.PARAMETRES, "Paramètres"),
    ).filter { !it.adminOnly || isAdmin }
        .filter { stockTracking || (it.route != Routes.STOCK && it.route != Routes.INVENTAIRE) }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val sidebarWidth = when {
        screenWidth < 800 -> 148.dp
        screenWidth < 1100 -> 172.dp
        else -> 196.dp
    }

    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SidebarStart, SidebarEnd, Color(0xFF0E2A4A)),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            "NexaGes",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Text(
            currentUser?.name ?: "Session",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarAccent,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            currentUser?.role ?: "",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        items.forEach { item ->
            val selected = currentRoute == item.route ||
                (item.route == Routes.COMMANDES && currentRoute?.startsWith("order_detail") == true)
            val bg by animateColorAsState(
                if (selected) GestionBlue else Color.Transparent,
                label = "navBg",
            )
            val scale by animateFloatAsState(
                if (selected) 1.02f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "navScale",
            )
            TextButton(
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.CAISSE) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = bg,
                    contentColor = if (selected) Color.White else SidebarText,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text("Déconnexion")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Offline · local",
            style = MaterialTheme.typography.labelLarge,
            color = SidebarText.copy(alpha = 0.7f),
        )
    }
}
