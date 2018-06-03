import { EntityStore } from '../entities/EntityStore';
import { Entity } from '../entities/Entity';
import { VisualComponentRenderer } from '../engine/component/VisualComponentRenderer';
import { VisualComponent, SpriteType } from '../entities/components/VisualComponent';
import { PositionComponent } from '../entities/components/PositionComponent';
import { Point } from '../model';
import { EntityRenderer } from '../engine/EntityRenderer';
import { EngineContext } from '../engine/EngineContext';
import { PointerManager } from '../engine/pointer/PointerManager';
import { DebugComponent } from '../entities/components/DebugComponent';
import { CollisionManager } from '../engine/map/CollisionManager';
import { MoveComponent } from 'entities/components';
import { EntityLocalFactory } from 'entities/EntityLocalFactory';

export class GameScene extends Phaser.Scene {
  private scoreText: Phaser.GameObjects.Text[];
  private controls: Phaser.Cameras.Controls.FixedKeyControl;

  private entityStore: EntityStore;
  private entityRenderer: EntityRenderer;
  private engineContext: EngineContext;
  private pointerManager: PointerManager;
  private collisionManager: CollisionManager;

  private entityFactory: EntityLocalFactory;

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

    this.entityFactory = new EntityLocalFactory(this.entityStore);

    this.setupTestEnv();
  }

  public setupTestEnv() {
    const master = this.entityFactory.addSprite('mastersmith', new Point(2, 3));
    const vitata = this.entityFactory.addSprite('vitata', new Point(5, 6));

    const move = new MoveComponent(
      3,
      1,
    );
    move.walkspeed = 1;
    move.path = [
      new Point(2, 4),
      new Point(2, 5),
      new Point(2, 6),
      new Point(3, 6),
      new Point(4, 6),
      new Point(5, 5),
      new Point(6, 4),
      new Point(6, 3),
      new Point(6, 2),
      new Point(5, 2),
      new Point(4, 2),
      new Point(3, 2),
      new Point(2, 3),
      new Point(1, 4)
    ];
    // move.path = [new Point(3, 3), new Point(4, 3)];
    master.addComponent(move);

    this.entityFactory.addObject('tree', new Point(10, 10));
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
