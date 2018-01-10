package net.bestia.zoneserver.guild;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.GuildDAO;
import net.bestia.model.dao.GuildMemberDAO;
import net.bestia.model.dao.GuildRankDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Guild;
import net.bestia.model.domain.GuildMember;
import net.bestia.model.domain.GuildRank;
import net.bestia.model.domain.PlayerBestia;

/**
 * Service to manipulate the players guild.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class GuildService {

	private static final Logger LOG = LoggerFactory.getLogger(GuildService.class);
	private int BASE_MAX_GUILD_MEMBERS = 24;

	private GuildDAO guildDao;
	// private GuildRankDAO rankDao;
	private GuildMemberDAO memberDao;
	private PlayerBestiaDAO playerBestiaDao;

	public GuildService() {

	}

	public void addPlayerToGuild(long playerBestiaId, int guildId) {
		final PlayerBestia pb = playerBestiaDao.findOne(playerBestiaId);
		if (pb == null) {
			return;
		}

		guildDao.findOne(guildId).ifPresent(guild -> {
			if (guild.getMembers().size() >= getMaxGuildMembers(guild)) {
				return;
			}

			final GuildMember gm = new GuildMember(guild, pb);
			memberDao.save(gm);
		});
	}

	/**
	 * The maximum number of guild members depends upon the guild skill
	 * SG_MAX_MEMBERS extension.
	 * 
	 * @param guild
	 * @return The calculated maximum number of guild members.
	 */
	public int getMaxGuildMembers(Guild guild) {
		if (guild == null) {
			return 0;
		}
		return BASE_MAX_GUILD_MEMBERS;
	}

	public boolean hasGuild(long playerBestiaId) {
		return memberDao.findByPlayerBestiaId(playerBestiaId) != null;
	}

	public Optional<Guild> getGuildOfPlayer(long playerBestiaId) {
		return memberDao.findByPlayerBestiaId(playerBestiaId).map(GuildMember::getGuild);
	}

	public int addExpTaxToGuild(long playerBestiaId, int earnedTotalExp) {
		return memberDao.findByPlayerBestiaId(playerBestiaId).map(member -> {
			final GuildRank rank = member.getRank();
			if (rank == null) {
				return 0;
			}

			final int taxExp = (int) Math.ceil(rank.getTaxRate() * earnedTotalExp);
			final Guild guild = member.getGuild();
			guild.addExp(taxExp);
			guildDao.save(guild);

			LOG.debug(String.format("Guild %d earned %d tax from pbid: %d", guild.getId(), taxExp, playerBestiaId));

			return taxExp;
		}).orElse(0);
	}

	public int getNeededNextLevelExp(Guild guild) {
		return guild.getLevel() * 1000;
	}

	public int getNeededNextLevelExp(int guildId) {
		return guildDao.findOne(guildId).map(guild -> getNeededNextLevelExp(guild)).orElse(0);
	}

	/*
	 * public Set<PlayerBestia> getGuildMembers(int guildId) {
	 * 
	 * }
	 */
	public Set<PlayerBestia> getGuildMembersFromPlayer(long playerBestiaId) {
		return getGuildOfPlayer(playerBestiaId).map(guild -> {
			return guild.getMembers().stream().map(GuildMember::getMember).filter(x -> x.getId() != playerBestiaId)
					.collect(Collectors.toSet());
		}).orElse(Collections.emptySet());
	}

	public int getSkillpointsToSpend(int guildId) {
		return 0;
	}
}
