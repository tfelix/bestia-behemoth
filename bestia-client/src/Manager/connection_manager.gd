extends Node
## Keeps the connection to the server and is responsible for scene changing
##
## Orchestrates login procedure and handles connection state and message translation
## and filtering to the various subsystems.
##

signal master_info_received(master: MasterSMSG)
signal entity_received(message: EntitySMSG)
signal self_received(message: SelfSMSG)
## Every bestia the selected master owns, whole, whenever that set changes.
signal owned_bestias_received(message: OwnedBestiasSMSG)
## The server confirmed which entity the player now controls.
signal active_entity_received(message: ActiveEntitySMSG)
## One of the player's own entities ended a journey, arrived or not.
signal journey_ended(message: JourneyEndedSMSG)
## Every route the selected master keeps, after asking or after one changed.
signal saved_routes_received(message: SavedRoutesSMSG)
## A trap was sprung near us and the catch was rolled.
signal bestia_capture_received(message: BestiaCaptureSMSG)
signal chat_received(message: ChatSMSG)
## Emitted when the server wants a dialog shown. Account-scoped, not tied to an entity, so it is
## deliberately kept out of [signal entity_received]. Consumed by the DialogManager autoload.
signal dialog_received(message: DialogSMSG)
## Emitted when the server answers a crafting-skill activation with what can be made here. Account-scoped
## for the reason [signal dialog_received] is: it opens a window, and a window belongs to a client.
signal craftable_recipes_received(message: CraftableRecipesSMSG)
signal discovery_result_received(message: DiscoveryResultSMSG)
## Emitted when a townsperson says something and offers options back. Account-scoped for the reason
## [signal dialog_received] is: it opens a window, and a window belongs to a client rather than to an
## entity - the speaker travels inside the message as metadata.
signal conversation_received(message: ConversationSMSG)
signal operation_success(message: OperationSuccess)
signal operation_error(message: OperationError)
## Emitted when another player asks us to trade. Account-scoped for the reason [signal dialog_received] is:
## it raises a prompt, and a prompt belongs to a client rather than to an entity.
signal trade_request_received(message: TradeRequestSMSG)
## Emitted on every change to an open trade. A full snapshot each time - it opens the window, updates it and
## closes it, so the trade window never has to reconstruct state from a sequence.
signal trade_state_received(message: TradeStateSMSG)
## Emitted whenever a merchant's counter is (re)drawn. A full snapshot each time - it opens the window and
## updates it, and it arrives again after every trade so the price shown is the price that was just moved.
signal shop_offer_received(message: ShopOfferSMSG)
signal auction_house_opened(message: AuctionHouseOpenSMSG)
signal auction_listings_received(message: AuctionListingsSMSG)
signal auction_claims_received(message: AuctionClaimsSMSG)
signal auction_purchased(message: AuctionPurchasedSMSG)
## Every party message: the roster, an invitation, the party ending for us. The party window listens.
signal party_message_received(message: PartySMSG)
## Every guild message: the guild, an invitation, leaving it. The guild window listens.
signal guild_message_received(message: GuildSMSG)
## The selected master's Bestia Compendium, on request and after every new sighting or catch.
signal compendium_received(message: CompendiumSMSG)
## Emitted whenever the server re-syncs the pending logout countdown (seconds until despawn).
signal logout_countdown_received(remaining_seconds: float)
## Emitted when the server aborts a pending logout (player moved / used a skill / took damage).
signal logout_cancelled()
## Emitted once the system browser has been opened for a passkey login, so the UI can offer a way out
## of a wait that is otherwise entirely off-screen.
signal passkey_browser_opened()
## Emitted when a passkey login ends without a token. Carries a message meant for the player.
signal passkey_login_failed(reason: String)
## Emitted when a stored session could not be resumed and has been discarded, so the browser is the only way
## left in. Not an error: this is what the end of a token's life looks like.
signal session_resume_unavailable()

enum ConnectionState {DISCONNECTED, CONNECTED_NOT_AUTHED, CONNECTED_AUTHED}
enum ConnectionError {NO_ERROR, ZONE_CONNECTION_LOST, PROTOCOL_MISMATCH}

@onready var _socket = $BnetSocket
@onready var _passkey_login = $PasskeyLogin
@onready var _ping_timer: Timer = $PingTimer

## Watches the terrain stream and drives SceneManager's loading screen while the world arrives.
##
## Here rather than in Game.tscn because it has to outlive it: the watch starts before the game scene
## exists, and a teleport restarts it with no scene change at all.
@onready var world_load: WorldLoadWatcher = $WorldLoadWatcher


