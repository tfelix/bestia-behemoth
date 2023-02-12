package net.bestia.model.bestia

import jakarta.persistence.*
import net.bestia.model.AbstractEntity
import net.bestia.model.battle.Element

@Entity
@Table(name = "bestias")
class Bestia(
    /**
     * The database name.
     *
     * @return The database name.
     */
    @Column(name = "bestia_db_name", unique = true, nullable = false, length = 100)
    val databaseName: String,

    @Column(name = "default_name", nullable = false, length = 100)
    var defaultName: String,

    @Enumerated(EnumType.STRING)
    val element: Element,
    val mesh: String,

    /**
     * Experience points gained if bestia was defeated.
     */
    val expGained: Int,

    /**
     * Returns the type of the bestia.
     */
    @Enumerated(EnumType.STRING)
    val type: BestiaType,
    val level: Int,
    val isBoss: Boolean = false,

    @Enumerated(EnumType.STRING)
    val temperatureKind: TemperatureKind = TemperatureKind.MEDIUM,

    @Embedded
    val baseValues: BaseValues,

    /**
     * Script which will be attached to this bestia.
     */
    val scriptExec: String? = null
) : AbstractEntity() {

  override fun toString(): String {
    return "Bestia[db: $databaseName, id: $id, level: $level]"
  }
}
