package de.tfelix.bestia.worldgen.message

import java.io.Serializable
import java.util.Objects

/**
 * Message which is exchanged between the nodes in order to signal the current
 * state of the workload.
 *
 * @author Thomas Felix
 */
class WorkstateMessage(
    val source: String,
    val state: Workstate,
    val workload: String? = null,
    val message: String? = null
) : Serializable
