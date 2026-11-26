extends Decal

## The ground marker of a skill aimed at a spot: a faint grid with one cell per tile the cast will hit, the cell
## under the cursor a little stronger. A decal rather than a mesh, so it drapes over uneven voxel ground.

## Pixels per tile in the generated grid texture.
const _CELL_PX := 16
const _BORDER := Color(1.0, 1.0, 1.0, 0.75)
const _FILL := Color(1.0, 1.0, 1.0, 0.08)
const _CENTRE_FILL := Color(1.0, 1.0, 1.0, 0.25)

## Grid textures by cell count per side, shared by every cast: they differ only in size.
static var _grids: Dictionary = {}


func _ready() -> void:
	if texture_albedo == null:
		set_radius(0.0)


## Scales the decal's ground footprint to match an AOE skill's radius, and lays a cell on every tile in it.
##
## The footprint is 2 * radius + 1 tiles, not 2 * radius: server side a radius counts tiles in every
## direction *from the centre tile*, and that centre tile burns too - so a radius of 1 is the 3x3
## around the aiming point (see AreaEffectSystem). Decal.size is a full extent, and the vertical (.y)
## extent is left alone so only the ground footprint changes.
func set_radius(radius: float) -> void:
	var extent := radius * 2.0 + 1.0
	size = Vector3(extent, size.y, extent)
	var grid := _grid(maxi(1, roundi(extent)))
	texture_albedo = grid


static func _grid(cells: int) -> ImageTexture:
	if _grids.has(cells):
		return _grids[cells]

	var side := cells * _CELL_PX
	var image := Image.create(side, side, false, Image.FORMAT_RGBA8)
	var centre := cells / 2
	for y in side:
		for x in side:
			var cx := x % _CELL_PX
			var cy := y % _CELL_PX
			var on_border := cx == 0 or cy == 0 or cx == _CELL_PX - 1 or cy == _CELL_PX - 1
			var is_centre := x / _CELL_PX == centre and y / _CELL_PX == centre
			image.set_pixel(x, y, _BORDER if on_border else (_CENTRE_FILL if is_centre else _FILL))

	var texture := ImageTexture.create_from_image(image)
	_grids[cells] = texture
	return texture
