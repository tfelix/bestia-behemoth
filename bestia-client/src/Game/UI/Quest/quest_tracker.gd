class_name QuestTracker
extends VBoxContainer

## The tracked quests on the HUD, one title and one line each: progress, what the master carries, time left.
## Reads the log from [QuestLogPanel] and redraws when it or the bag changes.

## Time left moves on its own, so the lines are redrawn this often even when nothing else changed.
const _REFRESH_SECONDS := 30.0

var quest_log: QuestLogPanel:
	set(value):
		quest_log = value
		quest_log.quests_changed.connect(_redraw)
		_redraw()

var inventory: Inventory:
	set(value):
		inventory = value
		inventory.inventory_updated.connect(_redraw)


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_theme_constant_override("separation", 1)
	var timer := Timer.new()
	timer.wait_time = _REFRESH_SECONDS
	timer.autostart = true
	timer.timeout.connect(_redraw)
	add_child(timer)


func _redraw() -> void:
	for child in get_children():
		if child is Label:
			child.queue_free()
	if quest_log == null:
		return

	for quest in quest_log.tracked_quests():
		add_child(_line(QuestStyle.title_of(quest), 13, QuestStyle.level_colour(quest.SuggestedLevel)))
		for objective in quest.Objectives:
			if objective.Stage != quest.Stage:
				continue
			if not objective.Text.Key.is_empty():
				add_child(_line("   " + DialogText.resolve_ref(objective.Text), 11, UiStyle.MUTED))
				continue
			var progress := tr("QUEST_TRACKER_LINE") % [
				objective.Progress, objective.Required, quest_log.carried(objective), QuestStyle.time_left(quest)
			]
			add_child(_line("   " + progress, 11, UiStyle.MUTED))


func _line(text: String, size: int, colour: Color) -> Label:
	var label := UiStyle.label(text, size, colour)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	label.add_theme_constant_override("outline_size", 4)
	label.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.8))
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return label
