package net.bestia.zone.dialog

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.DialogSmsgProto
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DialogServiceTest {

  private val registry = DialogDefinitionRegistry().apply {
    load(
      listOf(
        DialogDefinition(id = 1, identifier = "MASTER_INTRO", type = DialogType.CONFIRM, args = listOf("masterName")),
        DialogDefinition(id = 2, identifier = "NO_ARGS", type = DialogType.CONFIRM, args = emptyList()),
      )
    )
  }

  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val dialogService = DialogService(registry, outMessageProcessor)

  private val introDefinition get() = registry.getOrThrow(1)

  @Test
  fun `sends the dialog id and args to the given account`() {
    val sent = slot<SMSG>()
    every { outMessageProcessor.sendToPlayer(ACCOUNT_ID, capture(sent)) } returns Unit

    dialogService.send(ACCOUNT_ID, introDefinition, mapOf("masterName" to DialogArg.Text("Thorin")))

    val msg = sent.captured as DialogSMSG
    assertEquals(1, msg.dialogId)
    assertEquals(DialogType.CONFIRM, msg.type)
    assertEquals(mapOf("masterName" to DialogArg.Text("Thorin")), msg.args)
  }

  /**
   * A supplied-but-undeclared arg is the case that would otherwise fail silently: the value is
   * simply never substituted, and nothing anywhere reports it.
   */
  @Test
  fun `rejects an arg the dialog does not declare`() {
    val e = assertThrows<IllegalArgumentException> {
      dialogService.send(
        ACCOUNT_ID,
        introDefinition,
        mapOf("masterName" to DialogArg.Text("Thorin"), "extra" to DialogArg.Number(1))
      )
    }

    assertTrue(e.message!!.contains("unexpected: [extra]"), "should name the offending arg: ${e.message}")
    verify(exactly = 0) { outMessageProcessor.sendToPlayer(any<Long>(), any<SMSG>()) }
  }

  @Test
  fun `rejects a missing declared arg`() {
    val e = assertThrows<IllegalArgumentException> {
      dialogService.send(ACCOUNT_ID, introDefinition, emptyMap())
    }

    assertTrue(e.message!!.contains("missing: [masterName]"), "should name the missing arg: ${e.message}")
    verify(exactly = 0) { outMessageProcessor.sendToPlayer(any<Long>(), any<SMSG>()) }
  }

  @Test
  fun `an arg-less dialog can be sent without args`() {
    dialogService.send(ACCOUNT_ID, registry.getOrThrow(2))

    verify { outMessageProcessor.sendToPlayer(ACCOUNT_ID, any<SMSG>()) }
  }

  @Test
  fun `an unknown dialog id throws instead of sending nothing`() {
    assertThrows<DialogDefinitionNotFoundException> { registry.getOrThrow(999) }
  }

  /**
   * The whole point of the typed args: item/skill/entity references must survive onto the wire as
   * references so the client can localize them, rather than being flattened into strings here.
   */
  @Test
  fun `every arg type maps onto its own wire field`() {
    val msg = DialogSMSG(
      dialogId = 7,
      type = DialogType.CONFIRM,
      args = mapOf(
        "text" to DialogArg.Text("Thorin"),
        "number" to DialogArg.Number(42),
        "entity" to DialogArg.Entity(1234L),
        "item" to DialogArg.Item(9L),
        "skill" to DialogArg.Skill(3L),
      )
    )

    val dialog = msg.toBnetEnvelope().dialog

    assertEquals(7, dialog.dialogId)
    assertEquals(DialogSmsgProto.DialogType.CONFIRM, dialog.type)
    assertEquals("Thorin", dialog.argsMap.getValue("text").text)
    assertEquals(42L, dialog.argsMap.getValue("number").number)
    assertEquals(1234L, dialog.argsMap.getValue("entity").entityId)
    assertEquals(9L, dialog.argsMap.getValue("item").itemId)
    assertEquals(3L, dialog.argsMap.getValue("skill").skillId)
    assertFalse(dialog.hasSourceEntityId(), "no source entity was set")
  }

  @Test
  fun `a source entity is optional and only present when given`() {
    val withSource = DialogSMSG(dialogId = 2, type = DialogType.CONFIRM, sourceEntityId = 55L)
      .toBnetEnvelope().dialog

    assertTrue(withSource.hasSourceEntityId())
    assertEquals(55L, withSource.sourceEntityId)
  }

  companion object {
    private const val ACCOUNT_ID = 1L
  }
}
