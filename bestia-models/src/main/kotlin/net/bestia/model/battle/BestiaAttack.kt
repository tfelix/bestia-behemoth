package net.bestia.model.battle

import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.model.AbstractEntity
import net.bestia.model.bestia.Bestia

/**
 * Lists the default attacks a Bestia will learn when it gains more
 * level.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "bestia_attacks")
data class BestiaAttack(
    @ManyToOne
    val attack: Attack,

    @ManyToOne
    val bestia: Bestia,

    /**
     * Returns the minimum level required until the bestia can use this attack.
     *
     * @return The minimum level until the bestia can use this attack.
     */
    val minLevel: Int = 0
) : AbstractEntity()