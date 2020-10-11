package net.bestia.zoneserver.actor

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntityShardMessageExtractorTest {

  private class TestMessage(override val entityId: Long) : EntityMessage

  private val extractor = EntityShardMessageExtractor()

  @Test
  fun entityId_returns_id_for_accountMsg() {
    val msg = TestMessage(10)
    val id = extractor.entityId(msg)
    assertTrue(id!!.contains("10"))
  }

  @Test
  fun shardId_returns_id_for_clientToMsgEnvelope() {
    val msg = TestMessage(10)
    val id = extractor.shardId(msg)
    assertNotNull(id)
  }
}