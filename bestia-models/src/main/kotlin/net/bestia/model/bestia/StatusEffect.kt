package net.bestia.model.bestia

import net.bestia.model.AbstractEntity
import java.io.Serializable

import javax.persistence.Entity

@Entity
data class StatusEffect(
    val datebaseName: String,
    /**
     * Flag if the status effect is transmitted to the client and shown inside
     * the client GUI. This is not the case for every status effect. If for example an equipment
     * modifies the bestia it will not be transferred to the client.
     *
     * @return TRUE if the status effect should be visible to the client.
     */
    val isClientVisible: Boolean = true
) : AbstractEntity(), Serializable
