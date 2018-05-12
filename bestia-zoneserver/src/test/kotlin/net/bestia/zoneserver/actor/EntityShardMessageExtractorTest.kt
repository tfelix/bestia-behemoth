package net.bestia.zoneserver.actor

import net.bestia.messages.entity.ToEntityEnvelope
import org.junit.Assert
import org.junit.Test

class EntityShardMessageExtractorTest {

  private val extractor = EntityShardMessageExtractor()

  @Test
  fun entityId_returns_id_for_accountMsg() {
    val msg = ToEntityEnvelope(10, "Test")
    val id = extractor.entityId(msg)
    Assert.assertTrue(id.contains("10"))
  }

  @Test
  fun shardId_returns_id_for_clientToMsgEnvelope() {
    val msg = ToEntityEnvelope(10, "Test")
    val id = extractor.shardId(msg)
    Assert.assertNotNull(id)
  }
}