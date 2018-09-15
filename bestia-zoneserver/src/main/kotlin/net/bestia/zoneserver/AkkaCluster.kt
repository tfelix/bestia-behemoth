package net.bestia.zoneserver

/**
 * Configuration names for the akka cluster. This hardcoded values are used to
 * build up the cluster.
 *
 * It provides also methods for accessing the routing names of the actors.
 *
 * @author Thomas Felix
 */
object AkkaCluster {

  /**
   * Creates the node name of the akka cluster server.
   *
   * @param names
   * The names for the actor.
   * @return The actor path.
   */
  @JvmStatic
  fun getNodeName(vararg names: String): String {

    val joinedNames = names.joinToString("/")

    return String.format("/user/%s", joinedNames)
  }
}
