package net.bestia.model.dto

import java.util.ArrayList
import java.util.stream.Collectors

import net.bestia.model.domain.Party
import net.bestia.model.domain.PlayerBestia

class PartyDTO(party: Party) {

  val id: Long
  val name: String
  val maxMember: Int
  val members: List<PartyMemberDTO> = ArrayList()

  private class PartyMemberDTO(m: PlayerBestia) {
    val playerBestiaId: Long
    val entityId: Long
    val name: String
    val masterName: String

    init {

      this.playerBestiaId = m.id
      this.entityId = m.entityId
      this.name = m.name
      this.masterName = m.owner.getMaster().getName()
    }
  }

  init {

    this.maxMember = Party.MAX_PARTY_MEMBER
    this.name = party.name
    this.id = party.id

    val members = party.members.stream().map { m -> PartyMemberDTO(m) }.collect<List<PartyMemberDTO>, Any>(Collectors.toList())

    members.addAll(members)
  }
}
