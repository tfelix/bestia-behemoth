package net.bestia.zoneserver.actor

import net.bestia.messages.AccountMessage
import net.bestia.messages.ClientToMessageEnvelope
import org.junit.Assert
import org.junit.Test

class ConnectionShardMessageExtractorTest {

  private val extractor = ConnectionShardMessageExtractor()

  @Test
  fun entityId_returns_id_for_accountMsg() {
    val accMsg = object : AccountMessage(10) {
      override fun createNewInstance(accountId: Long): AccountMessage {
        return this
      }
    }
    val id = extractor.entityId(accMsg)
    Assert.assertTrue(id.contains("10"))
  }

  @Test
  fun entityId_returns_id_for_clientToMsgEnvelope() {
    val env = ClientToMessageEnvelope(10, "Test")
    val id = extractor.entityId(env)
    Assert.assertTrue(id.contains("10"))
  }

  @Test
  fun shardId_returns_id_for_clientToMsgEnvelope() {
    val env = ClientToMessageEnvelope(10, "Test")
    val id = extractor.shardId(env)
    Assert.assertNotNull(id)
  }

  @Test
  fun shardId_returns_id_for_accountMsg() {
    val accMsg = object : AccountMessage(10) {
      override fun createNewInstance(accountId: Long): AccountMessage {
        return this
      }
    }
    val id = extractor.shardId(accMsg)
    Assert.assertNotNull(id)
  }
}