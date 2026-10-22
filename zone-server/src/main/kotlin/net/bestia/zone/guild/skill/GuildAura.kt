package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 12 (`GUILD_AURA`): the guild leader's presence. [GuildAuraSystem] keeps it on the
 * members nearby with the leader as its source, and it adds the stat auras of the leader's guild.
 */
@Component
class GuildAura : StatusEffectScript {

  /** It runs out within seconds of the leader leaving, so a stored copy is never worth restoring. */
  override val isPersisted: Boolean = false

  /** Refreshed every pulse, so a countdown would only ever show the same few seconds. */
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    // A leader who left gives nothing for the few seconds the effect outlives them.
    val leader = sourceEntityId?.let { world.get(it, GuildMembership::class) } ?: return

    for ((aura, auraLevel) in GuildAuraSkill.levelsOf(leader::skillLevel)) {
      aura.raise(context, auraLevel)
    }
  }

  companion object {
    /** Outlasts two of [GuildAuraSystem]'s pulses, so one late pulse leaves no gap. */
    const val DURATION_SECONDS = 5.0
  }
}
