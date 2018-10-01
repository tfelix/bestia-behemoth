package net.bestia.messages.ui

import java.io.Serializable

/**
 * These nodes will be read by the client software which will then decide how to
 * display this information.
 *
 * @author Thomas Felix
 */
data class DialogNode(
    val type: DialogAction,
    val data: String
) : Serializable
