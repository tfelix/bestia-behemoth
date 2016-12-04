package net.bestia.server;

/**
 * Configuration names for the akka cluster. This hardcoded values are used to
 * build up the cluster.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class AkkaCluster {

	public static final String CLUSTER_NAME = "BehemothCluster";

	public static final String CLUSTER_PUBSUB_TOPIC = "behemoth";

	public static final String ROLE_WEB = "webserver";
	public static final String ROLE_ZONE = "zoneserver";

	private AkkaCluster() {
		// no op.
	}
}
