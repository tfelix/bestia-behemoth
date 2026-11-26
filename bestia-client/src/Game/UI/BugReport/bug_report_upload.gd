class_name BugReportUpload extends Node
## Sends one report to the api-server as multipart, with the connection's HTTP ticket.

signal finished(accepted: bool, status: int)

const PATH := "/v1/companion/bug-reports"

var _request: HTTPRequest


func _ready() -> void:
	_request = HTTPRequest.new()
	_request.timeout = 60.0
	add_child(_request)
	_request.request_completed.connect(_on_completed)


func is_busy() -> bool:
	return _request.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED


## [param files] maps a part name to its bytes; empty ones are left out, because the server refuses them. Returns
## false when nothing was sent: no ticket yet, or a report still on its way.
func send(fields: Dictionary, files: Dictionary) -> bool:
	var ticket: String = ConnectionManager.http_ticket()
	if ticket == "" or is_busy():
		return false

	var boundary := "bestia-report-%d-%d" % [Time.get_ticks_usec(), randi()]
	var headers := PackedStringArray([
		"Authorization: Ticket " + ticket,
		"Content-Type: multipart/form-data; boundary=" + boundary,
	])
	var url := SettingsManager.get_instance().api_server_url + PATH
	return _request.request_raw(url, headers, HTTPClient.METHOD_POST, multipart(boundary, fields, files)) == OK


static func multipart(boundary: String, fields: Dictionary, files: Dictionary) -> PackedByteArray:
	var body := PackedByteArray()
	for part_name: String in fields:
		body.append_array(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n" % [boundary, part_name]).to_utf8_buffer())
		body.append_array(str(fields[part_name]).to_utf8_buffer())
		body.append_array("\r\n".to_utf8_buffer())

	for part_name: String in files:
		var content: PackedByteArray = files[part_name]
		if content.is_empty():
			continue
		body.append_array(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n" % [boundary, part_name, part_name]).to_utf8_buffer())
		body.append_array("Content-Type: application/octet-stream\r\n\r\n".to_utf8_buffer())
		body.append_array(content)
		body.append_array("\r\n".to_utf8_buffer())

	body.append_array(("--%s--\r\n" % boundary).to_utf8_buffer())
	return body


func _on_completed(result: int, status: int, _headers: PackedStringArray, _body: PackedByteArray) -> void:
	finished.emit(result == HTTPRequest.RESULT_SUCCESS and status == 202, status)
