package net.bestia.zoneserver.actor;

import akka.cluster.sharding.ShardRegion;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.ClientToMessageEnvelope;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;
import org.springframework.stereotype.Component;

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * client connection actors.
 *
 * @author Thomas Felix
 */
@Component
public class ConnectionShardMessageExtractor implements ShardRegion.MessageExtractor {

	private final static int NUMBER_OF_SHARDS = 10;

	@Override
	public String entityId(Object message) {
		if (message instanceof AccountMessage) {
			final long accId = ((AccountMessage) message).getAccountId();
			return getActorName(accId);
		} else if (message instanceof ClientToMessageEnvelope) {
			final long accId = ((ClientToMessageEnvelope) message).getClientAccountId();
			return getActorName(accId);
		} else {
			return null;
		}
	}

	private String getActorName(long accId) {
		if (accId <= 0) {
			return null;
		}
		return ClientConnectionActor.getActorName(accId);
	}

	@Override
	public Object entityMessage(Object message) {
		// The message should be resend as is without altering it.
		return message;
	}

	@Override
	public String shardId(Object message) {
		if (message instanceof AccountMessage) {
			// Use actor name to get the hash more evenly distributed.
			// Maybe not necessairy.
			final long accId = ((AccountMessage) message).getAccountId();
			return getShardId(accId);
		} else if (message instanceof ClientToMessageEnvelope) {
			final long accId = ((ClientToMessageEnvelope) message).getClientAccountId();
			return getShardId(accId);
		} else {
			return null;
		}
	}

	private String getShardId(long accId) {
		if (accId <= 0) {
			return null;
		}
		final String name = ClientConnectionActor.getActorName(accId);
		return String.valueOf(name.hashCode() % NUMBER_OF_SHARDS);
	}
}