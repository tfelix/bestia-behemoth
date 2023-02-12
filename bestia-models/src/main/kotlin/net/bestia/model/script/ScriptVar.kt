package net.bestia.model.script

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.model.AbstractEntity
import java.io.Serializable


/**
 * This will save script variables to the database. These variables can be used
 * for several purposes:
 *
 *  * NPC-Player Bestia Variables
 *  * NPC-Account Variables
 *  * NPC-Only Variables
 *  * Zone Global Variables
 *  * Server Global Variables
 * *NPC-Player Bestia** These variables will relate to bestias to certain NPCs/Entities.
 *
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "script_vars", indexes = [
  Index(name = "name_id_key", columnList = "script_key", unique = false)
])
data class ScriptVar(
    @Column(name = "script_key", nullable = false)
    val scriptKey: String,
    var data: String
) : AbstractEntity(), Serializable
