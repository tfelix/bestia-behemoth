package net.bestia.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.bestia.model.domain.Party;
import net.bestia.model.domain.PlayerBestia;

@SuppressWarnings("unused")
public class PartyDTO {
	
	private static class PartyMemberDTO {
		private final long playerBestiaId;
		private final long entityId;
		private final String name;
		private final String masterName;

		public PartyMemberDTO(PlayerBestia m) {
			
			this.playerBestiaId = m.getId();
			this.entityId = m.getEntityId();
			this.name = m.getName();
			this.masterName = m.getOwner().getMaster().getName();
		}

		public long getPlayerBestiaId() {
			return playerBestiaId;
		}

		public long getEntityId() {
			return entityId;
		}

		public String getName() {
			return name;
		}

		public String getMasterName() {
			return masterName;
		}
	}
	
	private final long id;
	private final String name;
	private final int maxMember;
	private final List<PartyMemberDTO> members = new ArrayList<>();

	public PartyDTO(Party party) {
		
		this.maxMember = Party.MAX_PARTY_MEMBER;
		this.name = party.getName();
		this.id = party.getId();
		
		final List<PartyMemberDTO> members = party.getMembers().stream().map(m -> {
			return new PartyMemberDTO(m);
		}).collect(Collectors.toList());
		
		members.addAll(members);
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getMaxMember() {
		return maxMember;
	}

	public List<PartyMemberDTO> getMembers() {
		return members;
	}
}
