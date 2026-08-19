package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * The shape every skill in the Craftsman and Blacksmith trees has: aim it at the ground and either a station
 * goes up or the recipe list opens.
 *
 * The nine scripts that extend this differ only in which station they work at and whether they may build one,
 * which is why those are two constructor arguments rather than nine copies of this [execute].
 *
 * The "place one if there is none" rule is what makes a single activation discoverable: a player who has taken
 * Carpentry and aims it at bare ground gets a workbench, and aiming it at their workbench gets the menu. A
 * skill that only *uses* a station never places one, so aiming Weapon Repair at empty ground offers an empty
 * list rather than building a forge out of nothing.
 *
 * Range comes from the catalogue through [BasicMagicSkillStrategy], so the two-tile reach the client enforces
 * while aiming and the reach the server checks are the same number.
 */
abstract class CraftingSkillStrategy(
  losService: LineOfSightService,

  /** The station this work happens at, or null for work that needs none - a meal, a ritual, an upgrade. */
  private val station: StaticEntityKind?,

  /** Whether aiming this skill at ground with no station on it builds one. */
  private val placesStation: Boolean = false
) : BasicMagicSkillStrategy(losService) {

  init {
    require(!placesStation || station != null) {
      "A skill that places a station has to say which one"
    }
  }

  override fun execute(ctx: SkillContext): Damage? {
    val at = ctx.targetPosition ?: ctx.world.positionOf(ctx.casterId)
    if (at == null) {
      LOG.debug { "Crafting skill ${ctx.skillId} by ${ctx.casterId} has nowhere to work" }
      return null
    }

    if (placesStation && station != null && ctx.world.stationNear(at, station) == null) {
      val masterId = ctx.world.masterIdOf(ctx.casterId)
      if (masterId == null) {
        LOG.debug { "Entity ${ctx.casterId} is not a master and cannot build a $station" }
        return null
      }

      if (ctx.world.placeStation(station, masterId, at)) {
        return null
      }
    }

    ctx.world.offerRecipes(ctx.skillId)

    // Working is not hitting: there is no number to show over anything.
    return null
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
