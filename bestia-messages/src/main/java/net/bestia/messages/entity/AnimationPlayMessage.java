package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.geometry.Point;

/**
 * Contains information to tell a client to play a animation. The animation
 * system is very flexible and allows the play of animation which can alter
 * existing entities or display certain effects. Only
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AnimationPlayMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "animation.play";

	@JsonProperty("an")
	private String animationName;

	@JsonProperty("teid")
	private long targetEntityId;

	@JsonProperty("op")
	private Point ownerPos;

	@JsonProperty("tp")
	private Point targetPos;

	/**
	 * Total duration of the animation. If this is set the animation framework
	 * has to shorten the animation duration accordingly.
	 */
	@JsonProperty("d")
	private int duration;
	
	/**
	 * For Jackson.
	 */
	public AnimationPlayMessage() {
		//no op.
	}

	public AnimationPlayMessage(long accountId, String animationName, long ownerEntityId) {
		this(accountId, animationName, ownerEntityId, 0);
	}

	public AnimationPlayMessage(long accountId, String animationName, long ownerEntityId, long targetEntityId) {
		super(ownerEntityId);

		this.animationName = Objects.requireNonNull(animationName);
		this.targetEntityId = targetEntityId;
	}

	public AnimationPlayMessage(long accountId, String animationName, Point ownerPos) {
		this(accountId, animationName, ownerPos, null);
	}

	public AnimationPlayMessage(long accountId, String animationName, Point ownerPos, Point targetPos) {
		super(accountId);

		if (targetPos == null && ownerPos == null) {
			throw new NullPointerException("Not both owner and target position can be null at the same time.");
		}

		this.animationName = Objects.requireNonNull(animationName);
		this.ownerPos = ownerPos;
		this.targetPos = targetPos;
	}

	public String getAnimationName() {
		return animationName;
	}

	public long getTargetEntityId() {
		return targetEntityId;
	}

	public Point getOwnerPos() {
		return ownerPos;
	}

	public Point getTargetPos() {
		return targetPos;
	}

	public int getDuration() {
		return duration;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {

		final String ownerPosStr = (ownerPos != null) ? ownerPos.toString() : "NULL";
		final String targetPosStr = (targetPos != null) ? targetPos.toString() : "NULL";

		return String.format(
				"AnimationPlayMessage[name: %s, ownerId: %d, targetId: %d, ownerPos: %s, targetPos: %s, duration: %d]",
				animationName,
				getEntityId(),
				targetEntityId,
				ownerPosStr,
				targetPosStr,
				duration);
	}
}
