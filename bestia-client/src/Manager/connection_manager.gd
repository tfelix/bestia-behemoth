extends Node
## Keeps the connection to the server and is responsible for scene changing
##
## Orchestrates login procedure and handles connection state and message translation
## and filtering to the various subsystems.
##

signal master_info_received(master: MasterSMSG)
signal entity_received(message: EntitySMSG)
signal self_received(message: SelfSMSG)
signal chat_received(message: ChatSMSG)
## Emitted when the server wants a dialog shown. Account-scoped, not tied to an entity, so it is
## deliberately kept out of [signal entity_received]. Consumed by the DialogManager autoload.
signal dialog_received(message: DialogSMSG)
## Emitted when the server answers a crafting-skill activation with what can be made here. Account-scoped
## for the reason [signal dialog_received] is: it opens a window, and a window belongs to a client.
signal craftable_recipes_received(message: CraftableRecipesSMSG)
signal operation_success(message: OperationSuccess)
signal operation_error(message: OperationError)
## Emitted when another player asks us to trade. Account-scoped for the reason [signal dialog_received] is:
## it raises a prompt, and a prompt belongs to a client rather than to an entity.
signal trade_request_received(message: TradeRequestSMSG)
## Emitted on every change to an open trade. A full snapshot each time - it opens the window, updates it and
## closes it, so the trade window never has to reconstruct state from a sequence.
signal trade_state_received(message: TradeStateSMSG)
## Emitted whenever the server re-syncs the pending logout countdown (seconds until despawn).
signal logout_countdown_received(remaining_seconds: float)
## Emitted when the server aborts a pending logout (player moved / used a skill / took damage).
signal logout_cancelled()
## Emitted once the system browser has been opened for a passkey login, so the UI can offer a way out
## of a wait that is otherwise entirely off-screen.
signal passkey_browser_opened()
## Emitted when a passkey login ends without a token. Carries a message meant for the player.
signal passkey_login_failed(reason: String)

enum ConnectionState {DISCONNECTED, CONNECTED_NOT_AUTHED, CONNECTED_AUTHED}
enum ConnectionError {NO_ERROR, LOGIN_OFFLINE, LOGIN_ERROR, ZONE_CONNECTION_LOST}

@onready var _socket = $BnetSocket
@onready var _login_request = $LoginRequest
@onready var _passkey_login = $PasskeyLogin


