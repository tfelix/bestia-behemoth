package net.bestia.zoneserver.entity.factory

import net.bestia.model.geometry.Point

sealed class Blueprint

data class MobBlueprint(
    val mobDbName: String,
    val position: Point
) : Blueprint()

data class ItemBlueprint(
    val itemDbName: String,
    val position: Point,
    val amount: Int = 1
) : Blueprint()

data class ScriptBlueprint(
    val scriptName: String,
    val intervalMs: Int,
    val position: Point
) : Blueprint()

data class PlayerBestiaBlueprint(
    val playerBestiaId: Long
) : Blueprint()