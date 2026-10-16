extends PanelContainer
class_name HomeCard

## One home a new master could start in, as a selectable card.

signal chosen(card: HomeCard)

var spawn_point_id := 0

@onready var _name_label: Label = %NameLabel
@onready var _suggested_label: Label = %SuggestedLabel
@onready var _tier_label: Label = %TierLabel
@onready var _history_label: Label = %HistoryLabel
@onready var _population_label: Label = %PopulationLabel
@onready var _position_label: Label = %PositionLabel
@onready var _biome_label: Label = %BiomeLabel


## Call once the card is in the tree. Every text but the settlement's own name is translated here, so
## the server's facts read in the player's language.
func show_candidate(candidate: MasterSpawnPointCandidate, suggested: bool) -> void:
	spawn_point_id = candidate.Id
	_name_label.text = candidate.SettlementName
	_suggested_label.visible = suggested
	_tier_label.text = tr("SETTLEMENT_TIER_%s" % candidate.Tier.to_upper())
	_population_label.text = str(candidate.Population)
	_position_label.text = "%s / %s" % [candidate.X, candidate.Y]
	_biome_label.text = tr(candidate.BiomeToken)

	var history: Array = Array(candidate.History).map(
		func(line): return DialogText.resolve_line(line.Key, Array(line.Args))
	)
	_history_label.text = " ".join(history)
	_history_label.visible = not history.is_empty()


func set_selected(selected: bool) -> void:
	theme_type_variation = "HomeCardSelected" if selected else "HomeCard"


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		chosen.emit(self)
		accept_event()
