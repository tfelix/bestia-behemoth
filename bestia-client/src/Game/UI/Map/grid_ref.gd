class_name GridRef

## Short map references like [code]H12 41 37[/code]; a copy of the server's [code]GridRef.kt[/code], held to
## the same cases in [code]shared/src/test/resources/gridref-cases.tsv[/code]. Squares are 8 km, named by a
## column letter from the west and a row number from the south; inside one, equal halves of digits east then
## north, more digits for a finer cell.

const SQUARE_METRES := 8000.0
const MAX_DIGITS := 4

static var _pattern: RegEx


## [param digits] per half, 1 (1 km) to 4 (1 m). The world wraps, so any coordinate has a reference.
static func format(east_metres: float, north_metres: float, world_width: float, world_height: float,
		digits: int = 2) -> String:
	var east := fposmod(east_metres, world_width)
	var north := fposmod(north_metres, world_height)
	var column := int(east / SQUARE_METRES)
	var row := int(north / SQUARE_METRES)

	var cell := _cell_metres(digits)
	var in_east := int(floorf((east - column * SQUARE_METRES) / cell))
	var in_north := int(floorf((north - row * SQUARE_METRES) / cell))

	return "%s%d %s %s" % [column_name(column), row + 1, str(in_east).pad_zeros(digits),
			str(in_north).pad_zeros(digits)]


## The centre of the cell [param text] names, as [code]{east, north, precision}[/code] in metres; empty when
## it is not a reference or names a square or cell outside the world.
static func parse(text: String, world_width: float, world_height: float) -> Dictionary:
	if _pattern == null:
		_pattern = RegEx.create_from_string("^([A-Za-z]+)\\s*(\\d+)(?:\\s+(\\d+)(?:\\s+(\\d+))?)?$")
	var match := _pattern.search(text.strip_edges())
	if match == null:
		return {}

	var columns := ceili(world_width / SQUARE_METRES)
	var rows := ceili(world_height / SQUARE_METRES)
	var column := column_index(match.get_string(1).to_upper())
	var row := match.get_string(2).to_int() - 1
	if column >= columns or row < 0 or row >= rows:
		return {}

	var first := match.get_string(3)
	var second := match.get_string(4)
	if first.is_empty():
		return {
			"east": minf(column * SQUARE_METRES + SQUARE_METRES / 2.0, world_width),
			"north": minf(row * SQUARE_METRES + SQUARE_METRES / 2.0, world_height),
			"precision": SQUARE_METRES,
		}

	var east_digits := first
	var north_digits := second
	if second.is_empty():
		if first.length() % 2 != 0:
			return {}
		east_digits = first.substr(0, first.length() / 2)
		north_digits = first.substr(first.length() / 2)
	if east_digits.length() != north_digits.length() or east_digits.length() > MAX_DIGITS:
		return {}

	var cell := _cell_metres(east_digits.length())
	var in_east := east_digits.to_int() * cell
	var in_north := north_digits.to_int() * cell
	if in_east >= SQUARE_METRES or in_north >= SQUARE_METRES:
		return {}

	var east := column * SQUARE_METRES + in_east
	var north := row * SQUARE_METRES + in_north
	if east >= world_width or north >= world_height:
		return {}

	return {"east": east + cell / 2.0, "north": north + cell / 2.0, "precision": cell}


## Spreadsheet-style: 0 is A, 25 is Z, 26 is AA.
static func column_name(index: int) -> String:
	var n := index + 1
	var name := ""
	while n > 0:
		var rest := (n - 1) % 26
		name = String.chr(65 + rest) + name
		n = (n - 1) / 26
	return name


static func column_index(name: String) -> int:
	var n := 0
	for c in name:
		n = n * 26 + (c.unicode_at(0) - 64)
	return n - 1


static func _cell_metres(digits: int) -> float:
	return pow(10.0, MAX_DIGITS - digits)
