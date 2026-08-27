extends Node

var version = "bclient/0.0.1-alpha"

# Login-server. Hosts the passkey pages the system browser opens and the endpoints
# [PasskeyLoginService] calls to exchange the resulting one-time code for a signed JWT.
var login_server_url = "http://localhost:8080"

# Zone-server's map tile endpoint. A different port from the game socket on purpose: tiles are bulk PNG
# traffic with their own caching, and the two are meant to be firewalled and scaled apart. See
# `zone-server/src/main/resources/application.yml`.
#
# An IPv4 literal rather than `localhost`, and it is load-bearing rather than cosmetic: `server.address` in
# that file pins Tomcat to 127.0.0.1, so nothing answers on `::1` at all. Godot resolves `localhost` to `::1`
# first and [HTTPRequest] tries exactly one resolved address, so the wrong spelling here would not fail -
# every tile request would pay a connect timeout, which reads as a map filling in one row every half minute
# rather than as a connection problem. [BnetSocket] is immune to the same trap because .NET's `TcpClient`
# tries every resolved address; [HTTPRequest] does not, so the map is the one place the spelling matters.
# This line and `server.address` move together or not at all.
var map_server_url = "http://127.0.0.1:8091"

# Distance (world units) within which the mouse's ground position must be from an
# entity for entity-target skills (AttackResource.target_type ENEMY/FRIENDLY) to
# visually snap onto it. See MouseStateSkillTargeting._update_entity_snap.
var skill_target_snap_distance: float = 1.5
