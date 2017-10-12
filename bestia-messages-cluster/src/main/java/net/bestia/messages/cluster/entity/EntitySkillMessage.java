package net.bestia.messages.cluster.entity;

import java.io.Serializable;
import java.util.Objects;

import net.bestia.model.geometry.Point;

/**
 * Entity attack message which is issued if an entity used an attack/skill
 * against another entity or against a coordinate on the ground.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySkillMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Point targetPostion;
	private final long targetEntityId;
	private final long sourceEntityId;
	private final int attackId;

	public EntitySkillMessage(long sourceEntityId, int attackId, long targetEntityId) {

		this.targetPostion = null;
		this.targetEntityId = targetEntityId;
		this.sourceEntityId = sourceEntityId;
		this.attackId = attackId;
	}

	public EntitySkillMessage(long sourceEntityId, int attackId, Point targetPosition) {

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
