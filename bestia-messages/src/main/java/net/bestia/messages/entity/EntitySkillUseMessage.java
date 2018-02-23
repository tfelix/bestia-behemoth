package net.bestia.messages.entity;

import java.io.Serializable;
import java.util.Objects;

import bestia.model.geometry.Point;

/**
 * Entity attack message which is issued if an entity used an attack/skill
 * against another entity or against a coordinate on the ground.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySkillUseMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Point targetPostion;
	private final long targetEntityId;
	private final long sourceEntityId;
	private final int attackId;

	public EntitySkillUseMessage(long sourceEntityId, int attackId, long targetEntityId) {

		this.targetPostion = null;
		this.targetEntityId = targetEntityId;
		this.sourceEntityId = sourceEntityId;
		this.attackId = attackId;
	}

	public EntitySkillUseMessage(long sourceEntityId, int attackId, Point targetPosition) {

		this.targetPostion = Objects.requireNonNull(targetPosition);
		this.targetEntityId = 0;
		this.sourceEntityId = sourceEntityId;
		this.attackId = attackId;
	}

	public Point getTargetPostion() {
		return targetPostion;
	}

	public long getTargetEntityId() {
		return targetEntityId;
	}

	public long getSourceEntityId() {
		return sourceEntityId;
	}

	public int getAttackId() {
		return attackId;
	}
}
