package com.maquis.caisse.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Permissions
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UserRoleOption(val role: String, val label: String) {
    ADMIN("ADMIN", "Administrateur"),
    CAISSIER("CAISSIER", "Caissier"),
}

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val session: SessionManager,
) : ViewModel() {
    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentUser: StateFlow<AppUser?> = session.currentUser

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun isAdmin(): Boolean = session.userOrNull()?.role == "ADMIN" ||
        session.can(Permissions.MANAGE_USERS)

    fun add(name: String, pin: String, roleOption: UserRoleOption) = viewModelScope.launch {
        if (!isAdmin()) {
            _message.value = "Accès réservé à l'administrateur"
            return@launch
        }
        try {
            val permissions = when (roleOption) {
                UserRoleOption.ADMIN -> Permissions.ADMIN_DEFAULT.toSet()
                UserRoleOption.CAISSIER -> Permissions.CAISSIER_DEFAULT.toSet()
            }
            userRepository.add(
                AppUser(
                    name = name.trim(),
                    pin = pin,
                    role = roleOption.role,
                    permissions = permissions,
                ),
            )
            _message.value = "Compte « ${name.trim()} » créé"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec création"
        }
    }

    fun delete(user: AppUser) = viewModelScope.launch {
        if (!isAdmin()) {
            _message.value = "Accès réservé à l'administrateur"
            return@launch
        }
        val current = session.userOrNull()
        if (current?.id == user.id) {
            _message.value = "Impossible de supprimer le compte actuellement connecté"
            return@launch
        }
        try {
            userRepository.deactivate(user.id)
            _message.value = "Compte « ${user.name} » supprimé"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec suppression"
        }
    }

    fun changePin(userId: Long, newPin: String, confirm: String) = viewModelScope.launch {
        if (!isAdmin()) {
            _message.value = "Accès réservé à l'administrateur"
            return@launch
        }
        if (newPin != confirm) {
            _message.value = "Les deux codes ne correspondent pas"
            return@launch
        }
        try {
            userRepository.changePin(userId, newPin)
            _message.value = "Code mis à jour"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec modification du code"
        }
    }
}

@Composable
fun UsersScreen(viewModel: UsersViewModel = hiltViewModel()) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val current by viewModel.currentUser.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRoleOption.CAISSIER) }
    var toDelete by remember { mutableStateOf<AppUser?>(null) }
    var pinTarget by remember { mutableStateOf<AppUser?>(null) }

    if (!viewModel.isAdmin()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Réservé à l'administrateur.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageHeader(
            title = "Utilisateurs",
            subtitle = "Admin : ${current?.name ?: "—"} — déconnecte-toi pour changer de session",
        )
        TextPill("${users.size} comptes actifs", PillTone.INFO)

        GlassCard {
            Text("Créer un compte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("PIN initial") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(0.7f),
                    singleLine = true,
                )
            }
            DropdownField(
                label = "Rôle",
                selected = role,
                options = UserRoleOption.entries,
                optionLabel = { it.label },
                onSelect = { if (it != null) role = it },
            )
            Button(
                onClick = {
                    viewModel.add(name, pin, role)
                    name = ""
                    pin = ""
                },
                enabled = name.isNotBlank() && pin.length >= 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text("Créer le compte") }
        }

        message?.let { TextPill(it, PillTone.SUCCESS) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.id }) { user ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextPill(
                                    user.role.lowercase().replaceFirstChar { it.titlecase() },
                                    when (user.role) {
                                        "ADMIN" -> PillTone.DANGER
                                        "CAISSIER" -> PillTone.INFO
                                        else -> PillTone.CYAN
                                    },
                                )
                                if (user.id == current?.id) TextPill("Connecté", PillTone.SUCCESS)
                            }
                        }
                        TextButton(onClick = { pinTarget = user }) { Text("Code") }
                        if (user.id != current?.id) {
                            TextButton(onClick = { toDelete = user }) {
                                Text("Suppr.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    pinTarget?.let { user ->
        PinChangeDialog(
            title = "Nouveau code pour ${user.name}",
            onDismiss = { pinTarget = null },
            onConfirm = { a, b ->
                viewModel.changePin(user.id, a, b)
                pinTarget = null
            },
        )
    }

    toDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ${user.name} ?") },
            text = { Text("Le compte sera désactivé. L'historique reste intact.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(user)
                    toDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun PinChangeDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Nouveau code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Confirmer") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin, confirm) },
                enabled = pin.length >= 4 && confirm.length >= 4,
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
