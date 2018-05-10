precision mediump float;
varying vec2 v_text_coord;
uniform sample2D u_sampler;

void main(void) {
  gl_FragColor = texture2D(u_sampler, v_text_coord) * vec4(1.0, 0.0, 0.0, 1.0);
}