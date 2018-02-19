package bestia.entity.component;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds the data to correlate an entity to a guild.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.ALL)
public class GuildComponent extends Component {
	
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("gid")
	private int guildId;
	
	@JsonProperty("gn")
	private String guildName;
	
	@JsonProperty("e")
	private String emblem;
	
	@JsonProperty("rn")
	private String rankName;
	
	public GuildComponent(long id) {
		super(id);
		// no op.
	}
	
	public int getGuildId() {
		return guildId;
	}

	public void setGuildId(int guildId) {
		this.guildId = guildId;
	}

	public String getGuildName() {
		return guildName;
	}

	public void setGuildName(String guildName) {
		this.guildName = guildName;
	}

	public String getEmblem() {
		return emblem;
	}

	public void setEmblem(String emblem) {
		this.emblem = emblem;
	}

	public String getRankName() {
		return rankName;
	}

	public void setRankName(String rankName) {
		this.rankName = rankName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(guildId, guildName, emblem, rankName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		GuildComponent other = (GuildComponent) obj;
		if (emblem == null) {
			if (other.emblem != null)
				return false;
		} else if (!emblem.equals(other.emblem))
			return false;
		if (guildId != other.guildId)
			return false;
		if (guildName == null) {
			if (other.guildName != null)
				return false;
		} else if (!guildName.equals(other.guildName))
			return false;
		if (rankName == null) {
			if (other.rankName != null)
				return false;
		} else if (!rankName.equals(other.rankName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("GuildComponent[id: %d, %s]", getId(), getGuildId());
	}
}
