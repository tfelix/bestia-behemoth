import { EntityStore } from '../entities/EntityStore';
import { Entity } from '../entities/Entity';
import { VisualComponentRenderer } from '../engine/component/VisualComponentRenderer';
import { VisualComponent, SpriteType } from '../entities/components/VisualComponent';
import { PositionComponent } from '../entities/components/PositionComponent';
import { Point } from '../entities/Point';
import { EntityRenderer } from '../engine/EntityRenderer';
import { EngineContext } from '../engine/EngineContext';
import {PointerManager } from '../engine/pointer/PointerManager';

export class GameScene extends Phaser.Scene {
  private scoreText: Phaser.GameObjects.Text[];
  private controls: Phaser.Cameras.Controls.FixedKeyControl;

  private entityStore: EntityStore;
  private entityRenderer: EntityRenderer;
  private engineContext: EngineContext;
  private pointerManager: PointerManager;

  constructor() {
    super({
      key: "GameScene"
    });
  }

  init(entityStore: EntityStore): void {
    this.entityStore = new EntityStore();
    this.engineContext = new EngineContext(this);
    this.entityRenderer = new EntityRenderer(this, this.entityStore);
    this.pointerManager = new PointerManager(this.engineContext);
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

  preload(): void {
    

    this.pointerManager.load(this.load);
  }

  create() {
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
    this.pointerManager.create();

    this.add.text(100, 200, 'Phaser', { fontFamily: 'Arial', fontSize: 12, color: '#ffff00' });
  }

  update(time, delta) {
    this.controls.update(delta);

    this.entityRenderer.update();
  }
}
