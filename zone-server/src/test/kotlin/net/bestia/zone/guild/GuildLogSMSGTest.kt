package net.bestia.zone.guild

import net.bestia.zone.guild.persistence.GuildLogKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GuildLogSMSGTest {

  @Test
  fun `every log kind has its proto value`() {
    GuildLogKind.entries.forEach { kind ->
      assertEquals("GUILD_LOG_KIND_${kind.name}", GuildLogSMSG.toProto(kind).name)
    }
  }
}
