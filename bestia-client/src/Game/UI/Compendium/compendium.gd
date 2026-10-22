class_name CompendiumPanel
extends PanelContainer

## The Bestia Compendium: every species number, blacked out until the master or one of its bestias has seen
## one. The species come from the local [BestiaDB]; the server only says which of them this master knows.
##
## Built in code, like [PartyPanel].

## Something the player should be told, in the chat's notice colour.
signal notice(text: String)

const PARCHMENT := Color(0.91, 0.86, 0.74)
## An unseen species' portrait is modulated to this, which leaves its outline on the parchment.
const SILHOUETTE := Color(0.0, 0.0, 0.0, 0.85)

const _DETAIL_PORTRAIT_SIZE := 150

var _searcher := CompendiumSearcher.new()
## Every species the compendium lists, by number.
var _species: Array[BestiaResource] = []
## species id -> CompendiumEntry, for each species this master has seen
var _known: Dictionary[int, CompendiumEntry] = {}
## False until the first book arrives, so the entries already in it are not announced as new.
var _has_book := false
var _rows: Dictionary[int, CompendiumRow] = {}
var _selected_id := 0

var _search: LineEdit
var _tabs: TabBar
var _counter: Label
var _no_match: Label
## The portrait, and beside it the live model.
var _pictures: HBoxContainer
var _detail_portrait: TextureRect
var _detail_name: Label
var _detail_subtitle: Label
var _detail_caught: Label
var _detail_habitat: Label
var _detail_description: Label


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(640, 460)
	_species = BestiaDB.get_instance().listed_species()
	_build()
	_refresh_rows()
	if not _species.is_empty():
		_select(_species[0].bestia_id)

	ConnectionManager.compendium_received.connect(_on_compendium)
	visibility_changed.connect(_on_visibility_changed)
	# Asked once loaded, like the party: a book pushed while the game scene was coming up reached nobody.
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.request_compendium()


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 10, 8, 10, 10))

	var header := UiStyle.hbox(8)
	body.add_child(header)

	_search = LineEdit.new()
	_search.placeholder_text = tr("COMPENDIUM_SEARCH")
	_search.clear_button_enabled = true
	_search.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_search.text_changed.connect(func(_text: String) -> void: _refresh_rows())
	header.add_child(_search)

	_tabs = TabBar.new()
	_tabs.clip_tabs = false
	_tabs.add_tab(tr("COMPENDIUM_TAB_ALL"))
	_tabs.add_tab(tr("COMPENDIUM_TAB_SEEN"))
	_tabs.add_tab(tr("COMPENDIUM_TAB_CAUGHT"))
	_tabs.tab_changed.connect(func(_tab: int) -> void: _refresh_rows())
	header.add_child(_tabs)

	_counter = UiStyle.label("", 11, UiStyle.MUTED)
	body.add_child(_counter)

	var columns := UiStyle.hbox(10)
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(columns)

	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(230, 0)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	columns.add_child(scroll)

	var list := UiStyle.vbox(3)
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	for species in _species:
		var row := CompendiumRow.new()
		row.picked.connect(_select)
		list.add_child(row)
		_rows[species.bestia_id] = row

	_no_match = UiStyle.label(tr("COMPENDIUM_NO_MATCH"), 12, UiStyle.MUTED)
	list.add_child(_no_match)

	columns.add_child(_build_detail())


func _build_detail() -> Control:
	var detail := UiStyle.vbox(6)
	detail.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	_pictures = UiStyle.hbox(8)
	detail.add_child(_pictures)

	var frame := PanelContainer.new()
	frame.add_theme_stylebox_override("panel", UiStyle.flat(PARCHMENT, 6, 6))
	_detail_portrait = TextureRect.new()
	_detail_portrait.custom_minimum_size = Vector2(_DETAIL_PORTRAIT_SIZE, _DETAIL_PORTRAIT_SIZE)
	_detail_portrait.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_detail_portrait.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	frame.add_child(_detail_portrait)
	_pictures.add_child(frame)

	_detail_name = UiStyle.label("", 18, UiStyle.ACCENT)
	detail.add_child(_detail_name)

	_detail_subtitle = UiStyle.label("", 11, UiStyle.MUTED)
	detail.add_child(_detail_subtitle)

	_detail_caught = UiStyle.label("", 12)
	detail.add_child(_detail_caught)

	var text_scroll := ScrollContainer.new()
	text_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	text_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	detail.add_child(text_scroll)

	var text := UiStyle.vbox(6)
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	text_scroll.add_child(text)

	_detail_habitat = UiStyle.label("", 12)
	_detail_habitat.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.add_child(_detail_habitat)

	_detail_description = UiStyle.label("", 12, UiStyle.TEXT)
	_detail_description.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.add_child(_detail_description)

	return detail


