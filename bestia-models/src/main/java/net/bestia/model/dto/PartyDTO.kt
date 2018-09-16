package net.bestia.model.dto

data class PartyDTO(
        val id: Long,
        val name: String,
        val maxMember: Int,
        val members: List<PartyMemberDTO>
)

