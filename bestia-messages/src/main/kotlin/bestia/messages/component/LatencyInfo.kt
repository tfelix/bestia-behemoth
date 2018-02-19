package bestia.messages.component

interface LatencyInfo {

  fun createNewInstance(accountId: Long, latency: Int): LatencyInfo
}