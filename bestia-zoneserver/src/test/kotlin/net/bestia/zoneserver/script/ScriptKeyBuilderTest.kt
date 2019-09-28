package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Test

internal class ScriptKeyBuilderTest {

  @Test
  fun `build suitable script keys`() {
    val key1 = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, "tackle")
    Assert.assertEquals("attack_tackle", key1)

    val key2 = ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, "tackle.js")
    Assert.assertEquals("attack_tackle", key2)

    val key3 = ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, "strange.item.js")
    Assert.assertEquals("item_strange.item", key3)
  }
}