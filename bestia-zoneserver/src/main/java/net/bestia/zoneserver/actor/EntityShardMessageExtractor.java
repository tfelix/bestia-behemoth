package net.bestia.zoneserver.actor;

import akka.cluster.sharding.ShardRegion;
import net.bestia.messages.entity.ToEntityEnvelope;

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * entity actors.
 * 
 * @author Thomas Felix
 *
 */
public class EntityShardMessageExtractor implements ShardRegion.MessageExtractor {

	private final static int NUMBER_OF_SHARDS = 10;

	@Override
	public String entityId(Object message) {
		if (message instanceof ToEntityEnvelope) {
			return String.valueOf(((ToEntityEnvelope) message).getEntityId());
		} else {
			return null;
		}
	}

	@Override
	public Object entityMessage(Object message) {
		// It IS the payload itself. No need to extract anything.
		return message;
	}

	@Override
	public String shardId(Object message) {
		if (message instanceof ToEntityEnvelope) {
			final long id = ((ToEntityEnvelope) message).getEntityId();
			return String.valueOf(id % NUMBER_OF_SHARDS);
		} else {
			return null;
		}
	}
}