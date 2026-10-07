package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusEffectServiceTest {

  /** Registered by simple class name; what it does to values does not matter here. */
  private class Lingering : StatusEffectScript {
    override fun durationSeconds(level: Int): Double {
      return 10.0
    }
  }

  private val world = testWorld()

  private val service = StatusEffectService(
    StatusEffectDefinitionRegistry().apply {
      load(
        listOf(
          definition(HARM, StatusEffectPolarity.DEBUFF),
          definition(HELP, StatusEffectPolarity.BUFF),
        )
      )
    },
    StatusEffectScriptRegistry(listOf(Lingering()))
  )

  @Test
  fun `a harmful effect from a player does not reach a warded player`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertFalse(service.applyEffect(world, warded, HARM, level = 1, sourceEntityId = player()))
    assertFalse(world.getOrThrow(warded, StatusEffects::class).hasEffect(HARM))
  }

  @Test
  fun `a helpful one does`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertTrue(service.applyEffect(world, warded, HELP, level = 1, sourceEntityId = player()))
  }

  @Test
  fun `and a mob's harmful effect still lands`() {
    val warded = player(shield = HarmShield.PLAYERS)
    val mob = world.createEntity { }

    assertTrue(service.applyEffect(world, warded, HARM, level = 1, sourceEntityId = mob))
  }

  private fun player(shield: HarmShield? = null): EntityId {
    val effect = ActiveStatusEffect(definitionId = 99L, level = 1, remainingSeconds = 10f, shield = shield)

    return world.createEntity { id ->
      add(id, Account(accountId = id))
      add(id, StatusEffects(mutableListOf(effect)))
    }
  }

  private fun definition(id: Long, polarity: StatusEffectPolarity): StatusEffectDefinition {
    return StatusEffectDefinition(
      id = id, identifier = "TEST_$id", isSyncedToClient = true, script = "Lingering", polarity = polarity
    )
  }

  private companion object {
    const val HARM = 1L
    const val HELP = 2L
  }
}
