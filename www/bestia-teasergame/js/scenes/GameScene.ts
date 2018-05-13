import { EntityStore } from '../entities/EntityStore';
import { Entity } from '../entities/Entity';
import { VisualComponentRenderer } from '../engine/component/VisualComponentRenderer';
import { VisualComponent, SpriteType } from '../entities/components/VisualComponent';
import { PositionComponent } from '../entities/components/PositionComponent';
import { Point } from '../entities/Point';
import { EntityRenderer } from '../engine/EntityRenderer';
import { EngineContext } from '../engine/EngineContext';
import { PointerManager } from '../engine/pointer/PointerManager';
import { DebugComponent } from '../entities/components/DebugComponent';
import { CollisionManager } from '../engine/map/CollisionManager';

export class GameScene extends Phaser.Scene {
  private scoreText: Phaser.GameObjects.Text[];
  private controls: Phaser.Cameras.Controls.FixedKeyControl;

  private entityStore: EntityStore;
  private entityRenderer: EntityRenderer;
  private engineContext: EngineContext;
  private pointerManager: PointerManager;
  private collisionManager: CollisionManager;

  constructor() {
    super({
      key: 'GameScene'
    });
  }

  public init(entityStore: EntityStore): void {
    this.entityStore = new EntityStore();
    this.engineContext = new EngineContext(this);
    this.entityRenderer = new EntityRenderer(this, this.entityStore);
    this.pointerManager = new PointerManager(this.engineContext);
    this.collisionManager = new CollisionManager(this.engineContext);
    this.setupTestEnv();
  }

  public setupTestEnv() {
    const entity = new Entity(1);
    this.entityStore.addEntity(entity);
    const visual = new VisualComponent(
      1,
      1,
      true,
      'mastersmith',
      SpriteType.MULTI
    );
    entity.addComponent(visual);

    const position = new PositionComponent(
      2,
      1,
      new Point(2, 2)
    );
    entity.addComponent(position);

    const vitata = new Entity(2);
    this.entityStore.addEntity(vitata);
    const vitataVisual = new VisualComponent(
      10,
      10,
      true,
      'vitata',
      SpriteType.MULTI
    );
    vitataVisual.animation = 'stand_down';
    vitata.addComponent(vitataVisual);

    const vitataPosition = new PositionComponent(
      11,
      10,
      new Point(5, 4)
    );
    vitata.addComponent(vitataPosition);
    // entity.addComponent(new DebugComponent(3, 1));*/
  }

  public preload(): void {
    this.pointerManager.load(this.load);
  }

  public create() {
    this.engineContext.game.input.mouse.disableContextMenu();

    // Setup tilemap
    const map = this.make.tilemap({ key: 'map' });
    const floorTiles = map.addTilesetImage('trees_plants_rocks', 'tiles');
    map.createStaticLayer('floor_0', floorTiles, 0, 0);
    map.createStaticLayer('floor_1', floorTiles, 0, 0);

    this.cameras.main.setBounds(0, 0, map.widthInPixels, map.heightInPixels);

    const cursors = this.input.keyboard.createCursorKeys();

    const controlConfig = {
      camera: this.cameras.main,
      left: cursors.left,
      right: cursors.right,
      up: cursors.up,
      down: cursors.down,
      speed: 0.5
    };

    this.controls = new Phaser.Cameras.Controls.Fixed(controlConfig);
    this.pointerManager.create();
  }

  public update(time, delta) {
    this.controls.update(delta);

    this.pointerManager.update();
    this.entityRenderer.update();
    this.collisionManager.update();
  }
}
