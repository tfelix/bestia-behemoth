package net.bestia.server;

/**
 * Configuration names for the akka cluster. This hardcoded values are used to
 * build up the cluster.
 * 
 * It provides also methods for accessing the routing names of the actors.
 * 
 * @author Thomas Felix
 *
 */
public final class AkkaCluster {

	public static final String CLUSTER_NAME = "BehemothCluster";

	public static final String ROLE_WEB = "webserver";
	public static final String ROLE_ZONE = "zoneserver";

	private AkkaCluster() {
		// no op.
	}

	/**
	 * Creates the node name of the akka cluster server.
	 * 
	 * @param names
	 *            The names for the actor.
	 * @return The actor path.
	 */
	public static String getNodeName(String... names) {

		final String joinedNames = String.join("/", names);

		return String.format("/user/%s", joinedNames);
	}
}
