package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.AbstractActorTest

class LevelComponentActorTest : AbstractActorTest() {

  fun `on level up new status component is calculated`() {
    testKit {
      val actor = testActorOf(LevelComponentActor::class)
    }
  }
}