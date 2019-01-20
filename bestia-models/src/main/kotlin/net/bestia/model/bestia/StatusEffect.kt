package net.bestia.model.bestia

import net.bestia.model.AbstractEntity
import java.io.Serializable

import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class StatusEffect(
    val datebaseName: String,
    /**
     * Flag if the status effect is transmitted to the client and shown inside
     * the client GUI.
     *
     * @return TRUE if the status effect should be visible to the client.
     */
    val isClientVisible: Boolean = true
) : AbstractEntity(), Serializable
