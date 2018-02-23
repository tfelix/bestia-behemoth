package net.bestia.zoneserver.actor;

import org.springframework.stereotype.Component;

import akka.cluster.sharding.ShardRegion;
import net.bestia.messages.AccountMessage;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;

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
		if (message instanceof AccountMessage) {
			final long accId = ((AccountMessage) message).getAccountId();
			
			if(accId <= 0) {
				return null;
			}
			
			return ClientConnectionActor.getActorName(accId);
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
		if (message instanceof AccountMessage) {
			// Use actor name to get the hash more evenly distributed.
			// Maybe not necessairy.
			final long accId = ((AccountMessage) message).getAccountId();
			
			if(accId <= 0) {
				return null;
			}
			
			final String name = ClientConnectionActor.getActorName(accId);
			return String.valueOf(name.hashCode() % NUMBER_OF_SHARDS);
		} else {
			return null;
		}
	}
}