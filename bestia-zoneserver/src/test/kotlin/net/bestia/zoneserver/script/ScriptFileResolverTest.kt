package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Test

class ScriptFileResolverTest {

  @Test
  fun `cleanScriptName with no js ending add ending`() {
    Assert.assertEquals("/test.js", cleanScriptName("test"))
    Assert.assertEquals("/test.js", cleanScriptName("test.js"))
  }

  @Test
  fun `cleanScriptName adds slash at the beginning`() {
    Assert.assertEquals("/test/test.js", cleanScriptName("test/test"))
    Assert.assertEquals("/test.js", cleanScriptName("/test"))
  }

}