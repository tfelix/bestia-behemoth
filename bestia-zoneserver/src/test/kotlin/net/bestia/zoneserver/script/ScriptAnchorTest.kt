package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Test

class ScriptAnchorTest {

  @Test
  fun fromString_createsCorrectAnchor() {
    var sa = ScriptAnchor.fromString("test.js")
    Assert.assertEquals("main", sa.functionName)
    Assert.assertEquals("test.js", sa.name)
    Assert.assertEquals("test.js:main", sa.anchorString)

    sa = ScriptAnchor.fromString("test/fire:callback")
    Assert.assertEquals("callback", sa.functionName)
    Assert.assertEquals("test/fire.js", sa.name)
    Assert.assertEquals("test/fire.js:callback", sa.anchorString)
  }
}