var Authentication = load("res://Bnet/Message/Authentication.cs")
var GetMasterCMSG = load("res://Bnet/Message/Master/GetMasterCMSG.cs")
var GetSelfCMSG = load("res://Bnet/Message/Master/GetSelfCMSG.cs")
var SelectMasterCMSG = load("res://Bnet/Message/Master/SelectMasterCMSG.cs")
var CreateMasterCMSG = load("res://Bnet/Message/Master/CreateMasterCMSG.cs")
var DeleteMasterCMSG = load("res://Bnet/Message/Master/DeleteMasterCMSG.cs")
var GetAllEntities = load("res://Bnet/Message/Entity/GetAllEntities.cs")
var AttackEntityCMSG = load("res://Bnet/Message/Entity/AttackEntityCMSG.cs")
var MoveActiveEntityCMSG = load("res://Bnet/Message/Entity/MoveActiveEntityCMSG.cs")
var GetInventoryCMSG = load("res://Bnet/Message/Inventory/GetInventoryCMSG.cs")
var GetSkillsCMSG = load("res://Bnet/Message/Master/GetSkillsCMSG.cs")
var ActivateSkillCMSG = load("res://Bnet/Message/Master/ActivateSkillCMSG.cs")
var InvestSkillPointCMSG = load("res://Bnet/Message/Master/InvestSkillPointCMSG.cs")
var UseItemCMSG = load("res://Bnet/Message/Inventory/UseItemCMSG.cs")
var DropItemCMSG = load("res://Bnet/Message/Inventory/DropItemCMSG.cs")
var LootItemCMSG = load("res://Bnet/Message/Inventory/LootItemCMSG.cs")
var EquipItemCMSG = load("res://Bnet/Message/Inventory/EquipItemCMSG.cs")
var UnequipItemCMSG = load("res://Bnet/Message/Inventory/UnequipItemCMSG.cs")
var RequestLogoutCMSG = load("res://Bnet/Message/System/RequestLogoutCMSG.cs")
var CollectPropCMSG = load("res://Bnet/Message/Map/CollectPropCMSG.cs")
var CraftItemCMSG = load("res://Bnet/Message/Crafting/CraftItemCMSG.cs")
var CancelCraftCMSG = load("res://Bnet/Message/Crafting/CancelCraftCMSG.cs")
var RequestTradeCMSG = load("res://Bnet/Message/Trade/RequestTradeCMSG.cs")
var AnswerTradeRequestCMSG = load("res://Bnet/Message/Trade/AnswerTradeRequestCMSG.cs")
var OfferTradeItemCMSG = load("res://Bnet/Message/Trade/OfferTradeItemCMSG.cs")
var RetractTradeItemCMSG = load("res://Bnet/Message/Trade/RetractTradeItemCMSG.cs")
var SetTradeLockCMSG = load("res://Bnet/Message/Trade/SetTradeLockCMSG.cs")
var ConfirmTradeCMSG = load("res://Bnet/Message/Trade/ConfirmTradeCMSG.cs")
var CancelTradeCMSG = load("res://Bnet/Message/Trade/CancelTradeCMSG.cs")
var Ping = load("res://Bnet/Message/Ping.cs")
var ChunkStreamManagerScript = load("res://Game/World/ChunkStreamManager.cs")
var WeatherStateScript = load("res://Game/World/WeatherState.cs")
var WorldClockScript = load("res://Game/World/WorldClock.cs")

var _connection_state : ConnectionState = ConnectionState.DISCONNECTED

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
var selected_master_info: MasterInfo = null

# Set while we deliberately drop the connection (logout to main menu) so the socket-closed handler
# routes to the main menu instead of the "connection lost" screen.
var _intentional_disconnect: bool = false


func _ready() -> void:
	chunk_stream = ChunkStreamManagerScript.new()
	chunk_stream.name = "ChunkStreamManager"
	add_child(chunk_stream)
	chunk_stream.Attach(_socket)

	weather = WeatherStateScript.new()
	weather.name = "WeatherState"
	add_child(weather)
	weather.Attach(_socket)

	world_clock = WorldClockScript.new()
	world_clock.name = "WorldClock"
	add_child(world_clock)
	world_clock.Attach(_socket)


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
	_socket.SendMessage(MoveActiveEntityCMSG.new())


## Deliberately disconnects and returns to the main menu (used by the logout flow), bypassing the
## connection-lost screen.
func disconnect_to_main_menu() -> void:
	_intentional_disconnect = true
	_socket.DisconnectFromServer()


func login() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	last_connection_error = ConnectionError.NO_ERROR
	# Exchange the configured static development credentials for a signed JWT at the login server.
	# Once we have the token we connect to the zone and send it during the auth handshake.
	var url = SettingsManager.login_server_url + "/api/v1/auth/static"
	var headers = ["Content-Type: application/json"]
	var payload = JSON.stringify({
		"username": SettingsManager.dev_username,
		"token": SettingsManager.dev_static_token
	})
	var err = _login_request.request(url, headers, HTTPClient.METHOD_POST, payload)
	if err != OK:
		printerr("ConnectionManager: could not start login request: ", err)
		_goto_connection_lost(ConnectionError.LOGIN_OFFLINE)