func _on_visibility_changed() -> void:
	if is_visible_in_tree() and ConnectionManager.is_ready_to_send():
		ConnectionManager.request_compendium()


func _on_compendium(message: CompendiumSMSG) -> void:
	var known: Dictionary[int, CompendiumEntry] = {}
	for entry in message.Entries:
		known[entry.SpeciesId] = entry

	if _has_book:
		_announce_new(known)
	_known = known
	_has_book = true

	_refresh_rows()
	_show_detail()


func _announce_new(known: Dictionary[int, CompendiumEntry]) -> void:
	for species_id in known:
		if _known.has(species_id):
			continue
		var species := BestiaDB.get_instance().get_bestia(species_id)
		if species != null:
			notice.emit(tr("COMPENDIUM_NEW_ENTRY") % tr(species.name_key))


func _refresh_rows() -> void:
	var query := _search.text
	var tab := _tabs.current_tab
	var any_shown := false

	for species in _species:
		var row := _rows[species.bestia_id]
		var entry: CompendiumEntry = _known.get(species.bestia_id)
		var seen := entry != null
		var caught := seen and entry.Caught
		var number_label := _searcher.NumberLabel(species.bestia_id)

		row.show_species(species, number_label, seen, caught)
		row.visible = _searcher.Matches(species.bestia_id, tr(species.name_key), seen, caught, tab, query)
		any_shown = any_shown or row.visible

	_no_match.visible = not any_shown
	_counter.text = tr("COMPENDIUM_COUNT") % [_known.size(), _caught_count(), _species.size()]


func _caught_count() -> int:
	var caught := 0
	for entry: CompendiumEntry in _known.values():
		if entry.Caught:
			caught += 1
	return caught


func _select(species_id: int) -> void:
	if _rows.has(_selected_id):
		_rows[_selected_id].set_selected(false)
	_selected_id = species_id
	_rows[species_id].set_selected(true)
	_show_detail()


func _show_detail() -> void:
	var species := BestiaDB.get_instance().get_bestia(_selected_id)
	if species == null:
		return

	var entry: CompendiumEntry = _known.get(_selected_id)
	var number_label := _searcher.NumberLabel(_selected_id)
	_detail_portrait.texture = species.get_portrait()

	if entry == null:
		_detail_portrait.modulate = SILHOUETTE
		_detail_name.text = tr("COMPENDIUM_UNKNOWN")
		_detail_subtitle.text = number_label
		_detail_caught.text = ""
		_detail_habitat.text = ""
		_detail_description.text = tr("COMPENDIUM_UNSEEN_HINT")
		return

	_detail_portrait.modulate = Color.WHITE
	_detail_name.text = tr(species.name_key)
	_detail_subtitle.text = "%s · %s" % [number_label, tr("BESTIA_KIND_" + species.kind)]
	_detail_caught.text = tr("COMPENDIUM_CAUGHT") if entry.Caught else tr("COMPENDIUM_NOT_CAUGHT")
	_detail_caught.add_theme_color_override("font_color", UiStyle.ACCENT if entry.Caught else UiStyle.MUTED)
	_detail_habitat.text = "%s: %s" % [tr("COMPENDIUM_HABITAT"), _habitat_text(species)]
	_detail_description.text = "%s\n\n%s" % [
		tr(species.description_key),
		tr("COMPENDIUM_FIRST_SEEN") % Time.get_date_string_from_unix_time(entry.FirstSeenUnix),
	]


func _habitat_text(species: BestiaResource) -> String:
	var biomes := species.get_habitat()
	if biomes.is_empty():
		return tr("COMPENDIUM_HABITAT_ANYWHERE")

	var names: PackedStringArray = []
	for biome in biomes:
		names.append(tr("BIOME_" + biome))
	return ", ".join(names)
