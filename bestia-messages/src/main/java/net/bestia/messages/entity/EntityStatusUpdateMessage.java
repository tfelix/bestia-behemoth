package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.entity.StatusBasedValues;

/**
 * Sends updates regarding to an entities status values towards a player. The
 * player client can use this information to update a bestias status values.
 * 
 * @author Thomas Felix
 *
 */
public class EntityStatusUpdateMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "entity.status";

	@JsonProperty("sp")
	private final StatusPoints statusPoints;

	@JsonProperty("sv")
	private final ConditionValues statusValues;

	@JsonProperty("osp")
	private final StatusPoints unmodifiedStatusPoints;

	@JsonProperty("sbv")
	private final StatusBasedValues statusBasedValues;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	private EntityStatusUpdateMessage() {
		super(0, 0);

		this.statusBasedValues = null;
		this.unmodifiedStatusPoints = null;
		this.statusValues = null;
		this.statusPoints = null;

	}

	public EntityStatusUpdateMessage(
			long accId,
			long entityId,
			StatusPoints statusPoints,
			StatusPoints unmodifiedStatusPoints,
			ConditionValues statusValues,
			StatusBasedValues statusBasedValues) {
		super(accId, entityId);

		this.statusBasedValues = Objects.requireNonNull(statusBasedValues);
		this.unmodifiedStatusPoints = Objects.requireNonNull(unmodifiedStatusPoints);
		this.statusValues = Objects.requireNonNull(statusValues);
		this.statusPoints = Objects.requireNonNull(statusPoints);

	}

	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public StatusBasedValues getStatusBasedValues() {
		return statusBasedValues;
	}

	public ConditionValues getStatusValues() {
		return statusValues;
	}

	public StatusPoints getUnmodifiedStatusPoints() {
		return unmodifiedStatusPoints;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new EntityStatusUpdateMessage(accountId,
				getEntityId(),
				getStatusPoints(),
				getUnmodifiedStatusPoints(), 
				getStatusValues(), 
				getStatusBasedValues());
	}

	@Override
	public String toString() {
		return String.format("EntityStatusUpdate[eid: %d, sp: %s, sv: %s, sbv: %s]",
				getEntityId(),
				getStatusValues(),
				getStatusValues(),
				getStatusBasedValues());
	}
}
