package net.bestia.model.party

data class PartyDTO(
    val id: Long,
    val name: String,
    val maxMember: Int,
    val members: List<PartyMemberDTO>
)

