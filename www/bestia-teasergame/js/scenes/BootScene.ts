export class BootScene extends Phaser.Scene {
  constructor() {
    super({
      key: 'BootScene'
    });
  }

  public preload(): void {
    // Load Player Sprite
    this.load.json('mastersmith_desc', '../assets/sprites/mob/mastersmith/mastersmith_desc.json');
    this.load.atlas(
      'mastersmith',
      '../assets/sprites/mob/mastersmith/mastersmith.png',
      '../assets/sprites/mob/mastersmith/mastersmith.json'
    );

    this.load.atlas(
      'female_01',
      '../assets/sprites/partials/female_01/female_01.png',
      '../assets/sprites/partials/female_01/female_01.json'
    );
    this.load.json(
      'female_01_desc',
      '../assets/sprites/partials/female_01/female_01_desc.json'
    );
    this.load.json(
      'offset_female_01_mastersmith',
      '../assets/sprites/partials/female_01/offset_female_01_mastersmith.json'
    );

    this.load.json('poring_desc', '../assets/sprites/mob/poring/poring_desc.json');
    this.load.atlas(
      'poring',
      '../assets/sprites/mob/poring/poring.png',
      '../assets/sprites/mob/poring/poring.json'
    );

    this.load.json('vitata_desc', '../assets/sprites/mob/vitata/vitata_desc.json');
    this.load.atlas(
      'vitata',
      '../assets/sprites/mob/vitata/vitata.png',
      '../assets/sprites/mob/vitata/vitata.json'
    );

    // Load Music

    // Load Tileset + Tilesheet
    this.load.image('tiles', '../assets/tilemap/tiles/trees_plants_rocks.png');
    this.load.tilemapTiledJSON('map', '../assets/tilemap/maps/demo.json');

    // Tree Sprite

    // Misc
    this.load.glsl('shaderTest', '../assets/shader/test.frag');
  }

  public update(): void {
    this.scene.start('GameScene');
  }
}
