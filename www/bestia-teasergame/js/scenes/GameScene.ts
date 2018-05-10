import { EntityStore } from '../entities/EntityStore';
import { Entity } from '../entities/Entity';
import { VisualComponentRenderer } from '../engine/component/VisualComponentRenderer';
import { VisualComponent, SpriteType } from '../entities/components/VisualComponent';
import { PositionComponent } from '../entities/components/PositionComponent';
import { Point } from '../entities/Point';
import { EntityRenderer } from '../engine/EntityRenderer';

export class GameScene extends Phaser.Scene {
  private scoreText: Phaser.GameObjects.Text[];
  private controls: Phaser.Cameras.Controls.FixedKeyControl;

  private entityStore: EntityStore;
  private entityRenderer: EntityRenderer;

  // private renderer = new EntityRenderer();

  constructor() {
    super({
      key: "GameScene"
    });
  }

  init(entityStore: EntityStore): void {
    this.entityStore = new EntityStore();
    this.setupTestEnv();
  }

  setupTestEnv() {
    const entity = new Entity(1);
    this.entityStore.addEntity(entity);
    const visual = new VisualComponent(
      1,
      1,
      true,
      "mastersmith",
      SpriteType.MULTI
    );
    entity.addComponent(visual);

    const position = new PositionComponent(
      2,
      1,
      new Point(5, 5)
    );
    entity.addComponent(position);
  }

  create(): void {
    this.entityRenderer = new EntityRenderer(this, this.entityStore);

    // Setup tilemap
    var map = this.make.tilemap({ key: 'map' });
    var floorTiles = map.addTilesetImage('trees_plants_rocks', 'tiles');
    map.createStaticLayer('floor_0', floorTiles, 0, 0);
    map.createStaticLayer('floor_1', floorTiles, 0, 0);

    this.cameras.main.setBounds(0, 0, map.widthInPixels, map.heightInPixels);

    var cursors = this.input.keyboard.createCursorKeys();

    var controlConfig = {
      camera: this.cameras.main,
      left: cursors.left,
      right: cursors.right,
      up: cursors.up,
      down: cursors.down,
      speed: 0.5
    };

    this.controls = new Phaser.Cameras.Controls.Fixed(controlConfig);

    // TEST
    const cursor = this.add.sprite(320, 320, 'indicator_move');
    var config = {
      key: 'cursor_anim',
      frames: this.anims.generateFrameNumbers('indicator_move', { start: 0, end: 1 }),
      frameRate: 1,
      repeat: -1
    };
    this.anims.create(config);
    cursor.anims.play('cursor_anim');
  }

  update(time, delta): void {
    this.controls.update(delta);

    this.entityRenderer.update();
  }
}
