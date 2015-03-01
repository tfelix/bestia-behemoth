package net.bestia.core.message.jackson;

import java.sql.Date;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import net.bestia.core.game.battle.PVPMode;
import net.bestia.core.game.model.Account;
import net.bestia.core.game.model.Location;
import net.bestia.core.game.model.StatusPoints;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract class PlayerBestiaMixIn {
	@JsonProperty("m")
	abstract String getMapDbName();
	
	private int exp;
	private String name;
	private Date traveltime;
	@AttributeOverrides({
			@AttributeOverride(name = "mapDbName", column = @Column(name = "saveMapDbName")),
			@AttributeOverride(name = "x", column = @Column(name = "saveX")),
			@AttributeOverride(name = "y", column = @Column(name = "saveY")), })
	@Embedded
	private Location savePosition;

	@Embedded
	private Location currentPosition;
	@Enumerated(EnumType.STRING)
	private PVPMode pvpMode;
	@ManyToOne
	@JoinColumn(name="account_id")
	private Account owner;
	@Embedded
	private StatusPoints individualValue;
	@Embedded
	private StatusPoints effortValue;
}
