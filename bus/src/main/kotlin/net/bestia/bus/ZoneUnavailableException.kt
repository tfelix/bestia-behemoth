package net.bestia.bus

/** Nobody was subscribed to the node's request channel: the zone is down or still booting. */
class ZoneUnavailableException(nodeId: String) : RuntimeException("No zone is listening on node '$nodeId'")