var Authentication = load("res://Bnet/Message/Authentication.cs")
var GetMasterCMSG = load("res://Bnet/Message/Master/GetMasterCMSG.cs")
var GetSelfCMSG = load("res://Bnet/Message/Master/GetSelfCMSG.cs")
var SelectMasterCMSG = load("res://Bnet/Message/Master/SelectMasterCMSG.cs")
var CreateMasterCMSG = load("res://Bnet/Message/Master/CreateMasterCMSG.cs")
var DeleteMasterCMSG = load("res://Bnet/Message/Master/DeleteMasterCMSG.cs")
var AttackEntityCMSG = load("res://Bnet/Message/Entity/AttackEntityCMSG.cs")
var MoveEntityCMSG = load("res://Bnet/Message/Entity/MoveEntityCMSG.cs")
var RequestSavedRoutesCMSG = load("res://Bnet/Message/Travel/RequestSavedRoutesCMSG.cs")
var SaveRouteCMSG = load("res://Bnet/Message/Travel/SaveRouteCMSG.cs")
var RenameRouteCMSG = load("res://Bnet/Message/Travel/RenameRouteCMSG.cs")
var DeleteRouteCMSG = load("res://Bnet/Message/Travel/DeleteRouteCMSG.cs")
var SelectActiveEntityCMSG = load("res://Bnet/Message/Entity/SelectActiveEntityCMSG.cs")
var GetInventoryCMSG = load("res://Bnet/Message/Inventory/GetInventoryCMSG.cs")
var GetSkillsCMSG = load("res://Bnet/Message/Master/GetSkillsCMSG.cs")
var ActivateSkillCMSG = load("res://Bnet/Message/Master/ActivateSkillCMSG.cs")
var InvestSkillPointCMSG = load("res://Bnet/Message/Master/InvestSkillPointCMSG.cs")
var UseItemCMSG = load("res://Bnet/Message/Inventory/UseItemCMSG.cs")
## Not a message: the argument bag a scripted item fills before one of the above is sent.
var ScriptArgsCls = load("res://Bnet/Message/ScriptArgs.cs")
var DropItemCMSG = load("res://Bnet/Message/Inventory/DropItemCMSG.cs")
var LootItemCMSG = load("res://Bnet/Message/Inventory/LootItemCMSG.cs")
var EquipItemCMSG = load("res://Bnet/Message/Inventory/EquipItemCMSG.cs")
var UnequipItemCMSG = load("res://Bnet/Message/Inventory/UnequipItemCMSG.cs")
var RequestLogoutCMSG = load("res://Bnet/Message/System/RequestLogoutCMSG.cs")
var RespawnCMSG = load("res://Bnet/Message/System/RespawnCMSG.cs")
var InteractCMSG = load("res://Bnet/Message/System/InteractCMSG.cs")
var ConversationChoiceCMSG = load("res://Bnet/Message/System/ConversationChoiceCMSG.cs")
var CollectPropCMSG = load("res://Bnet/Message/Map/CollectPropCMSG.cs")
var InteractEntityCMSG = load("res://Bnet/Message/Map/InteractEntityCMSG.cs")
var CraftItemCMSG = load("res://Bnet/Message/Crafting/CraftItemCMSG.cs")
var DiscoverRecipeCMSG = load("res://Bnet/Message/Crafting/DiscoverRecipeCMSG.cs")
var CancelCraftCMSG = load("res://Bnet/Message/Crafting/CancelCraftCMSG.cs")
var RequestTradeCMSG = load("res://Bnet/Message/Trade/RequestTradeCMSG.cs")
var AnswerTradeRequestCMSG = load("res://Bnet/Message/Trade/AnswerTradeRequestCMSG.cs")
var OfferTradeItemCMSG = load("res://Bnet/Message/Trade/OfferTradeItemCMSG.cs")
var RetractTradeItemCMSG = load("res://Bnet/Message/Trade/RetractTradeItemCMSG.cs")
var SetTradeLockCMSG = load("res://Bnet/Message/Trade/SetTradeLockCMSG.cs")
var ConfirmTradeCMSG = load("res://Bnet/Message/Trade/ConfirmTradeCMSG.cs")
var CancelTradeCMSG = load("res://Bnet/Message/Trade/CancelTradeCMSG.cs")
var OpenShopCMSG = load("res://Bnet/Message/Shop/OpenShopCMSG.cs")
var ShopTradeCMSG = load("res://Bnet/Message/Shop/ShopTradeCMSG.cs")
var SearchAuctionsCMSG = load("res://Bnet/Message/Auction/SearchAuctionsCMSG.cs")
var BuyAuctionListingCMSG = load("res://Bnet/Message/Auction/BuyAuctionListingCMSG.cs")
var RequestPartyInfoCMSG = load("res://Bnet/Message/Party/RequestPartyInfoCMSG.cs")
var CreatePartyCMSG = load("res://Bnet/Message/Party/CreatePartyCMSG.cs")
var RequestPartyInvitationCMSG = load("res://Bnet/Message/Party/RequestPartyInvitationCMSG.cs")
var AcceptPartyInviteCMSG = load("res://Bnet/Message/Party/AcceptPartyInviteCMSG.cs")
var DeclinePartyInviteCMSG = load("res://Bnet/Message/Party/DeclinePartyInviteCMSG.cs")
var LeavePartyCMSG = load("res://Bnet/Message/Party/LeavePartyCMSG.cs")
var RemovePartyMemberCMSG = load("res://Bnet/Message/Party/RemovePartyMemberCMSG.cs")
var SetPartyExpShareCMSG = load("res://Bnet/Message/Party/SetPartyExpShareCMSG.cs")
var RequestGuildInfoCMSG = load("res://Bnet/Message/Guild/RequestGuildInfoCMSG.cs")
var RequestCompendiumCMSG = load("res://Bnet/Message/Compendium/RequestCompendiumCMSG.cs")
var CreateGuildCMSG = load("res://Bnet/Message/Guild/CreateGuildCMSG.cs")
var InviteToGuildCMSG = load("res://Bnet/Message/Guild/InviteToGuildCMSG.cs")
var AnswerGuildInviteCMSG = load("res://Bnet/Message/Guild/AnswerGuildInviteCMSG.cs")
var LeaveGuildCMSG = load("res://Bnet/Message/Guild/LeaveGuildCMSG.cs")
var KickGuildMemberCMSG = load("res://Bnet/Message/Guild/KickGuildMemberCMSG.cs")
var SetGuildMemberRankCMSG = load("res://Bnet/Message/Guild/SetGuildMemberRankCMSG.cs")
var SaveGuildRankCMSG = load("res://Bnet/Message/Guild/SaveGuildRankCMSG.cs")
var DeleteGuildRankCMSG = load("res://Bnet/Message/Guild/DeleteGuildRankCMSG.cs")
var InvestGuildSkillCMSG = load("res://Bnet/Message/Guild/InvestGuildSkillCMSG.cs")
var DisbandGuildCMSG = load("res://Bnet/Message/Guild/DisbandGuildCMSG.cs")
var RequestGuildLogCMSG = load("res://Bnet/Message/Guild/RequestGuildLogCMSG.cs")
var SetGuildMotdCMSG = load("res://Bnet/Message/Guild/SetGuildMotdCMSG.cs")
var SetGuildEmblemCMSG = load("res://Bnet/Message/Guild/SetGuildEmblemCMSG.cs")
var TransferGuildLeadershipCMSG = load("res://Bnet/Message/Guild/TransferGuildLeadershipCMSG.cs")
var LiftGuildExpulsionCMSG = load("res://Bnet/Message/Guild/LiftGuildExpulsionCMSG.cs")
var Ping = load("res://Bnet/Message/Ping.cs")
var ChunkStreamManagerScript = load("res://Game/World/ChunkStreamManager.cs")
var WeatherStateScript = load("res://Game/World/WeatherState.cs")
var WorldClockScript = load("res://Game/World/WorldClock.cs")

var _connection_state : ConnectionState = ConnectionState.DISCONNECTED

## Smoothed round trip to the server, in milliseconds, or -1 before the first measurement.
##
## Measured without any help from the protocol: Ping and Pong carry nothing, and they do not need to, because
## the client is the one that knows when it sent the ping. One ping is in flight at a time, so a single send
## time is enough to attribute the answer - a stale pong with nothing outstanding is ignored rather than
## timed against the wrong ping. [member rtt_ms] is the raw last sample this is smoothed from.
##
## Exponentially smoothed, because a single sample is mostly scheduler noise. [member latency_jitter_msec] is
## the mean deviation from that average, which is the number that says whether a walk should look smooth.
var latency_msec: float = -1.0
var latency_jitter_msec: float = 0.0

