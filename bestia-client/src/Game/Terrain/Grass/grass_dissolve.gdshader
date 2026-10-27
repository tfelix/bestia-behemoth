// grass.gdshader with blades dissolving in at the reveal front instead of growing. A shader of its own because
// the discard it needs costs every blade drawn with it, revealing or not. `TerrainGrass.DissolveReveal` swaps it in.
shader_type spatial;
render_mode cull_disabled;

#define GRASS_DISSOLVE
#include "res://Game/Terrain/Grass/grass.gdshaderinc"
