package net.bestia.zoneserver.battle.attack

data class KnownAttack(
    val minLevel: Int,
    val attackId: Long,
    val databaseName: String
)