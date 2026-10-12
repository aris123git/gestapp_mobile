package com.maquis.caisse.domain.assistant

enum class SuggestionLevel { DANGER, WARNING, INFO }

data class AssistantSuggestion(
    val level: SuggestionLevel,
    val title: String,
    val detail: String,
    val category: String,
)