const _LATENCY_SMOOTHING: float = 0.2

## Client-side terrain streaming: reconciles chunk manifests, decodes payloads, applies patches.
##
## Created here in code rather than added to ConnectionManager.tscn, because a scene node needs a
## resource uid that only the Godot editor can mint. It subscribes to BnetSocket's MessageReceived
## itself, so no signal wiring in the scene is needed.
##
## Note that a signal fans out to every listener, so terrain messages DO also reach
## _on_bnet_socket_message_received below - which is why that handler has a MapSMSG branch. It went
## without one for a while and reported every chunk as unidentified.
var chunk_stream: Node = null

## Integrates WeatherSMSG into the global shader parameters the terrain shader reads.
##
## Created here for the same reason chunk_stream is, and it also has to outlive the Game scene: weather arrives
## whenever the server sends it, which includes while the player is choosing a master.
var weather: Node = null

## The world calendar, anchored by WorldInfoSMSG and run forward locally.
##
## Created here for the reason weather is, and it also has to outlive the Game scene: the world info arrives
## on authentication, which is before a master has even been picked. The HUD clock connects to its
## [signal WorldClock.TimeChanged] and calls PublishNow() once, because by then the anchor is long since set.
var world_clock: Node = null

# Signed JWT obtained from the login server, sent to the zone during the auth handshake.
var _login_token: String = ""

# Credential for this client's REST calls, handed over by the zone in AuthenticationSuccess.
var _http_ticket: String = ""

var last_connection_error: ConnectionError = ConnectionError.NO_ERROR

## Round trip of the last answered ping, or -1 before the first pong.
var rtt_ms: int = -1
var _ping_sent_at_ms: int = -1

var selected_master_info: MasterInfo = null

# Set while we deliberately drop the connection (logout to main menu) so the socket-closed handler
# routes to the main menu instead of the "connection lost" screen.
var _intentional_disconnect: bool = false


func _ready() -> void:
	chunk_stream = ChunkStreamManagerScript.new()
	chunk_stream.name = "ChunkStreamManager"
	add_child(chunk_stream)
	chunk_stream.Attach(_socket)
	# Terrain streaming is network traffic, so it answers to the same setting the socket does rather
	# than to a switch of its own.
	chunk_stream.VerboseChunkLog = SettingsManager.get_instance().net_log_mode == "detail"

	weather = WeatherStateScript.new()
	weather.name = "WeatherState"
	add_child(weather)
	weather.Attach(_socket)
	chunk_stream.Weather = weather

	world_clock = WorldClockScript.new()
	world_clock.name = "WorldClock"
	add_child(world_clock)
	world_clock.Attach(_socket)
	# The ground marks fade on the world's calendar, so the stream reads the second off this clock.
	chunk_stream.AttachClock(world_clock)

	world_load.attach(self)


## Credential for this client's REST calls, or empty before the zone has authenticated this connection.
##
## Not the login JWT, which is what this used to be: that token expires an hour after the login server issued
## it, and the socket only judges it once at the handshake - so the game carried on working while every map
## request started coming back 401. The zone mints this one per connection and forgets it when the connection
## goes, so it cannot expire underneath a client that is still playing.
##
## The map is the only thing behind HTTP today, hence the generic name: anything else put there wants this
## same ticket rather than a scheme of its own.
func http_ticket() -> String:
	return _http_ticket


func disconnect_from_server() -> void:
	_socket.DisconnectFromServer()


## Requests the delayed logout countdown to start for the active master. Progress arrives via
## [signal logout_countdown_received]; completion is the master vanishing; abort via
## [signal logout_cancelled].
func request_logout() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestLogoutCMSG.new())


## Cancels a pending logout. Reuses an empty-path move ("stop where I stand"), which the server
## already treats as player activity and so removes the logout intent — no dedicated message needed.
func cancel_logout() -> void:
	assert(is_ready_to_send())
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return
	var msg = MoveEntityCMSG.new()
	msg.EntityId = entity_manager.get_controlled_entity_id()
	_socket.SendMessage(msg)


## Asks the server to revive the active entity at its save point. Only meaningful while that entity
## is dead; the server refuses it silently otherwise. Confirmation is its DeadComponentSMSG arriving
## with Removed = true.
func request_respawn() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RespawnCMSG.new())


## Deliberately disconnects and returns to the main menu (used by the logout flow), bypassing the
## connection-lost screen.
func disconnect_to_main_menu() -> void:
	_intentional_disconnect = true
	_socket.DisconnectFromServer()


## Shared tail of every login method. The zone only ever sees a signed JWT, so how it was obtained -
## passkey, later a wallet signature - stops mattering here.
func _accept_login_token(token: String) -> void:
	_login_token = token

	# We have a valid JWT now. Go to master select (blocked until the zone confirms auth) and connect.
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn", true)
	# A real round trip to the zone, and nothing about it is measurable from here - so a caption and
	# no figure. Taken down again in the AuthenticationSuccess branch that unblocks the transition.
	SceneManager.show_loading("Signing in...")

	# Applied here rather than left to BnetSocket's own exported defaults, so the socket and the two
	# HTTP endpoints are all named by one settings file instead of one of the three needing a rebuild.
	var settings := SettingsManager.get_instance()
	_socket.ServerName = settings.game_server_host
	_socket.Port = settings.game_server_port
	_socket.NetLogMode = settings.net_log_mode
	_socket.NetLogFilter = settings.net_log_filter
	_socket.ConnectToServer()


## Signs in with a passkey. The system browser does the WebAuthn ceremony and hands back a one-time
## code, which [PasskeyLoginService] exchanges for a signed JWT.
##
## This is also how a new account is made: the page offers signing in, creating an account and
## recovering one, so the menu needs no separate entry point for registration.
func login_with_passkey() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	last_connection_error = ConnectionError.NO_ERROR
	_passkey_login.StartLogin(SettingsManager.get_instance().login_server_url)


## Whether a stored session exists, i.e. whether signing in can skip the browser.
func has_stored_session() -> bool:
	return _passkey_login.HasStoredSession()


## Uses the stored session instead of the browser. Ends in the same place a passkey login does, or in
## [signal session_resume_unavailable] if the server would not take the token.
func resume_session() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	last_connection_error = ConnectionError.NO_ERROR
	_passkey_login.TryResume(SettingsManager.get_instance().login_server_url)


