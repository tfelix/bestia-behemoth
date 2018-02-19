package bestia.model.domain;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A rank in a guild allows the leader to give certain member different
 * rights.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "guild_ranks")
public class GuildRank {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	
	@Column(nullable=false)
	private String name;
	private float taxRate;
	private boolean canEditMember;
	private boolean canEditRanks;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "GUILD_ID", nullable = false)
	@JsonIgnore
	private Guild guild;
	
	public GuildRank() {
		
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public float getTaxRate() {
		return taxRate;
	}

	public void setTaxRate(float taxRate) {
		if(taxRate < 0) {
			taxRate = 0;
		}
		if(taxRate > 0.8) {
			taxRate = 0.8f;
		}
		this.taxRate = taxRate;
	}

	public boolean canEditMember() {
		return canEditMember;
	}
	
	public void canEditMember(boolean flag) {
		canEditMember = flag;
	}
	
	public boolean canEditRanks() {
		return canEditRanks;
	}
	
	public void canEditRanks(boolean flag) {
		canEditRanks = flag;
	}
	
	@Override
	public String toString() {
		return String.format("Rank[id: %d, name: %s, i: %b, r: %b]", 
				id,
				name,
				canEditMember, 
				canEditRanks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GuildRank other = (GuildRank) obj;
		if (id != other.id)
			return false;
		return true;
	}
}
