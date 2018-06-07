export class BootScene extends Phaser.Scene {
  constructor() {
    super({
      key: 'BootScene'
    });
  }

  public preload(): void {
    // Load Player Sprite
    this.load.json('player_1_desc', '../assets/sprites/mob/player_1/player_1_desc.json');
    this.load.atlas(
      'player_1',
      '../assets/sprites/mob/player_1/player_1.png',
      '../assets/sprites/mob/player_1/player_1.json'
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

    const additionalObjects = ['tree'];
    additionalObjects.forEach(x => {
      const baseUrl = `../assets/sprites/object/${x}`;
      const pngUrl = `${baseUrl}/${x}.png`;
      const jsonUrl = `${baseUrl}/${x}.json`;
      this.load.atlas(x, pngUrl, jsonUrl);
      this.load.json(`${x}_desc`, `${baseUrl}/${x}_desc.json`);
    });

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
