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

# I am still undecided if we should skip this message generation and instead
# just directly call into the C# side so we can skip this message generation here.
var Authentication = load("res://Bnet/Message/Authentication.cs")
var GetMasterCMSG = load("res://Bnet/Message/Master/GetMasterCMSG.cs")
var GetSelfCMSG = load("res://Bnet/Message/Master/GetSelfCMSG.cs")
var SelectMasterCMSG = load("res://Bnet/Message/Master/SelectMasterCMSG.cs")
var GetAllEntities = load("res://Bnet/Message/Entity/GetAllEntities.cs")
var AttackEntityCMSG = load("res://Bnet/Message/Entity/AttackEntityCMSG.cs")
var Ping = load("res://Bnet/Message/Ping.cs")

var _connection_state : ConnectionState = ConnectionState.DISCONNECTED


var selected_master_info: MasterInfo = null


func disconnect_from_server() -> void:
	socket.DisconnectFromServer()


func login() -> void:
	assert(_connection_state == ConnectionState.DISCONNECTED)
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn", true)
	# TODO REST handshake with the login server fetch refresh token.
	# TODO Save refresh token for later, in secure enclave? locally in setting?
	# TODO Perform login to zone, periodically send Ping to avoid DC
	socket.ConnectToServer()


func list_bestia_master() -> void:
	assert(is_ready_to_send())
	var msg = GetMasterCMSG.new()
	socket.SendMessage(msg)


func get_self() -> void:
	assert(is_ready_to_send())
	var msg = GetSelfCMSG.new()
	socket.SendMessage(msg)


func send_chat(text: String) -> void:
	assert(is_ready_to_send())
	var msg = ChatCMSG.new()
	msg.Text = text
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
				"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaXNzIjoibG9naW4iLCJhdWQiOlsiem9uZSJdLCJwZXJtaXNzaW9ucyI6W10sImlhdCI6MTc1ODIzNjQzMCwiZXhwIjoxNzU4MjM2NTUwfQ.otC5qKJPZh4EMxi2V3lDokgTaNINc5g42ErGbE5U-s0",
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
