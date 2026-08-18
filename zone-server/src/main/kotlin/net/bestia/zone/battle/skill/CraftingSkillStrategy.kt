package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.CraftingResult
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * The shape every skill in the Craftsman and Blacksmith trees has: aim it at the ground and either a station
 * goes up or the recipe list opens.
 *
 * The nine scripts that extend this differ only in which station they work at and whether they may build one,
 * which is why they are two constructor arguments rather than nine copies of the same `doAttack`. What actually
 * happens is decided by [net.bestia.zone.battle.skill.SkillExecutionService], because it needs the world to
 * know whether a station is already standing there - a strategy is a pure function of a [BattleContext].
 *
 * Range comes from the catalogue through [BasicMagicSkillStrategy], so the two-tile reach the client enforces
 * while aiming and the reach the server checks are the same number.
 */
abstract class CraftingSkillStrategy(
  losService: LineOfSightService,

  /** The station this work happens at, or null for work that needs none. */
  private val station: StaticEntityKind?,

  /** Whether aiming this skill at ground with no station on it builds one. */
  private val placesStation: Boolean = false
) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: BattleContext): Damage = CraftingResult(station, placesStation)
}
