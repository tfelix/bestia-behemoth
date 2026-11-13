package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.util.EntityId

/**
 * A Bard's song as it plays: the status on the Bard is the field, and everyone on the Bard's side inside it hears the
 * song. The field moves with the Bard. It ends early when the Bard falls, puts the instrument away or can no longer
 * cast, as when silenced or stunned.
 */
abstract class SongScript(aoi: EntityAOIService, private val songcraft: Songcraft) : AuraScript(aoi) {

  protected abstract val baseSeconds: Double

  override fun durationSeconds(level: Int): Double {
    return baseSeconds
  }

  override fun durationSeconds(world: World, bearerId: EntityId, level: Int): Double {
    return songcraft.durationSeconds(world, bearerId, baseSeconds)
  }

  override fun radiusTiles(world: World, bearerId: EntityId, effect: ActiveStatusEffect): Long {
    return songcraft.radiusTiles(world, bearerId)
  }

  override fun grantedAmount(world: World, bearerId: EntityId, effect: ActiveStatusEffect): Int {
    return songcraft.effectPercent(world, bearerId)
  }

  override fun reaches(world: World, bearerId: EntityId, otherId: EntityId): Boolean {
    return isFriendOf(world, bearerId, otherId)
  }

  override fun onTick(context: StatusEffectTickContext) {
    if (!isStillPlaying(context.world, context.hostId)) {
      context.removeSelf()
      return
    }

    super.onTick(context)
  }

  private fun isStillPlaying(world: World, bardId: EntityId): Boolean {
    val holdsInstrument = world.get(bardId, CombatStats::class)?.values?.heldWeaponType == WeaponType.INSTRUMENT
    val isSilenced = world.get(bardId, StatusEffects::class)?.isLocked(ActionLock.CAST) == true

    return holdsInstrument && !isSilenced && !world.has(bardId, Dead::class)
  }

  companion object {
    const val SONG_SECONDS = 60.0
  }
}
