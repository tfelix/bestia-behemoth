package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoughGemScriptTest {

  private val skills = mockk<SkillRepository> {
    every { findByIdentifier(SkillId.M_GEM_LORE.name) } returns mockk<Skill> { every { id } returns GEM_LORE_ID }
  }

  private val items = mockk<ItemTemplateRegistry> {
    RoughGemScript.SHARES.forEachIndexed { i, (identifier, _) -> every { idOf(identifier) } returns GEM_IDS[i] }
  }

  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private var roll = 0
  private val random = mockk<Random> { every { nextInt(any()) } answers { roll } }

  private val sut = RoughGemScript(skills, items, messages, random)
  private val world = testWorld()

  @Test
  fun `a master with Gem Lore gets the gem the stone really is`() {
    val master = master(gemLore = 1)

    assertTrue(sut.execute(world, master, ScriptArgs.EMPTY))

    assertEquals(listOf(GEM_IDS[0] to 1), granted(master))
  }

  @Test
  fun `the rarer the gem, the rarer the stone that hides it`() {
    // 50 amethyst, 25 emerald, 15 ruby, 8 pyrelith, then 2 diamond: rolls 98 and 99 are the diamonds.
    val gems = listOf(0, 49, 50, 74, 75, 89, 90, 97, 98, 99).map { rolled ->
      roll = rolled
      val master = master(gemLore = 3)
      sut.execute(world, master, ScriptArgs.EMPTY)
      granted(master).single().first
    }

    assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4).map { GEM_IDS[it] }, gems)
  }

  @Test
  fun `without Gem Lore the stone stays in the bag`() {
    val master = master(gemLore = 0)

    assertFalse(sut.execute(world, master, ScriptArgs.EMPTY))

    assertNull(world.get(master, CreateItemsIntent::class))
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.GEM_LORE_NEEDED)) }
  }

  private fun master(gemLore: Int) = world.createEntity {
    add(it, Account(ACCOUNT_ID))
    add(it, KnownSkills(mutableMapOf(GEM_LORE_ID to gemLore)))
  }

  private fun granted(master: Long) =
    world.get(master, CreateItemsIntent::class)!!.grants.map { it.itemId to it.amount }

  private companion object {
    const val GEM_LORE_ID = 180L
    const val ACCOUNT_ID = 4L
    val GEM_IDS = listOf(401L, 402L, 403L, 404L, 405L)
  }
}
