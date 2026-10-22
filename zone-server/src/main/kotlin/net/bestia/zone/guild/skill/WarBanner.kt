package net.bestia.zone.guild.skill

import net.bestia.zone.ecs.core.Component

/** A guild's planted War Banner, which Rally to the Banner calls the guild to, until [remainingSeconds] run out. */
class WarBanner(
  val guildId: Long,
  var remainingSeconds: Float,
) : Component