## Discards the stored session here and on the server, so the next start needs a passkey again.
func sign_out() -> void:
	_passkey_login.SignOut(SettingsManager.get_instance().login_server_url)


## Abandons an in-flight passkey login. The browser tab is left alone - we cannot close it, and the
## login session expires on the server by itself.
func cancel_passkey_login() -> void:
	_passkey_login.Cancel()


func _on_passkey_awaiting_browser() -> void:
	passkey_browser_opened.emit()


func _on_passkey_login_succeeded(token: String) -> void:
	_accept_login_token(token)


func _on_passkey_login_failed(reason: String) -> void:
	printerr("ConnectionManager: passkey login failed: ", reason)
	passkey_login_failed.emit(reason)


func _on_passkey_resume_unavailable() -> void:
	session_resume_unavailable.emit()


func _goto_connection_lost(last_error: ConnectionError) -> void:
	last_connection_error = last_error
	SceneManager.goto_scene("res://Menu/ConnectionLost/ConnectionLost.tscn")


func list_bestia_master() -> void:
	assert(is_ready_to_send())
	var msg = GetMasterCMSG.new()
	_socket.SendMessage(msg)


func get_self() -> void:
	assert(is_ready_to_send())
	var msg = GetSelfCMSG.new()
	_socket.SendMessage(msg)


func get_inventory() -> void:
	assert(is_ready_to_send())
	var msg = GetInventoryCMSG.new()
	_socket.SendMessage(msg)


func get_skills() -> void:
	assert(is_ready_to_send())
	var msg = GetSkillsCMSG.new()
	_socket.SendMessage(msg)


func activate_skill(attack_id: int, skill_level: int, target_position: Vector3 = Vector3.ZERO, target_entity_id: int = 0) -> void:
	assert(is_ready_to_send())
	var msg = ActivateSkillCMSG.new()
	msg.AttackId = attack_id
	msg.SkillLevel = skill_level
	msg.TargetPosition = target_position
	msg.TargetEntityId = target_entity_id
	_socket.SendMessage(msg)


## Spends one or more skill points across one or more skill tree nodes in a single batch.
## [param investments] is an Array of {"attack_id": int, "amount": int} dictionaries.
func invest_skill_points(investments: Array) -> void:
	assert(is_ready_to_send())
	var msg = InvestSkillPointCMSG.new()
	var points: Array = []
	for investment in investments:
		points.append({"attack_id": investment["attack_id"], "amount": investment["amount"]})
	msg.InvestedPoints = points
	_socket.SendMessage(msg)


## Spends one or more status points across one or more base status attributes in a single batch.
## [param investments] is an Array of {"attribute": StatusAttribute ordinal, "amount": int}
## dictionaries (see Game/UI/StatusPoints/status_attribute.gd for the ordinal values).
func invest_status_points(investments: Array) -> void:
	assert(is_ready_to_send())
	var msg = InvestStatusPointCMSG.new()
	var points: Array = []
	for investment in investments:
		points.append({"attribute": investment["attribute"], "amount": investment["amount"]})
	msg.InvestedPoints = points
	_socket.SendMessage(msg)


## Moves the controlled entity to a ground position it can see, walking there tile by tile at once.
## TODO this is bullshit to do this in the connection manager !!! do this in the game and only send the path.
func move_to(destination: Vector3) -> void:
	assert(is_ready_to_send())

	var entity_manager := EntityManager.get_instance()
	var owned_entity = entity_manager.get_controlled_entity() if entity_manager else null
	if owned_entity == null:
		printerr("ConnectionManager: cannot move, no owned entity yet")
		return

	# Moving cancels a cast server-side, so movement clicks are swallowed while channelling rather
	# than silently throwing the cast away. Gated here rather than in the mouse state so every caller
	# is covered; cancel_logout() sends its empty stop directly and is deliberately unaffected.
	if owned_entity.is_casting():
		return

	# Mid-walk the route starts on the tile being stepped onto, and goes out with that tile in front so the
	# server joins it onto the walk there - see Entity.joining_tile. Otherwise from the logical position, not
	# the rendered one: the path is rounded to whole tiles on the way out (Vec3Convert), and the rendered
	# position carries a sub-voxel ground correction that can round the starting tile to the wrong side of a
	# boundary.
	var join: Vector3 = owned_entity.joining_tile()
	var walking := join.is_finite()
	var start: Vector3 = join if walking else owned_entity.get_logical_position()
	var path := PathCalculator.calculate_tile_path(start, destination)
	if walking:
		path.push_front(join)
	if path.is_empty():
		return

	var waypoints: Array[Vector3] = [destination]
	var msg = MoveEntityCMSG.new()
	msg.EntityId = owned_entity.entity_id
	msg.Waypoints = waypoints
	msg.FirstLeg = path
	_socket.SendMessage(msg)

	# Walk it now rather than a round trip from now. Waiting for the server's own PathComponentSMSG before
	# moving put the whole round trip between the click and the first step, which is what made movement feel
	# unresponsive however well the interpolation behaved. The server walks these steps as sent when they are
	# valid - see Entity.predict_path for what happens when it disagrees, or does not answer.
	owned_entity.predict_path(path)


## Sends one of the player's own entities through [param waypoints], however far: the server plans the way and
## keeps walking it while the player looks elsewhere. [param append] adds them to the journey under way;
## [param journey_name] and [param saved_route_id] say which saved route it follows, if any.
func send_on_journey(
	entity_id: int, waypoints: Array[Vector3], append: bool = false, journey_name: String = "", saved_route_id: int = 0
) -> void:
	assert(is_ready_to_send())

	var msg = MoveEntityCMSG.new()
	msg.EntityId = entity_id
	msg.Waypoints = waypoints
	msg.Append = append
	msg.Name = journey_name
	msg.SavedRouteId = saved_route_id
	_socket.SendMessage(msg)


func request_saved_routes() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestSavedRoutesCMSG.new())


## An empty [param route_name] lets the server name it from where it starts and ends.
func save_route(waypoints: Array[Vector3], route_name: String = "") -> void:
	assert(is_ready_to_send())
	var msg = SaveRouteCMSG.new()
	msg.Waypoints = waypoints
	msg.Name = route_name
	_socket.SendMessage(msg)


## An empty [param route_name] gives the route its server-made name back.
func rename_route(route_id: int, route_name: String) -> void:
	assert(is_ready_to_send())
	var msg = RenameRouteCMSG.new()
	msg.RouteId = route_id
	msg.Name = route_name
	_socket.SendMessage(msg)


