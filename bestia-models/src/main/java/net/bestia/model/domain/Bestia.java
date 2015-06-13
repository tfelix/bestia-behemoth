package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Bestia implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@JsonProperty("bdbn")
	private String databaseName;
	@Enumerated(EnumType.STRING)
	@JsonProperty("ele")
	private Element element;
	@JsonProperty("img")
	private String image;
	@JsonProperty("s")
	private String sprite;

	public String getDatabaseName() {
		return databaseName;
	}

	public String getImage() {
		return image;
	}

	public String getSprite() {
		return sprite;
	}
	
	public Element getElement() {
		return element;
	}
}
