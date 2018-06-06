import * as EasyStar from 'easystarjs';
import { PlayerEntityHolder, EntityStore } from 'entities';
import { CollisionUpdater } from 'map';

export class EngineConfig {

  public readonly debug = {
    renderCollision: false
  };

  constructor() {

  }
}

export class EngineContext {

  public mapGroup0: Phaser.GameObjects.Group;
  public pointerGroup: Phaser.GameObjects.Group;
  public pathfinder: EasyStar.js;
  public collisionUpdater: CollisionUpdater;

  public readonly config = new EngineConfig();

  constructor(
    public readonly game: Phaser.Scene,
    public readonly entityStore: EntityStore,
    public readonly playerHolder: PlayerEntityHolder
  ) {

    this.mapGroup0 = this.game.make.group({});

    // Easystar
    this.pathfinder = new EasyStar.js();
    this.pathfinder.enableDiagonals();

    this.collisionUpdater = new CollisionUpdater(this);
  }
}
