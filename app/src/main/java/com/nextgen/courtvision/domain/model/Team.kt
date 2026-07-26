package com.nextgen.courtvision.domain.model

data class Team(
    val id: String,
    val name: String,
    val joinCode: String,
    val coachIds: List<String>,
    val playerIds: List<String>,
)
