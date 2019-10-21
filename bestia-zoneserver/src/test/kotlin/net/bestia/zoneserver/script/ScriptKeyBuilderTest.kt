package net.bestia.zoneserver.script

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScriptKeyBuilderTest {

  @Test
  fun `build suitable script keys`() {
    val key1 = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, "tackle")
    assertEquals("attack_tackle", key1)

    val key2 = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, "tackle.js")
    assertEquals("attack_tackle", key2)

    val key3 = ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, "strange.item.js")
    assertEquals("item_strange.item", key3)
  }
}