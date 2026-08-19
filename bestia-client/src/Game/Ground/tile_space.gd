class_name TileSpace

## The one place that knows how a tile relates to the world it is drawn in.
##
## Tile [code]n[/code] spans world [code][n, n+1][/code], so its centre is at [code]n + 0.5[/code] and
## the coordinate itself names its lowest corner. That is not a choice made here. It is what the
## terrain mesh already does - a surface nets vertex adds a [code][0,1][/code] offset to its cell
## index, see [code]SurfaceNets.EmitVertex[/code] and the note at the top of
## [code]SurfaceProbe.SurfaceAt[/code] - and what the server does too, in
## [code]WorldConfig.columnCenter[/code] and in the carve brush [code]ChunkStreamSystem[/code] aims at
## a voxel. Anything drawn on the ground has to agree with it or it lands half a tile out.
##
## [b]floor() and round() are both right, for different inputs.[/b] Conflating them is what put
## entities on tile corners and let a click highlight one tile while walking to another:
##
## - A [i]world position[/i] - a raycast hit, a node's [code]global_position[/code] - becomes a tile
##   through [method world_to_tile], which floors, because the cell containing a point is the one
##   below it.
## - A [i]tile index[/i] that happens to be fractional - an entity mid-step, whose logical position
##   lerps between two whole waypoints - becomes a whole index with a plain [code]round()[/code],
##   because the tile it is on is the nearest one, not the one below it. There is deliberately no
##   helper here for that: it would be one character different from [method world_to_tile] at the
##   call site and impossible to tell apart when reading, and putting a world position through it
##   quietly steps the answer back a tile.

## The x/z offset from a tile's coordinate to the point it is drawn at.
##
## Horizontal only. The vertical is a whole voxel index the server owns outright, and the sub-voxel
## part of it is [code]Entity[/code]'s ground correction measured against real terrain - not a
## constant, and not this one.
const CENTRE_OFFSET := Vector3(0.5, 0.0, 0.5)


## The tile containing [param world]. Whole x/z; [code]y[/code] is passed through untouched, since a
## world position's height is already the voxel index the caller wants.
static func world_to_tile(world: Vector3) -> Vector3:
	return Vector3(floorf(world.x), world.y, floorf(world.z))


## Where [param tile] gets drawn: the middle of it, rather than the corner its coordinate names.
static func tile_centre(tile: Vector3) -> Vector3:
	return tile + CENTRE_OFFSET
