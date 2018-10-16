package net.bestia.messages.client

import com.fasterxml.jackson.annotation.JsonProperty

interface LatencyInfo {
  @get:JsonProperty("l")
  var latency: Int
}