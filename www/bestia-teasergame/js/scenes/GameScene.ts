import * as LOG from 'loglevel';

import { Entity, EntityStore, PlayerEntityHolder } from 'entities';
import {
  VisualComponent, SpriteType, PositionComponent, DebugComponent,
  MoveComponent
} from 'entities/components';
import { Point, AccountInfo } from 'model';
import { EngineContext } from 'engine/EngineContext';
import { PointerManager } from 'engine/pointer';
import { EntityLocalFactory } from 'entities/EntityLocalFactory';
import { EntityRenderer, CollisionRenderer } from 'engine/renderer';
import { CollisionUpdater } from 'map';
import { DamageAction } from 'entities/actions';
import { ActionsRendererManager } from 'engine/renderer/actions/ActionsRenderManager';

export class GameScene extends Phaser.Scene {
  private controls: Phaser.Cameras.Controls.FixedKeyControl;

  private readonly playerAccountId = 1;

  private entityStore: EntityStore;
  private engineContext: EngineContext;
  private pointerManager: PointerManager;

  private collisionUpdater: CollisionUpdater;

  private entityRenderer: EntityRenderer;
  private collisionRenderer: CollisionRenderer;
  private actionRenderManager: ActionsRendererManager;

  private entityFactory: EntityLocalFactory;

  constructor() {
    super({
      key: 'GameScene'
    });
  }

  public init(entityStore: EntityStore): void {
    this.entityStore = new EntityStore();
    const accountInfo = new AccountInfo('gast', this.playerAccountId, 'gast');
    const playerEntityHolder = new PlayerEntityHolder(accountInfo, this.entityStore);
    this.engineContext = new EngineContext(this, this.entityStore, playerEntityHolder);

    this.entityRenderer = new EntityRenderer(this, this.entityStore);
    this.collisionRenderer = new CollisionRenderer(this.engineContext);
    this.actionRenderManager = new ActionsRendererManager(this, this.entityStore);

    this.pointerManager = new PointerManager(this.engineContext);
    this.collisionUpdater = new CollisionUpdater(this.engineContext);

    this.entityFactory = new EntityLocalFactory(this.entityStore);

    this.setupTestEnv();
  }

  public setupTestEnv() {
    const master = this.entityFactory.addSprite('player_1', new Point(2, 3));
    this.entityFactory.addPlayerComponent(master, this.playerAccountId);
    this.entityFactory.addDebugComponent(master);
    const vitata = this.entityFactory.addSprite('vitata', new Point(5, 6));
    this.entityFactory.addDebugComponent(vitata);
    this.entityFactory.addConditionComponent(vitata);

    const tree = this.entityFactory.addObject('tree', new Point(10, 10));
    this.entityFactory.addDebugComponent(tree);

    this.engineContext.config.debug.renderCollision = false;

    this.time.addEvent({
      delay: 1000,
      repeat: 9,
      callback: () => {
        const dmg = Math.floor(Math.random() * 15 + 4);
        const dmgAction = new DamageAction(dmg);
        vitata.actions.push(dmgAction);
      }
    });
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
    this.actionRenderManager.update();
    this.collisionRenderer.update();

    this.engineContext.collisionUpdater.update();
  }
}
