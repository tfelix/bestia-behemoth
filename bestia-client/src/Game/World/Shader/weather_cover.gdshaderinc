// How much snow and wet lie on a surface, shared by the weather overlay and the entity uber material so a
// prop and the character standing next to it agree.

global uniform float weather_wetness;
#include "res://Game/World/Shader/weather_snow.gdshaderinc"

// A coarse speckle on the edge of the mask, so snow thins out in patches rather than along a clean line.
float weather_speckle(vec3 world_pos) {
	return fract(sin(dot(floor(world_pos.xz * 8.0 + world_pos.y * 3.0), vec2(12.9898, 78.233))) * 43758.5453);
}

// x is the snow lying on the surface, y the wetness of whatever snow leaves bare, both 0 to 1.
// [param up] is the world-space normal's y, [param snow_share] how much of the snow the surface keeps.
vec2 weather_cover(vec3 world_pos, float up, float snow_share, float melt_celsius) {
	float snow = weather_snow_cover(world_pos.y, melt_celsius) * snow_share
		* smoothstep(0.15, 0.55, up + (weather_speckle(world_pos) - 0.5) * 0.3);
	float wet = clamp(weather_wetness, 0.0, 1.0) * (1.0 - snow);
	return vec2(snow, wet);
}
