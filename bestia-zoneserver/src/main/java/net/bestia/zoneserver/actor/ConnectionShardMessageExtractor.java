package net.bestia.zoneserver.actor;

import org.springframework.stereotype.Component;

import akka.cluster.sharding.ShardRegion;
import net.bestia.messages.JsonMessage;
import net.bestia.zoneserver.actor.connection.ConnectionActor;

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * client connection actors.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ConnectionShardMessageExtractor implements ShardRegion.MessageExtractor {
	
	private final static int NUMBER_OF_SHARDS = 10;

	@Override
	public String entityId(Object message) {
		if (message instanceof JsonMessage) {			
			return ConnectionActor.getActorName(((JsonMessage) message).getAccountId());
		} else {
			return null;
		}
	}

	@Override
	public Object entityMessage(Object message) {
		// Message is not wrapped. It IS the payload itself. No need to extract
		// anything.
		return message;
	}

	@Override
	public String shardId(Object message) {
		if (message instanceof JsonMessage) {
			// Use actor name to get the hash more evenly distributed.
			// Maybe not necessairy.
			final String name = ConnectionActor.getActorName(((JsonMessage) message).getAccountId());
			return String.valueOf(name.hashCode() % NUMBER_OF_SHARDS);
		} else {
			return null;
		}
	}

}
