package com.maquis.caisse.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.assistant.AssistantAnalyzer
import com.maquis.caisse.domain.assistant.AssistantSuggestion
import com.maquis.caisse.domain.assistant.SuggestionLevel
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val analyzer: AssistantAnalyzer,
) : ViewModel() {
    private val _suggestions = MutableStateFlow<List<AssistantSuggestion>>(emptyList())
    val suggestions: StateFlow<List<AssistantSuggestion>> = _suggestions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _suggestions.value = analyzer.suggestions()
            _loading.value = false
        }
    }
}

@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(
            title = "Assistant",
            subtitle = "Analyse tes données et propose des actions.",
            actionLabel = if (loading) "Analyse…" else "Actualiser",
            onAction = viewModel::refresh,
            actionEnabled = !loading,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(suggestions, key = { i, s -> "${s.title}-$i" }) { _, suggestion ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 4 },
                ) {
                    SuggestionCard(suggestion)
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: AssistantSuggestion) {
    val tone = when (suggestion.level) {
        SuggestionLevel.DANGER -> PillTone.DANGER
        SuggestionLevel.WARNING -> PillTone.WARNING
        SuggestionLevel.INFO -> PillTone.INFO
    }
    val badge = when (suggestion.level) {
        SuggestionLevel.DANGER -> "Urgent"
        SuggestionLevel.WARNING -> "À surveiller"
        SuggestionLevel.INFO -> "Idée"
    }
    GlassCard {
        TextPill("$badge · ${suggestion.category}", tone)
        Text(suggestion.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(suggestion.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
