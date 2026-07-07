extends Window
class_name WidgetWindow

static func create(title: String, content: Control) -> WidgetWindow:
	var scn = preload("res://Game/UI/WidgetWindow/WidgetWindow.tscn").instantiate() as Window
	
	scn.title = title
	scn.add_child(content)

	return scn
