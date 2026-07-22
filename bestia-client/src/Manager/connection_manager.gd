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
signal operation_success(message: OperationSuccess)
signal operation_error(message: OperationError)
## Emitted whenever the server re-syncs the pending logout countdown (seconds until despawn).
signal logout_countdown_received(remaining_seconds: float)
## Emitted when the server aborts a pending logout (player moved / used a skill / took damage).
signal logout_cancelled()

enum ConnectionState {DISCONNECTED, CONNECTED_NOT_AUTHED, CONNECTED_AUTHED}
enum ConnectionError {NO_ERROR, LOGIN_OFFLINE, LOGIN_ERROR, ZONE_CONNECTION_LOST}

@onready var _socket = $BnetSocket
@onready var _login_request = $LoginRequest


var Authentication = load("res://Bnet/Message/Authentication.cs")
var GetMasterCMSG = load("res://Bnet/Message/Master/GetMasterCMSG.cs")
var GetSelfCMSG = load("res://Bnet/Message/Master/GetSelfCMSG.cs")
var SelectMasterCMSG = load("res://Bnet/Message/Master/SelectMasterCMSG.cs")
var CreateMasterCMSG = load("res://Bnet/Message/Master/CreateMasterCMSG.cs")
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
var Ping = load("res://Bnet/Message/Ping.cs")

var _connection_state : ConnectionState = ConnectionState.DISCONNECTED

# Signed JWT obtained from the login server, sent to the zone during the auth handshake.
var _login_token: String = ""

var last_connection_error: ConnectionError = ConnectionError.NO_ERROR
var selected_master_info: MasterInfo = null

# Set while we deliberately drop the connection (logout to main menu) so the socket-closed handler
# routes to the main menu instead of the "connection lost" screen.
var _intentional_disconnect: bool = false


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

	_login_token = json.data["token"]

	# We have a valid JWT now. Go to master select (blocked until the zone confirms auth) and connect.
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn", true)
	_socket.ConnectToServer()


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

	var path := PathCalculator.calculate_tile_path(owned_entity.global_position, destination)
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


func drop_item(item_id: int, amount: int) -> void:
	assert(is_ready_to_send())
	var msg = DropItemCMSG.new()
	msg.ItemId = item_id
	msg.Amount = amount
	_socket.SendMessage(msg)


func loot_item(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = LootItemCMSG.new()
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


func send_chat(text: String, mode: int = 3, target_player: String = "") -> void:
	assert(is_ready_to_send())
	var msg = ChatCMSG.new()
	msg.Text = text
	msg.ChatMode = mode
	if target_player != "":
		msg.TargetPlayerName = target_player
	_socket.SendMessage(msg)


func send_attack_entity(entity_id: int, attack_id: int, skill_level: int) -> void:
	assert(is_ready_to_send())
	var msg = AttackEntityCMSG.new()
	msg.EntityId = entity_id
	msg.UsedAttackId = attack_id
	msg.SkillLevel = skill_level
	_socket.SendMessage(msg)


## Requests the server to create a new master (character) for the current account.
## The result arrives asynchronously via the operation_success / operation_error signals.
## [param body], [param face] and [param hair] are the proto enum values.
func create_master(character_name: String, body: int, face: int, hair: int, hair_color: Color, skin_color: Color) -> void:
	assert(is_ready_to_send())
	var msg = CreateMasterCMSG.new()
	msg.Name = character_name
	msg.Body = body
	msg.Face = face
	msg.Hair = hair
	msg.HairColor = hair_color
	msg.SkinColor = skin_color
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
	elif message is OperationSuccess:
		operation_success.emit(message)
	elif message is OperationError:
		operation_error.emit(message)
	else:
		printerr("ConnectionManager: message was not identified and processed.")


### If we dont receive a periodically pong from the server after we send out a ping assume a disconnect.
func _on_pong() -> void:
	print("pong received")


func is_ready_to_send() -> bool:
	return _connection_state == ConnectionState.CONNECTED_AUTHED


func _on_bnet_socket_connection_status_changed(status: int) -> void:
	if status == 0:
		# connection was closed, perform cleanup and inform the user.
		_connection_state = ConnectionState.DISCONNECTED
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
