package net.bestia.zone

import java.util.*

/**
 * Internal eventing message which is used to deliver messages across different services. This is the topmost
 * layer.
 */
abstract class BestiaEvent(source: Any) : EventObject(source)