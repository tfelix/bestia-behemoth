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

enum ConnectionState {DISCONNECTED, CONNECTED_NOT_AUTHED, CONNECTED_AUTHED}

@onready var socket = $BnetSocket
@onready var login_request = $LoginRequest


var Authentication = load("res://Bnet/Message/Authentication.cs")
var GetMasterCMSG = load("res://Bnet/Message/Master/GetMasterCMSG.cs")
var GetSelfCMSG = load("res://Bnet/Message/Master/GetSelfCMSG.cs")
var SelectMasterCMSG = load("res://Bnet/Message/Master/SelectMasterCMSG.cs")
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
var Ping = load("res://Bnet/Message/Ping.cs")

var _connection_state : ConnectionState = ConnectionState.DISCONNECTED

# Signed JWT obtained from the login server, sent to the zone during the auth handshake.
var _login_token: String = ""


var selected_master_info: MasterInfo = null


func disconnect_from_server() -> void:
	socket.DisconnectFromServer()


func login() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	# Exchange the configured static development credentials for a signed JWT at the login server.
	# Once we have the token we connect to the zone and send it during the auth handshake.
	var url = SettingsManager.login_server_url + "/api/v1/auth/static"
	var headers = ["Content-Type: application/json"]
	var payload = JSON.stringify({
		"username": SettingsManager.dev_username,
		"token": SettingsManager.dev_static_token
	})
	var err = login_request.request(url, headers, HTTPClient.METHOD_POST, payload)
	if err != OK:
		printerr("ConnectionManager: could not start login request: ", err)
		_goto_connection_lost()


# TODO move the code into a seperate script on the LoginRequest node to tidy the connection manager up.
func _on_login_request_completed(result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		printerr("ConnectionManager: login failed (result=%s, code=%s)" % [result, response_code])
		_goto_connection_lost()
		return

	var json = JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_DICTIONARY or not json.data.has("token"):
		printerr("ConnectionManager: could not parse login response")
		_goto_connection_lost()
		return

	_login_token = json.data["token"]

	# We have a valid JWT now. Go to master select (blocked until the zone confirms auth) and connect.
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn", true)
	socket.ConnectToServer()


func _goto_connection_lost() -> void:
	SceneManager.goto_scene("res://Menu/ConnectionLost/ConnectionLost.tscn")


func list_bestia_master() -> void:
	assert(is_ready_to_send())
	var msg = GetMasterCMSG.new()
	socket.SendMessage(msg)


func get_self() -> void:
	assert(is_ready_to_send())
	var msg = GetSelfCMSG.new()
	socket.SendMessage(msg)


func get_inventory() -> void:
	assert(is_ready_to_send())
	var msg = GetInventoryCMSG.new()
	socket.SendMessage(msg)


func get_skills() -> void:
	assert(is_ready_to_send())
	var msg = GetSkillsCMSG.new()
	socket.SendMessage(msg)


func activate_skill(attack_id: int, skill_level: int, target_position: Vector3 = Vector3.ZERO, target_entity_id: int = 0) -> void:
	assert(is_ready_to_send())
	var msg = ActivateSkillCMSG.new()
	msg.AttackId = attack_id
	msg.SkillLevel = skill_level
	msg.TargetPosition = target_position
	msg.TargetEntityId = target_entity_id
	socket.SendMessage(msg)


## Spends one or more skill points across one or more skill tree nodes in a single batch.
## [param investments] is an Array of {"attack_id": int, "amount": int} dictionaries.
func invest_skill_points(investments: Array) -> void:
	assert(is_ready_to_send())
	var msg = InvestSkillPointCMSG.new()
	var points: Array = []
	for investment in investments:
		points.append({"attack_id": investment["attack_id"], "amount": investment["amount"]})
	msg.InvestedPoints = points
	socket.SendMessage(msg)


## Moves the active entity towards a ground position, walking there tile by tile.
func move_to(destination: Vector3) -> void:
	assert(is_ready_to_send())

	var entity_manager = get_tree().get_first_node_in_group("entity_manager")
	var owned_entity = entity_manager.get_owned_entity() if entity_manager else null
	if owned_entity == null:
		printerr("ConnectionManager: cannot move, no owned entity yet")
		return

	var path := PathCalculator.calculate_tile_path(owned_entity.global_position, destination)
	if path.is_empty():
		return

	var msg = MoveActiveEntityCMSG.new()
	msg.Path = path
	socket.SendMessage(msg)


func use_item(item_id: int) -> void:
	assert(is_ready_to_send())
	var msg = UseItemCMSG.new()
	msg.ItemId = item_id
	socket.SendMessage(msg)


func drop_item(item_id: int, amount: int) -> void:
	assert(is_ready_to_send())
	var msg = DropItemCMSG.new()
	msg.ItemId = item_id
	msg.Amount = amount
	socket.SendMessage(msg)


func loot_item(entity_id: int) -> void:
	assert(is_ready_to_send())
	var msg = LootItemCMSG.new()
	msg.EntityId = entity_id
	socket.SendMessage(msg)


func send_chat(text: String, mode: int = 3, target_player: String = "") -> void:
	assert(is_ready_to_send())
	var msg = ChatCMSG.new()
	msg.Text = text
	msg.ChatMode = mode
	if target_player != "":
		msg.TargetPlayerName = target_player
	socket.SendMessage(msg)


func send_attack_entity(entity_id: int, attack_id: int, skill_level: int) -> void:
	assert(is_ready_to_send())
	var msg = AttackEntityCMSG.new()
	msg.EntityId = entity_id
	msg.UsedAttackId = attack_id
	msg.SkillLevel = skill_level
	socket.SendMessage(msg)


## Select the bestia master given by the id
func select_bestia_master(master_info: MasterInfo) -> void:
	assert(is_ready_to_send())
	selected_master_info = master_info
	var msg = SelectMasterCMSG.new()
	msg.MasterId = master_info.MasterId
	socket.SendMessage(msg)
	SceneManager.goto_scene("res://Game/Game.tscn")


# Is it maybe a better approach to just call into C# instead of building the msg
# object here and then calling in? Could also save a few messages we would need to build.
func get_all_entities() -> void:
	assert(is_ready_to_send())
	var msg = GetAllEntities.new()
	socket.SendMessage(msg)


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
	elif message is EntitySMSG:
		entity_received.emit(message)
	elif message is SelfSMSG:
		self_received.emit(message)
	elif message is ChatSMSG:
		chat_received.emit(message)
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
		SceneManager.goto_scene("res://Menu/ConnectionLost/ConnectionLost.tscn")
	elif status == 1:
		# TODO maybe use assert instead of if?
		if _connection_state == ConnectionState.DISCONNECTED:
			# we are connected now send auth, please handle this better.
			_connection_state = ConnectionState.CONNECTED_NOT_AUTHED
			var auth_msg = Authentication.new(
				_login_token,
				SettingsManager.version
			)
			socket.SendMessage(auth_msg)
	elif status == 2:
		# socket tries to connect
		pass
	else:
		printerr("Unknown connection status (expected 0-2): ", status)


func _on_ping_timer_timeout() -> void:
	if _connection_state == ConnectionState.CONNECTED_AUTHED:
			var ping_msg = Ping.new()
			socket.SendMessage(ping_msg)
