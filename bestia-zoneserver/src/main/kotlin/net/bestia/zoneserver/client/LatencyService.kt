package net.bestia.zoneserver.client

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.IMap
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * This services helps to keep track of the client latency. It generates
 * timestamps and manages the latency calculation. This can and should be used
 * in order to reduce lag while sending position and motion updates to the
 * clients.
 *
 * @author Thomas Felix
 */
@Service
class LatencyService(
        hz: HazelcastInstance
) {

  private val latencyStore: IMap<Long, Queue<Int>>
  private val timestampStore: IMap<Long, Long>

  init {
    latencyStore = hz.getMap(LATENCY_STORE)
    timestampStore = hz.getMap(STAMP_STORE)
  }

  /**
   * @param accId
   * The account of the client to request.
   * @return Returns the time of the last reply by this client in the unix
   * timestamp format of 0 if the client never replied.
   */
  fun getLastClientReply(accId: Long): Long {
    return timestampStore.getOrDefault(accId, 0L)
  }

  /**
   * Generates a new latency entry for the given client. It throws if no
   * server timestamp was previously set via [.getTimestamp]. The
   * server stamp is used as precauson because the server timestamp could have
   * been changed in the meantime. Only if it matches the saved value the
   * latency estimation will be added.
   *
   * @param accountId
   * The account id.
   * @param clientStamp
   * The timestamp when the message was send to the client.
   * @param serverStamp
   * The timestamp when the message arrived again on the server.
   */
  fun addLatency(accountId: Long, clientStamp: Long, serverStamp: Long) {
    if (clientStamp > serverStamp) {
      throw IllegalArgumentException("Client stamp is bigger then server stamp.")
    }

    // Save the last client answer.
    timestampStore.putAsync(accountId, serverStamp)

    val latency = (serverStamp - clientStamp).toInt()

    var data: Queue<Int>? = latencyStore[accountId]

    if (data == null) {
      data = LinkedList()
    }

    if (data.size == MAX_NUM_LATENCY_DATA) {
      data.poll()
    }

    data.add(latency)
    latencyStore[accountId] = data
    LOG.debug("Added latency {} ms for user {}.", latency, accountId)
  }

  /**
   * Returns the client latency. It throws if there is no entry found for the
   * given account id.
   *
   * @param accountId
   * The account to return the lataency for.
   * @return The estimated latency to this client.
   */
  fun getClientLatency(accountId: Long): Int {

    val stamps = latencyStore[accountId] ?: throw IllegalStateException("No latency for account $accountId found")

    val data = stamps.toIntArray()
    Arrays.sort(data)

    val median = if (data.size % 2 == 0) {
      (data[data.size / 2] + data[data.size / 2 - 1]) / 2
    } else {
      data[data.size / 2]
    }

    LOG.debug("Found median latency {} ms for user {}.", median, accountId)

    return median
  }

  /**
   * Removes all client latency data for this account id.
   *
   * @param accountId
   * The account id.
   */
  fun delete(accountId: Long) {
    latencyStore.remove(accountId)
    timestampStore.remove(accountId)
  }

  companion object {
    private const val MAX_NUM_LATENCY_DATA = 20
    private const val LATENCY_STORE = "latency.store"
    private const val STAMP_STORE = "latency.stamp"
  }
}