package net.bestia.server

/**
 * Configuration names for the akka cluster. This hardcoded values are used to
 * build up the cluster.
 *
 * It provides also methods for accessing the routing names of the actors.
 *
 * @author Thomas Felix
 */
object AkkaCluster {

  val CLUSTER_NAME = "BehemothCluster"
  val ROLE_WEB = "webserver"
  val ROLE_ZONE = "zoneserver"

  /**
   * Creates the node name of the akka cluster server.
   *
   * @param names
   * The names for the actor.
   * @return The actor path.
   */
  fun getNodeName(vararg names: String): String {

    val joinedNames = names.joinToString("/")

    return String.format("/user/%s", joinedNames)
  }
}
