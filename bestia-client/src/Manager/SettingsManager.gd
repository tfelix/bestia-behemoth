extends Node

var version = "bclient/0.0.1-alpha"

# Login-server used to exchange the static development credentials for a signed JWT.
var login_server_url = "http://localhost:8080"

# Static development credentials. The login server seeds matching accounts (see DevAccountSeeder).
# 'admin' -> SUPER_GM, 'user' -> USER.
var dev_username = "admin"
var dev_static_token = "dev-admin-token"

# Distance (world units) within which the mouse's ground position must be from an
# entity for entity-target skills (AttackResource.target_type ENEMY/FRIENDLY) to
# visually snap onto it. See MouseStateSkillTargeting._update_entity_snap.
var skill_target_snap_distance: float = 1.5
