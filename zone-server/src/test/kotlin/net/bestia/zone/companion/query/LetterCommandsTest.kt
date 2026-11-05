package net.bestia.zone.companion.query

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer.LetterSent
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.LetterService.Outcome
import net.bestia.zone.postal.LetterService.Sender
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

/** A letter from the app keeps every rule a letter from the game has. */
class LetterCommandsTest {

  private val owned = mockk<OwnedMasters>()
  private val basicSkill = mockk<MasterBasicSkill>()
  private val letterService = mockk<LetterService>(relaxed = true)
  private val sessions = mockk<ConnectionInfoService>()

  /** Runs IO work at once, as if the account's inbox had nothing else queued. */
  private val inbox = object : AccountTaskExecutor {
    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      error("app letters run on the IO lane")
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> =
      CompletableFuture.completedFuture(task())
  }

  private val commands = LetterCommands(owned, basicSkill, letterService, inbox, sessions)

  private fun send() = commands.send("c", ACCOUNT, ZoneQuery.SendLetter(MASTER, "Mira", "Bridge", "It is down."))

  private fun givenMaySpeak(playing: Boolean = false) {
    every { owned.find(ACCOUNT, MASTER) } returns mockk<Master>()
    every { basicSkill.mayChat(ACCOUNT, MASTER) } returns true
    every { sessions.isPlaying(ACCOUNT, MASTER) } returns playing
    every { sessions.getSelectedMasterEntityId(ACCOUNT) } returns ENTITY
  }

  @Test
  fun `a letter from an offline master pays from the stored bag only`() {
    givenMaySpeak()
    every { letterService.send(any(), any(), any(), any()) } returns Outcome.Sent("Mira")

    val reply = send()

    assertEquals(LetterSent(null), reply.answer)
    verify { letterService.send(Sender(MASTER, liveEntityId = null), "Mira", "Bridge", "It is down.") }
  }

  @Test
  fun `a letter from the master being played also pays from its live bag`() {
    givenMaySpeak(playing = true)
    every { letterService.send(any(), any(), any(), any()) } returns Outcome.Sent("Mira")

    send()

    verify { letterService.send(Sender(MASTER, liveEntityId = ENTITY), any(), any(), any()) }
  }

  @Test
  fun `a master who may not speak yet sends nothing`() {
    givenMaySpeak()
    every { basicSkill.mayChat(ACCOUNT, MASTER) } returns false

    assertEquals(LetterSent(LetterSent.CHAT_LOCKED), send().answer)
    verify(exactly = 0) { letterService.send(any(), any(), any(), any()) }
  }

  @Test
  fun `the post's refusals become the app's`() {
    givenMaySpeak()
    every { letterService.send(any(), any(), any(), any()) } returns
      Outcome.Refused(OpError.MAIL_RECIPIENT_UNKNOWN, listOf("Mira"))

    assertEquals(LetterSent(LetterSent.RECIPIENT_UNKNOWN), send().answer)
  }

  @Test
  fun `a letter the form could not have made is invalid`() {
    givenMaySpeak()
    every { letterService.send(any(), any(), any(), any()) } returns Outcome.Refused(OpError.REQUEST_REFUSED)

    assertEquals(Status.INVALID, send().status)
  }

  @Test
  fun `somebody else's master cannot write or read`() {
    every { owned.find(ACCOUNT, MASTER) } returns null

    assertEquals(Status.NOT_FOUND, send().status)
    assertEquals(Status.NOT_FOUND, commands.list("c", ACCOUNT, ZoneQuery.Letters(MASTER)).status)
    assertEquals(Status.NOT_FOUND, commands.delete("c", ACCOUNT, ZoneQuery.DeleteLetter(MASTER, 3)).status)
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER = 10L
    const val ENTITY = 99L
  }
}
