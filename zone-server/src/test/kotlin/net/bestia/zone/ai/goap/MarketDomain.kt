package net.bestia.zone.ai.goap

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionResolver
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.precondition.Preconditions
import net.bestia.zone.ai.goap2.state.StateKey
import net.bestia.zone.ai.goap2.state.WorldState
import net.bestia.zone.geometry.Vec2F
import kotlin.collections.iterator

/**
 * A tiny worked domain shared by the planner tests. It exercises all three
 * flavours of memory at once: numeric (gold, satiation), a complex Vector2
 * (position), and a collection (inventory).
 */
object MarketDomain {

  val POSITION = StateKey<Vec2F>("position")
  val GOLD = StateKey<Int>("gold")
  val TIREDNESS = StateKey<Int>("tiredness")
  val SATIATION = StateKey<Int>("satiation")
  val INVENTORY = StateKey<Set<String>>("inventory")

  const val FOOD = "food"
  const val FOOD_PRICE = 5

  val HOME = Vec2F(0f, 0f)
  val MARKET = Vec2F(10f, 0f)

  private const val ARRIVAL_RADIUS = 0.5f
  private val locations = mapOf("home" to HOME, "market" to MARKET)

  private fun inventoryOf(state: WorldState): Set<String> = state.get(INVENTORY) ?: emptySet()
  private fun atMarket(state: WorldState): Boolean =
    state.get(POSITION)?.let { it.distanceTo(MARKET) <= ARRIVAL_RADIUS } ?: false

  private fun atHome(state: WorldState): Boolean =
    state.get(POSITION)?.let { it.distanceTo(HOME) <= ARRIVAL_RADIUS } ?: false

  /** Grounds walkTo / buyItem / eat against the concrete current state. */
  val resolver = ActionResolver { state ->
    buildList {
      val position = state.get(POSITION) ?: HOME

      // walkTo(location) — one grounded action per known location we're not at.
      for ((locName, target) in locations) {
        if (position.distanceTo(target) <= ARRIVAL_RADIUS) continue
        add(
          Action(
            name = "walkTo($locName)",
            effects = listOf(Effects.set(POSITION, target)),
            cost = { it.get(POSITION)?.distanceTo(target) ?: target.distanceTo(HOME) },
          )
        )
      }

      // buyItem(food) — only sold at the market, only if affordable and unowned.
      add(
        Action(
          name = "buyItem($FOOD)",
          preconditions = listOf(
            Preconditions.satisfies(POSITION, "at market") { atMarket(state) },
            Preconditions.atLeast(GOLD, FOOD_PRICE),
            Preconditions.satisfies(INVENTORY, "no $FOOD yet") { FOOD !in (it ?: emptySet()) },
          ),
          effects = listOf(
            Effects.modify(GOLD) { (it ?: 0) - FOOD_PRICE },
            Effects.modify(INVENTORY) { (it ?: emptySet()) + FOOD },
          ),
          cost = { 1f },
        )
      )

      // eat — consumes food from inventory and fills satiation.
      add(
        Action(
          name = "eat",
          preconditions = listOf(
            Preconditions.satisfies(INVENTORY, "has $FOOD") { FOOD in (it ?: emptySet()) },
          ),
          effects = listOf(
            Effects.set(SATIATION, 100),
            Effects.modify(INVENTORY) { (it ?: emptySet()) - FOOD },
          ),
          cost = { 1f },
        )
      )

      // sleep - action
      add(
        Action(
          name = "sleepAtHome",
          preconditions = listOf(
            Preconditions.satisfies(POSITION, "at home") { state.get(POSITION)?.let { it.distanceTo(HOME) <= ARRIVAL_RADIUS } ?: false },
          ),
          effects = listOf(
            Effects.modify(TIREDNESS) { (it ?: 0) - 25 },
            Effects.modify(SATIATION) { (it ?: 0) - 20 },
          ),
          cost = { 1f },
        )
      )

      add(
        Action(
          name = "sleepAnywhere",
          preconditions = listOf(),
          effects = listOf(
            Effects.modify(TIREDNESS) { (it ?: 0) - 15 },
            Effects.modify(SATIATION) { (it ?: 0) - 30 },
          ),
          cost = { 10f },
        )
      )
    }.filter { it.isApplicable(state) }
  }
}