func delete_route(route_id: int) -> void:
	assert(is_ready_to_send())
	var msg = DeleteRouteCMSG.new()
	msg.RouteId = route_id
	_socket.SendMessage(msg)


## [param args] carries whatever a scripted item gathered first - a placement position, a facing. Null for
## the ordinary case, where using the item is the whole request. Build one with ScriptArgsCls.new() and the
## keys in ScriptArgKeys.
func use_item(player_item_id: int, args = null) -> void:
	assert(is_ready_to_send())
	var msg = UseItemCMSG.new()
	msg.PlayerItemId = player_item_id
	msg.Args = args
	_socket.SendMessage(msg)


## [param player_item_id] names the held pile or copy to drop from, as InventoryItem.player_item_id does.
func drop_item(player_item_id: int, amount: int) -> void:
	assert(is_ready_to_send())
	var msg = DropItemCMSG.new()
	msg.PlayerItemId = player_item_id
	msg.Amount = amount
	_socket.SendMessage(msg)


func loot_item(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = LootItemCMSG.new()
	msg.EntityId = entity_id
	_socket.SendMessage(msg)


## Asks the server to take a static prop (a mana crystal, an aetherite shard) straight into the
## inventory. [param entity_id] comes off a ChunkStaticEntitiesSMSG entry and is only valid while the
## client holds that chunk - a stale one is simply refused, so there is nothing to check here.
##
## The server may refuse (out of range, already taken, not a collectible kind) with an OperationError.
## Nothing is applied locally either way: the prop disappears when StaticEntityRemovedSMSG arrives.
func collect_prop(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = CollectPropCMSG.new()
	msg.EntityId = entity_id
	_socket.SendMessage(msg)


## Clicking on something in the world. What it means is decided by the server from what the target is, so
## this one call covers starting to build, stopping again, and whatever a prop offers later.
##
## [param args] carries anything the interaction needed the player to choose first - see ScriptArgKeys.
func interact_entity(entity_id: int, args = null) -> void:
	assert(is_ready_to_send())
	var msg = InteractEntityCMSG.new()
	msg.EntityId = entity_id
	msg.Args = args
	_socket.SendMessage(msg)


## Asks the server to wear a held item. [param slot] is an EquipmentSlot ordinal (see
## Game/Item/equipment_slot.gd). [param player_item_id] names the held copy, as InventoryItem.player_item_id
## does. The server may refuse: it answers with an OperationError plus a fresh EquipmentComponentSMSG, so
## never assume success locally.
func equip_item(player_item_id: int, slot: int) -> void:
	assert(is_ready_to_send())
	var msg = EquipItemCMSG.new()
	msg.PlayerItemId = player_item_id
	msg.Slot = slot
	_socket.SendMessage(msg)


func unequip_item(slot: int) -> void:
	assert(is_ready_to_send())
	var msg = UnequipItemCMSG.new()
	msg.Slot = slot
	_socket.SendMessage(msg)


## Asks the server to perform one recipe. [param target_unique_id] names the held item instance to work on
## and is 0 for a recipe that makes something instead.
##
## The server may refuse (materials short, station gone, already crafting) with an OperationError, and a
## craft that starts can still fail when it resolves - nothing is applied locally either way. Progress
## arrives as a CastingComponentSMSG, which is deliberately the same bar a spell cast uses.
func craft_item(recipe_id: int, target_unique_id: int = 0) -> void:
	assert(is_ready_to_send())
	var msg = CraftItemCMSG.new()
	msg.RecipeId = recipe_id
	msg.TargetUniqueId = target_unique_id
	_socket.SendMessage(msg)


## Tries a mix against the blueprints of [param skill_id]. [param materials] maps a held pile's player_item_id to
## the amount to use. The materials are always spent; the answer arrives as [signal discovery_result_received].
func discover_recipe(skill_id: int, materials: Dictionary) -> void:
	assert(is_ready_to_send())
	var msg = DiscoverRecipeCMSG.new()
	msg.SkillId = skill_id
	msg.PlayerItemIds = PackedInt64Array(materials.keys())
	msg.Amounts = PackedInt32Array(materials.values())
	_socket.SendMessage(msg)


## Abandons the craft in progress. Nothing is refunded because nothing has been spent yet.
func cancel_craft() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(CancelCraftCMSG.new())


## Asks the player behind [param target_entity_id] to trade. Refused with an OperationError when they are
## out of reach, already trading, or not somebody who can trade yet.
func request_trade(target_entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = RequestTradeCMSG.new()
	msg.TargetEntityId = target_entity_id
	_socket.SendMessage(msg)


## Asks the server to deal with [param target_entity_id]. What that means is the server's decision -
## talking to a townsperson today. Answered with a ConversationSMSG, or with nothing at all when the
## target is not somebody who can be dealt with.
func interact(target_entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = InteractCMSG.new()
	msg.EntityId = target_entity_id
	_socket.SendMessage(msg)


## Picks one of the options a ConversationSMSG offered. Send the option's TopicId, never its position -
## the server keeps no conversation and cannot resolve "the second one".
func answer_conversation(target_entity_id: int, topic_id: int) -> void:
	assert(is_ready_to_send())
	var msg = ConversationChoiceCMSG.new()
	msg.EntityId = target_entity_id
	msg.TopicId = topic_id
	_socket.SendMessage(msg)


## Answers the prompt a TradeRequestSMSG raised.
func answer_trade_request(trade_id: int, accept: bool) -> void:
	assert(is_ready_to_send())
	var msg = AnswerTradeRequestCMSG.new()
	msg.TradeId = trade_id
	msg.Accept = accept
	_socket.SendMessage(msg)


## Puts an item into our side of the trade. Nothing is applied locally: the answering TradeStateSMSG is what
## draws it, which is also how a refusal corrects itself.
## [param player_item_id] names the held pile or copy, as InventoryItem.player_item_id does.
func offer_trade_item(trade_id: int, player_item_id: int, amount: int) -> void:
	assert(is_ready_to_send())
	var msg = OfferTradeItemCMSG.new()
	msg.TradeId = trade_id
	msg.PlayerItemId = player_item_id
	msg.Amount = amount
	_socket.SendMessage(msg)


## Takes one line back out of our own offer. [param offer_slot_id] is the id the server gave that line, not
## an item id - two lines of the same item are separate offers.
func retract_trade_item(trade_id: int, offer_slot_id: int) -> void:
	assert(is_ready_to_send())
	var msg = RetractTradeItemCMSG.new()
	msg.TradeId = trade_id
	msg.OfferSlotId = offer_slot_id
	_socket.SendMessage(msg)


## Asks [param merchant_entity_id] what they have. Answered by a ShopOfferSMSG, or by an OperationError when
## there is no settlement underfoot, the merchant keeps no shop, or they are too far off to speak to.
##
## The merchant chooses whose counter, never whose prices: those are the town we are standing in.
func open_shop(merchant_entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = OpenShopCMSG.new()
	msg.MerchantEntityId = merchant_entity_id
	_socket.SendMessage(msg)


## Buys or sells [param amount] of one item. Nothing is applied locally - the answering ShopOfferSMSG
## redraws the counter, which is also how a refusal corrects itself.
##
## No price is sent. What was shown is a snapshot; the server quotes against the live one, so a stale
## window pays today's price rather than the one on it.
func shop_trade(merchant_entity_id: int, item_id: int, amount: int, selling: bool) -> void:
	assert(is_ready_to_send())
	var msg = ShopTradeCMSG.new()
	msg.MerchantEntityId = merchant_entity_id
	msg.ItemId = item_id
	msg.Amount = amount
	msg.Selling = selling
	_socket.SendMessage(msg)


## Asks one page of the market at [param house_entity_id]. Answered by an AuctionListingsSMSG.
func search_auctions(
	house_entity_id: int, category: int, min_level: int, max_level: int, item_ids: Array[int], mine: bool, page: int
) -> void:
	assert(is_ready_to_send())
	var msg = SearchAuctionsCMSG.new()
	msg.HouseEntityId = house_entity_id
	msg.Category = category
	msg.MinLevel = min_level
	msg.MaxLevel = max_level
	msg.ItemIds = item_ids
	msg.Mine = mine
	msg.Page = page
	_socket.SendMessage(msg)


## Buys a whole listing. Answered by an AuctionPurchasedSMSG, or an OperationError when somebody was faster.
func buy_auction_listing(house_entity_id: int, listing_id: int) -> void:
	assert(is_ready_to_send())
	var msg = BuyAuctionListingCMSG.new()
	msg.HouseEntityId = house_entity_id
	msg.ListingId = listing_id
	_socket.SendMessage(msg)


## Locks or unlocks our side. Any change to either offer clears both locks again.
func set_trade_lock(trade_id: int, locked: bool) -> void:
	assert(is_ready_to_send())
	var msg = SetTradeLockCMSG.new()
	msg.TradeId = trade_id
	msg.Locked = locked
	_socket.SendMessage(msg)


## The final commitment. Accepted only once both sides are locked; the exchange runs on the second one.
func confirm_trade(trade_id: int) -> void:
	assert(is_ready_to_send())
	var msg = ConfirmTradeCMSG.new()
	msg.TradeId = trade_id
	_socket.SendMessage(msg)


## Calls the trade off. Allowed from either side up until the second confirmation.
func cancel_trade(trade_id: int) -> void:
	assert(is_ready_to_send())
	var msg = CancelTradeCMSG.new()
	msg.TradeId = trade_id
	_socket.SendMessage(msg)


## Asks for our party's roster; answered with a PartyInfoSMSG, an empty one when we are in no party.
func request_party_info() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestPartyInfoCMSG.new())


## Founds a party with us as its owner. Answered with the roster, or an OperationError for a bad name.
func create_party(party_name: String) -> void:
	assert(is_ready_to_send())
	var msg = CreatePartyCMSG.new()
	msg.PartyName = party_name
	_socket.SendMessage(msg)


## Invites the master named [param master_name]. We are told it went out with OperationSuccess.
func invite_to_party(master_name: String) -> void:
	assert(is_ready_to_send())
	var msg = RequestPartyInvitationCMSG.new()
	msg.InvitedMasterName = master_name
	_socket.SendMessage(msg)


## Answers the prompt a PartyInvitationSMSG raised.
func answer_party_invite(invitation_id: int, accept: bool) -> void:
	assert(is_ready_to_send())
	var msg = AcceptPartyInviteCMSG.new() if accept else DeclinePartyInviteCMSG.new()
	msg.InvitationId = invitation_id
	_socket.SendMessage(msg)


## Leaves our party. The owner leaving disbands it, since there is no ownership transfer.
func leave_party() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(LeavePartyCMSG.new())


## The owner removes the member playing [param master_id] from the party.
func kick_party_member(master_id: int) -> void:
	assert(is_ready_to_send())
	var msg = RemovePartyMemberCMSG.new()
	msg.MemberMasterId = master_id
	_socket.SendMessage(msg)


## The owner picks Even share or Each take. Nothing is applied locally: the roster that answers it is.
func set_party_exp_share(even_share: bool) -> void:
	assert(is_ready_to_send())
	var msg = SetPartyExpShareCMSG.new()
	msg.EvenShare = even_share
	_socket.SendMessage(msg)


## Asks for our guild; answered with a GuildInfoSMSG, an empty one when we are in no guild.
func request_guild_info() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestGuildInfoCMSG.new())


## Asks for the selected master's Bestia Compendium; answered with a CompendiumSMSG.
func request_compendium() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestCompendiumCMSG.new())


## Founds a guild named [param guild_name], spending a Founder's Seal from the master's bag.
func create_guild(guild_name: String) -> void:
	assert(is_ready_to_send())
	var msg = CreateGuildCMSG.new()
	msg.Name = guild_name
	_socket.SendMessage(msg)


func invite_to_guild(master_name: String) -> void:
	assert(is_ready_to_send())
	var msg = InviteToGuildCMSG.new()
	msg.MasterName = master_name
	_socket.SendMessage(msg)


## Answers the prompt a GuildInvitationSMSG raised.
func answer_guild_invite(invitation_id: int, accept: bool) -> void:
	assert(is_ready_to_send())
	var msg = AnswerGuildInviteCMSG.new()
	msg.InvitationId = invitation_id
	msg.Accept = accept
	_socket.SendMessage(msg)


func leave_guild() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(LeaveGuildCMSG.new())


## Kicks and expels [param master_id]; it cannot be invited again until [method lift_guild_expulsion].
func kick_guild_member(master_id: int, reason: String) -> void:
	assert(is_ready_to_send())
	var msg = KickGuildMemberCMSG.new()
	msg.MasterId = master_id
	msg.Reason = reason
	_socket.SendMessage(msg)


func lift_guild_expulsion(master_id: int) -> void:
	assert(is_ready_to_send())
	var msg = LiftGuildExpulsionCMSG.new()
	msg.MasterId = master_id
	_socket.SendMessage(msg)


## The leader hands the guild to [param master_id] and takes that member's rank.
func transfer_guild_leadership(master_id: int) -> void:
	assert(is_ready_to_send())
	var msg = TransferGuildLeadershipCMSG.new()
	msg.MasterId = master_id
	_socket.SendMessage(msg)


func set_guild_member_rank(master_id: int, rank_id: int) -> void:
	assert(is_ready_to_send())
	var msg = SetGuildMemberRankCMSG.new()
	msg.MasterId = master_id
	msg.RankId = rank_id
	_socket.SendMessage(msg)


## Edits a rank, or adds one at the bottom when [param rank_id] is 0.
func save_guild_rank(rank_id: int, rank_name: String, tax_percent: int, can_invite: bool, can_kick: bool, can_manage_ranks: bool, can_use_guild_skills: bool, can_edit_motd: bool) -> void:
	assert(is_ready_to_send())
	var msg = SaveGuildRankCMSG.new()
	msg.RankId = rank_id
	msg.Name = rank_name
	msg.TaxPercent = tax_percent
	msg.CanInvite = can_invite
	msg.CanKick = can_kick
	msg.CanManageRanks = can_manage_ranks
	msg.CanUseGuildSkills = can_use_guild_skills
	msg.CanEditMotd = can_edit_motd
	_socket.SendMessage(msg)


func delete_guild_rank(rank_id: int) -> void:
	assert(is_ready_to_send())
	var msg = DeleteGuildRankCMSG.new()
	msg.RankId = rank_id
	_socket.SendMessage(msg)


## The leader spends a guild skill point on the guild skill [param skill_id].
func invest_guild_skill(skill_id: int) -> void:
	assert(is_ready_to_send())
	var msg = InvestGuildSkillCMSG.new()
	msg.SkillId = skill_id
	_socket.SendMessage(msg)


## Replaces the guild's message of the day; both empty clears it.
func set_guild_motd(title: String, body: String) -> void:
	assert(is_ready_to_send())
	var msg = SetGuildMotdCMSG.new()
	msg.Title = title
	msg.Body = body
	_socket.SendMessage(msg)


## Replaces the guild's emblem with [param png], a 32x32 PNG made by [EmblemPicture].
func set_guild_emblem(png: PackedByteArray) -> void:
	assert(is_ready_to_send())
	var msg = SetGuildEmblemCMSG.new()
	msg.Png = png
	_socket.SendMessage(msg)


## Asks for the guild's newest log entries; answered with a GuildLogSMSG.
func request_guild_log() -> void:
	assert(is_ready_to_send())
	_socket.SendMessage(RequestGuildLogCMSG.new())


## The leader ends the guild. [param confirm_name] is the name the player typed, compared by the server.
func disband_guild(confirm_name: String) -> void:
	assert(is_ready_to_send())
	var msg = DisbandGuildCMSG.new()
	msg.ConfirmName = confirm_name
	_socket.SendMessage(msg)


func send_chat(text: String, mode: int = 3, target_player: String = "") -> void:
	assert(is_ready_to_send())
	var msg = ChatCMSG.new()
	msg.Text = text
	msg.ChatMode = mode
	if target_player != "":
		msg.TargetPlayerName = target_player
	_socket.SendMessage(msg)


## Swings the active entity's basic attack at [param entity_id]. A basic attack carries no attack id and
## no level - it has no entry in the Attack DB at all. Casting a skill is [method activate_skill].
func send_attack_entity(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = AttackEntityCMSG.new()
	msg.EntityId = entity_id
	_socket.SendMessage(msg)


## Asks to drive another of our own entities. Nothing changes here until [signal active_entity_received].
func select_active_entity(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = SelectActiveEntityCMSG.new()
	msg.EntityId = entity_id
	_socket.SendMessage(msg)


## Requests the server to create a new master (character) for the current account.
## The result arrives asynchronously via the operation_success / operation_error signals.
## [param body], [param face] and [param hair] are the proto enum values. [param spawn_point_id] is
## the id of a MasterSpawnPointCandidate from [signal master_info_received]'s SpawnPoints and is
## mandatory - there is no world-default spawn, the server refuses a request that names none.
## [param slot] is the account slot, counted from 0, the master is pinned to for good - the empty slot the
## player clicked. The server refuses one the account does not have or that already holds a master.
## [param effort_values] is the starting status value per attribute, keyed by
## StatusAttribute.field_key(). It must spend the creation budget exactly, or the server refuses the
## request - CreateNewMaster keeps its Create button disabled until it does.
func create_master(character_name: String, body: int, face: int, hair: int, hair_color: Color, skin_color: Color, spawn_point_id: int, slot: int, effort_values: Dictionary) -> void:
	assert(is_ready_to_send())
	var msg = CreateMasterCMSG.new()
	msg.Name = character_name
	msg.Body = body
	msg.Face = face
	msg.Hair = hair
	msg.HairColor = hair_color
	msg.SkinColor = skin_color
	msg.SpawnPointId = spawn_point_id
	msg.Slot = slot
	msg.Strength = effort_values.get("strength", 0)
	msg.Agility = effort_values.get("agility", 0)
	msg.Vitality = effort_values.get("vitality", 0)
	msg.Intelligence = effort_values.get("intelligence", 0)
	msg.Dexterity = effort_values.get("dexterity", 0)
	msg.Willpower = effort_values.get("willpower", 0)
	_socket.SendMessage(msg)


## Requests permanent deletion of one of the account's masters. This cannot be undone - the character,
## its bestias, its items and its skills are gone.
## [param confirmation_name] is the name the player typed into the confirmation prompt, not the name read
## back from the master data: the server compares the two and refuses the deletion if they differ, which
## is what stops a stray click from costing a character.
## The result arrives asynchronously via the operation_success / operation_error signals.
func delete_master(master_id: int, confirmation_name: String) -> void:
	assert(is_ready_to_send())
	var msg = DeleteMasterCMSG.new()
	msg.MasterId = master_id
	msg.ConfirmationName = confirmation_name
	_socket.SendMessage(msg)


## Select the bestia master given by the id
func select_bestia_master(master_info: MasterInfo) -> void:
	assert(is_ready_to_send())
	selected_master_info = master_info
	var msg = SelectMasterCMSG.new()
	msg.MasterId = master_info.MasterId
	_socket.SendMessage(msg)

	# The transition first: goto_scene returns at its first await, so the watch is armed well before
	# the fade reaches black and the loading screen comes up out of it rather than over the menu.
	SceneManager.goto_scene("res://Game/Game.tscn")
	world_load.begin()


func _on_bnet_socket_message_received(message: Object) -> void:
	if message is AuthenticationSuccess:
		# if auth successfull we are now considered fully authed and can unblock the scene
		# loader to go into the master selection screen.
		assert(_connection_state == ConnectionState.CONNECTED_NOT_AUTHED)
		_connection_state = ConnectionState.CONNECTED_AUTHED
		_http_ticket = message.HttpTicket
		# The zone closes a connection that is silent for 30 s, so an idle player must still ping.
		_ping_timer.start()
		SceneManager.unblock_transition()
		SceneManager.hide_loading()
	elif message is Pong:
		_on_pong()
	elif message is MasterSMSG:
		master_info_received.emit(message)
	elif message is LogoutIntentComponentSMSG:
		# Must be checked before the generic EntitySMSG branch below, since
		# LogoutIntentSMSG is itself an EntitySMSG subtype. Removed = true is the
		# server's "logout aborted" signal, sent as the same message type re-sent once more.
		if message.Removed:
			logout_cancelled.emit()
		else:
			logout_countdown_received.emit(message.RemainingSeconds)
	elif message is EntitySMSG:
		entity_received.emit(message)
	elif message is SelfSMSG:
		self_received.emit(message)
	elif message is OwnedBestiasSMSG:
		owned_bestias_received.emit(message)
	elif message is ActiveEntitySMSG:
		active_entity_received.emit(message)
	elif message is JourneyEndedSMSG:
		journey_ended.emit(message)
	elif message is SavedRoutesSMSG:
		saved_routes_received.emit(message)
	elif message is BestiaCaptureSMSG:
		bestia_capture_received.emit(message)
	elif message is ChatSMSG:
		chat_received.emit(message)
	elif message is DialogSMSG:
		dialog_received.emit(message)
	elif message is ConversationSMSG:
		conversation_received.emit(message)
	elif message is CraftableRecipesSMSG:
		craftable_recipes_received.emit(message)
	elif message is DiscoveryResultSMSG:
		discovery_result_received.emit(message)
	elif message is MaterialAxesSMSG:
		CraftAxes.store(message.AxesByItemId)
	elif message is OperationSuccess:
		operation_success.emit(message)
	elif message is OperationError:
		operation_error.emit(message)
	elif message is TradeRequestSMSG:
		trade_request_received.emit(message)
	elif message is TradeStateSMSG:
		trade_state_received.emit(message)
	elif message is ShopOfferSMSG:
		shop_offer_received.emit(message)
	elif message is AuctionHouseOpenSMSG:
		auction_house_opened.emit(message)
	elif message is AuctionListingsSMSG:
		auction_listings_received.emit(message)
	elif message is AuctionClaimsSMSG:
		auction_claims_received.emit(message)
	elif message is AuctionPurchasedSMSG:
		auction_purchased.emit(message)
	elif message is PartySMSG:
		party_message_received.emit(message)
	elif message is GuildSMSG:
		guild_message_received.emit(message)
	elif message is CompendiumSMSG:
		compendium_received.emit(message)
	elif message is MapSMSG:
		# Terrain traffic. ChunkStreamManager subscribes to MessageReceived itself, and a signal fans out to
		# every listener, so these reach this handler too and have to be ignored rather than reported.
		pass
	else:
		printerr("ConnectionManager: message was not identified and processed: %s" % message)


## Times the round trip against the ping this answers.
##
## A pong with nothing outstanding is dropped: it is either a duplicate or the answer to a ping whose send
## time a reconnect has already discarded, and timing it would report a round trip of however long the client
## has been running.
func _on_pong() -> void:
	if _ping_sent_at_ms < 0:
		return

	rtt_ms = Time.get_ticks_msec() - _ping_sent_at_ms
	_ping_sent_at_ms = -1

	var sample := float(rtt_ms)

	if latency_msec < 0.0:
		latency_msec = sample
		latency_jitter_msec = 0.0
		return

	latency_jitter_msec = lerpf(latency_jitter_msec, absf(sample - latency_msec), _LATENCY_SMOOTHING)
	latency_msec = lerpf(latency_msec, sample, _LATENCY_SMOOTHING)


func is_ready_to_send() -> bool:
	return _connection_state == ConnectionState.CONNECTED_AUTHED


func current_master_key() -> String:
	if ConnectionManager.selected_master_info == null:
		return ""
	return str(ConnectionManager.selected_master_info.MasterId)


func _on_bnet_socket_connection_status_changed(status: int) -> void:
	if status == 0:
		# connection was closed, perform cleanup and inform the user.
		_connection_state = ConnectionState.DISCONNECTED
		_ping_timer.stop()
		rtt_ms = -1
		# A measurement belongs to the connection it was taken on; the next one starts over.
		_ping_sent_at_ms = -1
		latency_msec = -1.0
		latency_jitter_msec = 0.0
		# The zone forgot this ticket the moment the socket died, so holding on to it would only let the map
		# present a credential that is already refused.
		_http_ticket = ""
		# Nothing is going to finish loading now, and the scene change below is to a menu the player has
		# to be able to use. Covers the sign-in screen too, which this same socket may have raised.
		world_load.abort()
		if _intentional_disconnect:
			# Player-initiated logout: go home quietly instead of showing "connection lost".
			_intentional_disconnect = false
			SceneManager.goto_scene("res://Menu/Main/Main.tscn")
		elif _socket.LastDisconnectReason == "PROTOCOL_MISMATCH":
			_goto_connection_lost(ConnectionError.PROTOCOL_MISMATCH)
		else:
			_goto_connection_lost(ConnectionError.ZONE_CONNECTION_LOST)
	elif status == 1:
		if _connection_state == ConnectionState.DISCONNECTED:
			# we are initially connected now send auth, please handle this better.
			_connection_state = ConnectionState.CONNECTED_NOT_AUTHED
			# Authentication also carries the protocol version; the zone refuses any other.
			var auth_msg = Authentication.new(
				_login_token,
				SettingsManager.VERSION
			)
			_socket.SendMessage(auth_msg)
	elif status == 2:
		# socket tries to connect
		pass
	else:
		printerr("Unknown connection status (expected 0-2): ", status)


func _on_ping_timer_timeout() -> void:
	if _connection_state == ConnectionState.CONNECTED_AUTHED:
		_ping_sent_at_ms = Time.get_ticks_msec()
		_socket.SendMessage(Ping.new())
