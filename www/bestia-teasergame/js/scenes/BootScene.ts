/**
 * @author       Digitsensitive <digit.sensitivee@gmail.com>
 * @copyright    2018 Digitsensitive
 * @description  Flappy Bird: Boot Scene
 * @license      Digitsensitive
 */

export class BootScene extends Phaser.Scene {
  constructor() {
    super({
      key: "BootScene"
    });
  }

  preload(): void {
    // Load Player Sprite
    this.load.json('mastersmith_desc', '../assets/sprites/mob/mastersmith/mastersmith_desc.json');
    this.load.atlas('mastersmith', '../assets/sprites/mob/mastersmith/mastersmith.png', '../assets/sprites/mob/mastersmith/mastersmith.json');

    // Load Music

    // Load Tileset + Tilesheet
    this.load.image('tiles', '../assets/tilemap/tiles/trees_plants_rocks.png');
    this.load.tilemapTiledJSON('map', '../assets/tilemap/maps/demo.json');

    // Tree Sprite

    // Misc
    this.load.glsl('shaderTest', '../assets/shader/test.frag');
  }

  update(): void {
    this.scene.start("GameScene");
  }
}
