/**
* A vertical blur filter by Mat Groves http://matgroves.com/ @Doormat23
*/
Phaser.Filter.Test = function (game) {

    Phaser.Filter.call(this, game);

    this.uniforms.color = { type: '1f', value: 0.5 };

    this.fragmentSrc = [
	
		"uniform float     time;",
		"uniform float     color;",

        "void main(void) {",

          "gl_FragColor = vec4(color, sin(time), 0.7);",

        "}"

    ];

};

Phaser.Filter.Test.prototype = Object.create(Phaser.Filter.prototype);
Phaser.Filter.Test.prototype.constructor = Phaser.Filter.Test;

Phaser.Filter.Test.prototype.init = function (color) {

    if (typeof color == 'undefined') { color = 0.5; }

    this.uniforms.color.value = color;

};