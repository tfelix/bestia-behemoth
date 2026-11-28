package net.bestia.zone.item.meal

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.item.script.ItemScriptSource
import net.bestia.zone.item.script.RestorativeScript
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Eating one meal of `meals.yml`, once its channel is through (see `EatingService`): it restores what the meal
 * restores, replaces whatever [StatusEffectId.SATISFIED] the eater had with this meal's, and lays on the meal's
 * other `grants`.
 *
 * A [RestorativeScript], so a bestia finds a meal in its bag the way it finds bread.
 */
class MealScript(
  val meal: MealCatalog.Meal,
  messages: OutMessageProcessor,
  private val effects: StatusEffectService,
) : RestorativeScript(
  itemId = meal.itemId,
  health = meal.health,
  stamina = meal.stamina,
  mana = meal.mana,
  messages = messages,
  statusEffects = effects,
) {

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    restore(world, userId, FULL_STRENGTH_PERCENT)

    // Off first, so the new meal brings its own bonus set and its own timer even when the old one had longer left.
    effects.removeEffect(world, userId, StatusEffectId.SATISFIED.id, RemovalReason.CANCELLED)
    val satisfied = effects.applyEffect(
      world, userId, StatusEffectId.SATISFIED.id, level = 1,
      durationSeconds = meal.durationSeconds.toDouble(), amount = meal.itemId.toInt(),
    )

    meal.grants.forEach { grant ->
      effects.applyEffect(world, userId, grant.effect.id, grant.level, durationSeconds = grant.durationSeconds?.toDouble())
    }

    // A meal is eaten even by a full stomach: its buff is the point of it.
    return satisfied
  }

  private companion object {
    const val FULL_STRENGTH_PERCENT = 100
  }
}

/** A [MealScript] for every meal of the [MealCatalog]. */
@Component
class MealScripts(
  private val meals: MealCatalog,
  private val messages: OutMessageProcessor,
  private val effects: StatusEffectService,
) : ItemScriptSource {

  override fun scripts(): List<ItemScript> = meals.all().map { MealScript(it, messages, effects) }
}