# TODO move the code into a seperate script on the LoginRequest node to tidy the connection manager up.
func _on_login_request_completed(result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		printerr("ConnectionManager: login failed (result=%s, code=%s)" % [result, response_code])
		_goto_connection_lost(ConnectionError.LOGIN_ERROR)
		return

	var json = JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_DICTIONARY or not json.data.has("token"):
		printerr("ConnectionManager: could not parse login response")
		_goto_connection_lost(ConnectionError.LOGIN_ERROR)
		return

	_accept_login_token(json.data["token"])


## Shared tail of every login method. The zone only ever sees a signed JWT, so how it was obtained -
## static development token, passkey, later a wallet signature - stops mattering here.
func _accept_login_token(token: String) -> void:
	_login_token = token

	# We have a valid JWT now. Go to master select (blocked until the zone confirms auth) and connect.
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn", true)
	_socket.ConnectToServer()


## Signs in with a passkey. The system browser does the WebAuthn ceremony and hands back a one-time
## code, which [PasskeyLoginService] exchanges for the same JWT the static login returns.
func login_with_passkey() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	last_connection_error = ConnectionError.NO_ERROR
	_passkey_login.StartLogin(SettingsManager.login_server_url)


## Creates a new account and its first passkey. Same flow as [method login_with_passkey], only the
## page the browser lands on differs.
func register_with_passkey() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	last_connection_error = ConnectionError.NO_ERROR
	_passkey_login.StartRegistration(SettingsManager.login_server_url)


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


## Moves the active entity towards a ground position, walking there tile by tile.
## TODO this is bullshit to do this in the connection manager !!! do this in the game and only send the path.
func move_to(destination: Vector3) -> void:
	assert(is_ready_to_send())

	var entity_manager = get_tree().get_first_node_in_group("entity_manager")
	var owned_entity = entity_manager.get_owned_entity() if entity_manager else null
	if owned_entity == null:
		printerr("ConnectionManager: cannot move, no owned entity yet")
		return

	# Moving cancels a cast server-side, so movement clicks are swallowed while channelling rather
	# than silently throwing the cast away. Gated here rather than in the mouse state so every caller
	# is covered; cancel_logout() sends its empty-path stop directly and is deliberately unaffected.
	if owned_entity.is_casting():
		return

	# The logical position, not the rendered one: the path is rounded to whole tiles on the way out
	# (Vec3Convert), and the rendered position carries a sub-voxel ground correction that can round
	# the starting tile to the wrong side of a boundary.
	var path := PathCalculator.calculate_tile_path(owned_entity.get_logical_position(), destination)
	if path.is_empty():
		return

	var msg = MoveActiveEntityCMSG.new()
	msg.Path = path
	_socket.SendMessage(msg)


func use_item(item_id: int) -> void:
	assert(is_ready_to_send())
	var msg = UseItemCMSG.new()
	msg.ItemId = item_id
	_socket.SendMessage(msg)


## [param unique_id] names which copy to drop and is 0 for a plain stack - see the field comment in
## drop_item_cmsg.proto for why the server tolerates a 0 on an instance item too.
func drop_item(item_id: int, amount: int, unique_id: int = 0) -> void:
	assert(is_ready_to_send())
	var msg = DropItemCMSG.new()
	msg.ItemId = item_id
	msg.Amount = amount
	msg.UniqueId = unique_id
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


## Asks the server to wear a held item. [param slot] is an EquipmentSlot ordinal (see
## Game/Item/equipment_slot.gd). [param unique_id] may be 0 when the client does not know the
## item instance id yet - the server then picks any held copy. The server may refuse: it answers
## with an OperationError plus a fresh EquipmentComponentSMSG, so never assume success locally.
func equip_item(item_id: int, unique_id: int, slot: int) -> void:
	assert(is_ready_to_send())
	var msg = EquipItemCMSG.new()
	msg.ItemId = item_id
	msg.UniqueId = unique_id
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


## Answers the prompt a TradeRequestSMSG raised.
func answer_trade_request(trade_id: int, accept: bool) -> void:
	assert(is_ready_to_send())
	var msg = AnswerTradeRequestCMSG.new()
	msg.TradeId = trade_id
	msg.Accept = accept
	_socket.SendMessage(msg)


## Puts an item into our side of the trade. Nothing is applied locally: the answering TradeStateSMSG is what
## draws it, which is also how a refusal corrects itself.
func offer_trade_item(trade_id: int, item_id: int, unique_id: int, amount: int) -> void:
	assert(is_ready_to_send())
	var msg = OfferTradeItemCMSG.new()
	msg.TradeId = trade_id
	msg.ItemId = item_id
	msg.UniqueId = unique_id
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


## Requests the server to create a new master (character) for the current account.
## The result arrives asynchronously via the operation_success / operation_error signals.
## [param body], [param face] and [param hair] are the proto enum values. [param spawn_point_id] is
## the id of a MasterSpawnPointCandidate from [signal master_info_received]'s SpawnPoints and is
## mandatory - there is no world-default spawn, the server refuses a request that names none.
## [param effort_values] is the starting status value per attribute, keyed by
## StatusAttribute.field_key(). It must spend the creation budget exactly, or the server refuses the
## request - CreateNewMaster keeps its Create button disabled until it does.
func create_master(character_name: String, body: int, face: int, hair: int, hair_color: Color, skin_color: Color, spawn_point_id: int, effort_values: Dictionary) -> void:
	assert(is_ready_to_send())
	var msg = CreateMasterCMSG.new()
	msg.Name = character_name
	msg.Body = body
	msg.Face = face
	msg.Hair = hair
	msg.HairColor = hair_color
	msg.SkinColor = skin_color
	msg.SpawnPointId = spawn_point_id
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
	SceneManager.goto_scene("res://Game/Game.tscn")


# Is it maybe a better approach to just call into C# instead of building the msg
# object here and then calling in? Could also save a few messages we would need to build.
func get_all_entities() -> void:
	assert(is_ready_to_send())
	var msg = GetAllEntities.new()
	_socket.SendMessage(msg)


func _on_bnet_socket_message_received(message: Object) -> void:
	if message is AuthenticationSuccess:
		# if auth successfull we are now considered fully authed and can unblock the scene
		# loader to go into the master selection screen.
		assert(_connection_state == ConnectionState.CONNECTED_NOT_AUTHED)
		_connection_state = ConnectionState.CONNECTED_AUTHED
		_http_ticket = message.HttpTicket
		SceneManager.unblock_transition()
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
	elif message is ChatSMSG:
		chat_received.emit(message)
	elif message is DialogSMSG:
		dialog_received.emit(message)
	elif message is CraftableRecipesSMSG:
		craftable_recipes_received.emit(message)
	elif message is OperationSuccess:
		operation_success.emit(message)
	elif message is OperationError:
		operation_error.emit(message)
	elif message is TradeRequestSMSG:
		trade_request_received.emit(message)
	elif message is TradeStateSMSG:
		trade_state_received.emit(message)
	elif message is MapSMSG:
		# Terrain traffic. ChunkStreamManager subscribes to MessageReceived itself, and a signal fans out to
		# every listener, so these reach this handler too and have to be ignored rather than reported.
		pass
	else:
		printerr("ConnectionManager: message was not identified and processed: %s" % message)


### If we dont receive a periodically pong from the server after we send out a ping assume a disconnect.
func _on_pong() -> void:
	print("pong received")


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
		# The zone forgot this ticket the moment the socket died, so holding on to it would only let the map
		# present a credential that is already refused.
		_http_ticket = ""
		if _intentional_disconnect:
			# Player-initiated logout: go home quietly instead of showing "connection lost".
			_intentional_disconnect = false
			SceneManager.goto_scene("res://Menu/Main/Main.tscn")
		else:
			_goto_connection_lost(ConnectionError.ZONE_CONNECTION_LOST)
	elif status == 1:
		if _connection_state == ConnectionState.DISCONNECTED:
			# we are initially connected now send auth, please handle this better.
			_connection_state = ConnectionState.CONNECTED_NOT_AUTHED
			var auth_msg = Authentication.new(
				_login_token,
				SettingsManager.version
			)
			_socket.SendMessage(auth_msg)
	elif status == 2:
		# socket tries to connect
		pass
	else:
		printerr("Unknown connection status (expected 0-2): ", status)


func _on_ping_timer_timeout() -> void:
	if _connection_state == ConnectionState.CONNECTED_AUTHED:
			var ping_msg = Ping.new()
			_socket.SendMessage(ping_msg)
