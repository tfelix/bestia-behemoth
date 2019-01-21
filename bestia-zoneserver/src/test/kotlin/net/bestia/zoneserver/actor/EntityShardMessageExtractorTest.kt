package net.bestia.zoneserver.actor

import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import org.junit.Assert
import org.junit.Test

class EntityShardMessageExtractorTest {

  private val extractor = EntityShardMessageExtractor()

  @Test
  fun entityId_returns_id_for_accountMsg() {
    val msg = EntityEnvelope(10, "Test")
    val id = extractor.entityId(msg)
    Assert.assertTrue(id!!.contains("10"))
  }

  @Test
  fun shardId_returns_id_for_clientToMsgEnvelope() {
    val msg = EntityEnvelope(10, "Test")
    val id = extractor.shardId(msg)
    Assert.assertNotNull(id)
  }
}