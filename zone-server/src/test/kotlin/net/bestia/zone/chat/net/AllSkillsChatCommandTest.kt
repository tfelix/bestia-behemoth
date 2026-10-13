package net.bestia.zone.chat.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.account.Role
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.master.bestia.PlayerBestiaSkillService
import net.bestia.zone.master.skill.MasterSkillTreeService
import net.bestia.zone.master.skill.SkillListSMSG
import net.bestia.zone.master.skill.SkillListService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AllSkillsChatCommandTest {

  private val world = testWorld()
  private val connectionInfo = mockk<ConnectionInfoService>()
  private val masterSkillTreeService = mockk<MasterSkillTreeService>()
  private val playerBestiaSkillService = mockk<PlayerBestiaSkillService>()
  private val skillListService = mockk<SkillListService>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = AllSkillsChatCommand(
    connectionInfo,
    world,
    masterSkillTreeService,
    playerBestiaSkillService,
    skillListService,
    out
  )

  private val list = listOf(SkillListSMSG.SkillListEntry(skillId = EMBER, level = 1))

  init {
    every { connectionInfo.getSelectedMasterEntityId(ACCOUNT) } returns MASTER_ENTITY
  }

  private fun givenActive(entityId: Long) {
    every { connectionInfo.getActiveEntityId(ACCOUNT) } returns entityId
    every { skillListService.entriesFor(ACCOUNT, entityId) } returns list
  }

  private fun givenBestia(known: MutableMap<Long, Int>) {
    givenActive(BESTIA_ENTITY)
    every { connectionInfo.getActivePlayerBestiaId(ACCOUNT) } returns PLAYER_BESTIA
    world.createEntity(BESTIA_ENTITY) { id -> add(id, KnownSkills(known)) }
  }

  private fun knownLevel(skillId: Long): Int {
    return world.get(BESTIA_ENTITY, KnownSkills::class)!!.levelOf(skillId)
  }

  private fun runAsGm(): Boolean {
    return sut.tryExecute(ACCOUNT, "/allskills", Role.GM.authorities)
  }

  @Test
  fun `only a GM may use it`() {
    assertEquals(Authority.SKILLS, sut.requiredAuthority)
    assertTrue(Authority.SKILLS in Role.GM.authorities)
    assertFalse(Authority.SKILLS in Role.USER.authorities)
    assertFalse(sut.tryExecute(ACCOUNT, "/allskills", Role.USER.authorities))
  }

  @Test
  fun `it matches the bare command and nothing longer`() {
    assertTrue(sut.isMatch("/allskills"))
    assertTrue(sut.isMatch("  /allskills "))
    assertFalse(sut.isMatch("/allskills 5"))
    assertFalse(sut.isMatch("/allskillsplease"))
  }

  @Test
  fun `a master has its tree raised, then gets the list and a summary`() {
    givenActive(MASTER_ENTITY)
    world.createEntity(MASTER_ENTITY) { id -> add(id, KnownSkills(mutableMapOf())) }
    every { masterSkillTreeService.grantAll(any(), ACCOUNT, MASTER_ENTITY) } returns mapOf(1L to 5, 2L to 3)

    assertTrue(runAsGm())

    verify { masterSkillTreeService.grantAll(any(), ACCOUNT, MASTER_ENTITY) }
    verify { out.sendToPlayer(ACCOUNT, SkillListSMSG(MASTER_ENTITY, list)) }
    verify { out.sendToPlayer(ACCOUNT, ChatSMSG(text = "Raised 2 skills to max level.", type = ChatType.COMMAND)) }
  }

  @Test
  fun `a bestia learns its learnset and the skills it did not know become known and dirty the status`() {
    givenBestia(mutableMapOf(EMBER to 1))
    every { playerBestiaSkillService.learnWholeLearnset(PLAYER_BESTIA) } returns mapOf(EMBER to 1, LATER to 1)

    assertTrue(runAsGm())

    assertEquals(1, knownLevel(EMBER))
    assertEquals(1, knownLevel(LATER))
    assertTrue(world.has(BESTIA_ENTITY, IsStatusValueDirty::class))
    verify { out.sendToPlayer(ACCOUNT, SkillListSMSG(BESTIA_ENTITY, list)) }
    verify { out.sendToPlayer(ACCOUNT, ChatSMSG(text = "Learned 1 new skills.", type = ChatType.COMMAND)) }
  }

  @Test
  fun `a bestia keeps a skill it knows at a higher level and is not marked dirty when nothing is new`() {
    givenBestia(mutableMapOf(EMBER to 4))
    every { playerBestiaSkillService.learnWholeLearnset(PLAYER_BESTIA) } returns mapOf(EMBER to 1)

    assertTrue(runAsGm())

    assertEquals(4, knownLevel(EMBER))
    assertFalse(world.has(BESTIA_ENTITY, IsStatusValueDirty::class))
    verify { out.sendToPlayer(ACCOUNT, ChatSMSG(text = "Learned 0 new skills.", type = ChatType.COMMAND)) }
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER_ENTITY = 100L
    const val BESTIA_ENTITY = 200L
    const val PLAYER_BESTIA = 11L
    const val EMBER = 1000L
    const val LATER = 1001L
  }
}
