// The snow rule every surface shares, so a roof, a tuft of grass and the ground beside them agree.
// The globals are published by WeatherState.

global uniform float weather_snow;
global uniform float weather_temperature;
// World heights where the server's replayed snowpack lies in patches and in full; far above everything when
// no snow lies in the region.
global uniform float weather_snow_line;
global uniform float weather_snow_full;

// How much snow lies at a world height, 0 to 1. Falling snow settles only where it is cold enough; the
// server already melted its snowpack by each height's own temperature, so the air here does not gate that.
float weather_snow_cover(float height, float melt_celsius) {
	float cold = smoothstep(melt_celsius + 3.0, melt_celsius, weather_temperature);
	float falling = clamp(weather_snow, 0.0, 1.0) * cold;
	float lying = smoothstep(weather_snow_line, weather_snow_full, height);
	return max(falling, lying);
}
