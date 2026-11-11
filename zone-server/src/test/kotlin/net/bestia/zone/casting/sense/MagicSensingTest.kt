package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MagicSensingTest {

  private val world = testWorld()
  private val sent = slot<SMSG>()
  private val sut = MagicSensing(
    worldService = mockk { every { config.voxelSize } returns 1.0 },
    mana = mockk<ManaConcentrationService> { every { factorAt(any()) } returns 1.0 },
    skills = mockk(relaxed = true),
    statusDefinitions = mockk<StatusEffectDefinitionRegistry> {
      every { findById(7L) } returns StatusEffectDefinition(7L, "BLESSING", true, "Blessing")
    },
    statusScripts = mockk<StatusEffectScriptRegistry> { every { this@mockk.get(any<String>()) } returns null },
    messages = mockk<OutMessageProcessor> { every { sendToPlayer(1L, capture(sent)) } returns Unit },
  )
  private val caster = world.createEntity { id ->
    add(id, Account(1L))
    add(id, Position.fromVec3(Vec3L(0, 0, 0)))
  }

  @Test
  fun `a blessed body and a mana crystal in range are found, a crystal out of range is not`() {
    blessedAt(Vec3L(5, 0, 0))
    crystalAt(Vec3L(0, 8, 0))
    crystalAt(Vec3L(40, 0, 0))

    val sources = sut.sourcesAround(world, caster, Vec3L(0, 0, 0), range = 10)

    assertEquals(setOf("Blessing", "Mana Crystal Large"), sources.map { it.name }.toSet())
  }

  @Test
  fun `level 1 only says something is there, level 4 names the spell`() {
    blessedAt(Vec3L(5, 0, 0))

    sut.sense(world, caster, level = 1)
    val vague = (sent.captured as SenseMarksSMSG).marks.single()
    assertTrue(vague.vague)
    assertEquals("", vague.label)

    sut.sense(world, caster, level = 4)
    assertEquals("strong neutral: Blessing", (sent.captured as SenseMarksSMSG).marks.single().label)
  }

  private fun blessedAt(at: Vec3L) = world.createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, StatusEffects(mutableListOf(
      ActiveStatusEffect(7L, 1, 60f, polarity = StatusEffectPolarity.BUFF, potency = 100)
    )))
  }

  private fun crystalAt(at: Vec3L) = world.createEntity { id ->
    add(id, PropPose(at, yaw = 0f))
    add(id, StaticVisual(StaticEntityKind.MANA_CRYSTAL_LARGE, variant = 0, heightDm = 10))
  }
}
