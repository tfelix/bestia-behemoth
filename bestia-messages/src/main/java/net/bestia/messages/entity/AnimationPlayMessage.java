package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.Position;

/**
 * Contains information to tell a client to play a animation. The animation
 * system is very flexible and allows the play of animation which can alter
 * existing entities or display certain effects. Only
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AnimationPlayMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "animation.play";

	@JsonProperty("an")
	private String animationName;

	@JsonProperty("oeid")
	private long ownerEntityId;

	@JsonProperty("teid")
	private long targetEntityId;

	@JsonProperty("op")
	private Position ownerPos;

	@JsonProperty("tp")
	private Position targetPos;

	/**
	 * Total duration of the animation. If this is set the animation framework
	 * has to shorten the animation duration accordingly.
	 */
	@JsonProperty("d")
	private int duration;

	public AnimationPlayMessage(long accountId, String animationName, long ownerEntityId) {
		this(accountId, animationName, ownerEntityId, 0);
	}

	public AnimationPlayMessage(long accountId, String animationName, long ownerEntityId, long targetEntityId) {
		super(accountId);

		this.animationName = Objects.requireNonNull(animationName);
		this.ownerEntityId = ownerEntityId;
		this.targetEntityId = targetEntityId;
	}

	public AnimationPlayMessage(long accountId, String animationName, Position ownerPos) {
		this(accountId, animationName, ownerPos, null);
	}

	public AnimationPlayMessage(long accountId, String animationName, Position ownerPos, Position targetPos) {
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

	public long getOwnerEntityId() {
		return ownerEntityId;
	}

	public long getTargetEntityId() {
		return targetEntityId;
	}

	public Position getOwnerPos() {
		return ownerPos;
	}

	public Position getTargetPos() {
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
				ownerEntityId,
				targetEntityId,
				ownerPosStr,
				targetPosStr,
				duration);
	}
}
