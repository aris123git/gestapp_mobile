package com.maquis.caisse.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) categoryRepository.add(name.trim())
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) categoryRepository.rename(id, name.trim())
    }

    fun deactivate(id: Long) = viewModelScope.launch {
        categoryRepository.deactivate(id)
    }
}

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    val activeCount = categories.count { it.isActive }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageHeader(
            title = "Catégories",
            subtitle = "Organisation du catalogue produits",
        )
        TextPill("$activeCount actives · ${categories.size} au total", PillTone.INFO)

        GlassCard {
            Text("Nouvelle catégorie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nom") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.add(newName)
                        newName = ""
                    },
                    enabled = newName.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Ajouter") }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextPill(
                                if (cat.isActive) "Active" else "Inactive",
                                if (cat.isActive) PillTone.SUCCESS else PillTone.NEUTRAL,
                            )
                        }
                        if (cat.isActive) {
                            TextButton(onClick = { viewModel.deactivate(cat.id) }) {
                                Text("Désactiver")
                            }
                        }
                    }
                }
            }
        }
    }
}
