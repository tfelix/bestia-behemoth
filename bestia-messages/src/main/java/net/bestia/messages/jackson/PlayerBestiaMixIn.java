package net.bestia.messages.jackson;

import java.sql.Date;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import net.bestia.model.PVPMode;
import net.bestia.model.Account;
import net.bestia.model.Location;
import net.bestia.model.StatusPoints;

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
