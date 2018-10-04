package net.bestia.zoneserver.entity.factory

import net.bestia.model.geometry.Point

sealed class Blueprint

class MobBlueprint(
    val mobDbName: String,
    val position: Point
) : Blueprint()

class ItemBlueprint(
    val itemDbName: String,
    val position: Point,
    val amount: Int = 1
) : Blueprint()

class ScriptBlueprint(
    val scriptName: String,
    val intervalMs: Int,
    val position: Point
) : Blueprint()

class PlayerBestiaBlueprint(
    val playerBestiaId: Long
) : Blueprint()