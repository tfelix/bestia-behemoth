shader_type canvas_item;

// Black everywhere but a small circle around the screen centre, where the played character stands.
uniform float clear_radius = 0.12;
uniform float edge = 0.18;

void fragment() {
	vec2 from_centre = UV - vec2(0.5);
	from_centre.x *= SCREEN_PIXEL_SIZE.y / SCREEN_PIXEL_SIZE.x;
	float darkness = smoothstep(clear_radius, clear_radius + edge, length(from_centre));
	COLOR = vec4(0.0, 0.0, 0.0, darkness * 0.97);
}
