extends Node

var version = "bclient/0.0.1-alpha"

# Login-server. Hosts the passkey pages the system browser opens and the endpoints
# [PasskeyLoginService] calls to exchange the resulting one-time code for a signed JWT.
var login_server_url = "http://localhost:8080"

# Zone-server's map tile endpoint. A different port from the game socket on purpose: tiles are bulk PNG
# traffic with their own caching, and the two are meant to be firewalled and scaled apart. See
# `zone-server/src/main/resources/application.yml`.
#
# An IPv4 literal rather than `localhost`, and that is not cosmetic. [HTTPRequest] resolves one address and
# Godot answers `localhost` with `::1` first, so against a server not listening on IPv6 every single tile
# request pays a thirty-second failover - which reads as a map that fills in one row of tiles every half
# minute, not as a connection problem. [BnetSocket] is immune to the same trap because .NET's `TcpClient`
# tries every resolved address; [HTTPRequest] does not, so the map is the one place the spelling matters.
var map_server_url = "http://127.0.0.1:8091"

# Distance (world units) within which the mouse's ground position must be from an
# entity for entity-target skills (AttackResource.target_type ENEMY/FRIENDLY) to
# visually snap onto it. See MouseStateSkillTargeting._update_entity_snap.
var skill_target_snap_distance: float = 1.5
