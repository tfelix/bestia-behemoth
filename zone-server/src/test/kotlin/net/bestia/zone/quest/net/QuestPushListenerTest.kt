package net.bestia.zone.quest.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.quest.QuestChangedEvent
import net.bestia.zone.quest.QuestService
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Test

class QuestPushListenerTest {

  private val questService = mockk<QuestService> {
    every { holdersOf(QUEST) } returns setOf(ALICE_MASTER, BOB_MASTER)
    every { viewFor(any(), QUEST) } returns null
  }

  // Bob is offline; a third account plays a master that does not hold the quest.
  private val connections = mockk<ConnectionInfoService> {
    every { activeMasters() } returns mapOf(ALICE_ACCOUNT to ALICE_MASTER, OTHER_ACCOUNT to OTHER_MASTER)
  }
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val listener = QuestPushListener(questService, connections, out)

  @Test
  fun `only holders being played are told`() {
    listener.onChanged(QuestChangedEvent(QUEST))

    verify(exactly = 1) { out.sendToPlayer(ALICE_ACCOUNT, QuestUpdatedSMSG(QUEST, null)) }
    verify(exactly = 0) { out.sendToPlayer(OTHER_ACCOUNT, any<QuestUpdatedSMSG>()) }
  }

  private companion object {
    const val QUEST = 7L
    const val ALICE_ACCOUNT = 1L
    const val ALICE_MASTER = 11L
    const val BOB_MASTER = 12L
    const val OTHER_ACCOUNT = 3L
    const val OTHER_MASTER = 13L
  }
}
