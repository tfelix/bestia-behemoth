package net.bestia.zoneserver.actor

import net.bestia.messages.AccountMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.connection.ConnectionShardMessageExtractor
import org.junit.Assert
import org.junit.Test

class ConnectionShardMessageExtractorTest {

  private val extractor = ConnectionShardMessageExtractor()

  private val accMsg = object : AccountMessage {
    override val accountId: Long
      get() = 10
  }

  @Test
  fun entityId_returns_id_for_clientToMsgEnvelope() {
    val env = ClientEnvelope(10, "Test")
    val id = extractor.entityId(env)
    Assert.assertTrue(id!!.contains("10"))
  }

  @Test
  fun shardId_returns_id_for_clientToMsgEnvelope() {
    val env = ClientEnvelope(10, "Test")
    val id = extractor.shardId(env)
    Assert.assertNotNull(id)
  }
}