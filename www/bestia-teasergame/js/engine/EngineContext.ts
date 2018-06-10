import * as EasyStar from 'easystarjs';
import { PlayerEntityHolder, EntityStore } from 'entities';
import { CollisionUpdater } from 'map';
import { DisplayHelper } from './DisplayHelper';
import { SpriteHelper } from './SpriteHelper';

export class EngineConfig {

  public readonly debug = {
    renderCollision: false
  };

  constructor() {

  }
}

export class GameData {
}

export class EngineContext {

  public mapGroup0: Phaser.GameObjects.Group;
  public pointerGroup: Phaser.GameObjects.Group;
  public pathfinder: EasyStar.js;
  public collisionUpdater: CollisionUpdater;

  public readonly config = new EngineConfig();
  public readonly data = new GameData();

  public readonly helper: {
    display: DisplayHelper;
    sprite: SpriteHelper;
  };

  constructor(
    public readonly game: Phaser.Scene,
    public readonly entityStore: EntityStore,
    public readonly playerHolder: PlayerEntityHolder
  ) {
    this.helper = {
      display: new DisplayHelper(this.game),
      sprite: new SpriteHelper(this.game)
    };

    this.mapGroup0 = this.game.make.group({});

    this.pathfinder = new EasyStar.js();
    this.pathfinder.enableDiagonals();

    this.collisionUpdater = new CollisionUpdater(this);
  }
}
