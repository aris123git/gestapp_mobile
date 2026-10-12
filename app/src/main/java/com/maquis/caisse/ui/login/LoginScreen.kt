package com.maquis.caisse.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val session: SessionManager,
    private val sessionRepository: com.maquis.caisse.domain.repository.CaisseSessionRepository,
) : ViewModel() {
    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun login(name: String, pin: String, openingBalance: Long = 0L, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _error.value = null
            if (name.isBlank() || pin.length < 4) {
                _error.value = "Choisis un compte et saisis ton code PIN"
                return@launch
            }
            val user = userRepository.login(name, pin)
            if (user == null) {
                _error.value = "Compte ou code incorrect"
                return@launch
            }
            session.setUser(user)
            sessionRepository.openSession(user, openingBalance)
            onSuccess()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var pin by remember { mutableStateOf("") }
    var showFondDialog by remember { mutableStateOf(false) }
    var fondInput by remember { mutableStateOf("") }

    if (showFondDialog) {
        AlertDialog(
            onDismissRequest = { showFondDialog = false },
            title = { Text("Fond de caisse") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Entrez le montant en espèces présent en caisse au départ (optionnel).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = fondInput,
                        onValueChange = { fondInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Montant FCFA") },
                        singleLine = true,
                        suffix = { Text("FCFA") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFondDialog = false
                        val fond = fondInput.toLongOrNull() ?: 0L
                        val name = selectedId?.let { id -> users.firstOrNull { it.id == id }?.name }.orEmpty()
                        viewModel.login(name, pin, fond, onLoggedIn)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GestionBlue),
                ) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFondDialog = false
                    val name = selectedId?.let { id -> users.firstOrNull { it.id == id }?.name }.orEmpty()
                    viewModel.login(name, pin, 0L, onLoggedIn)
                }) { Text("Passer") }
            },
        )
    }

    LaunchedEffect(users) {
        if (users.isEmpty()) {
            selectedId = null
            return@LaunchedEffect
        }
        if (selectedId == null || users.none { it.id == selectedId }) {
            selectedId = users.firstOrNull()?.id
        }
    }

    val selectedUser = users.firstOrNull { it.id == selectedId }

    val pulse = rememberInfiniteTransition(label = "loginPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glow)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GestionCyan.copy(alpha = 0.25f),
                            GestionBlue.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("NexaGes", style = MaterialTheme.typography.headlineMedium, color = GestionBlue)
                    Text(
                        "Touche ton compte, puis saisis ton code PIN.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (users.isEmpty()) {
                        Text(
                            "Aucun compte actif. Demande à l'administrateur d'en créer.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "Compte",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            users.forEach { user ->
                                val selected = user.id == selectedId
                                UserChip(
                                    name = user.name,
                                    role = user.role,
                                    selected = selected,
                                    onClick = {
                                        selectedId = user.id
                                        pin = ""
                                        viewModel.clearError()
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Code PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            fondInput = ""
                            showFondDialog = true
                        },
                        enabled = selectedUser != null && pin.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = GestionBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Text(
                            if (selectedUser != null) {
                                "Entrer — ${selectedUser.name}"
                            } else {
                                "Entrer"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserChip(
    name: String,
    role: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) GestionBlue else Color.White
    val fg = if (selected) Color.White else GestionBlue
    val border = if (selected) GestionBlue else GestionBlue.copy(alpha = 0.35f)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.5.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(min = 110.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(name, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(
            role.lowercase().replaceFirstChar { it.titlecase() },
            color = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
