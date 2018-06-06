import * as LOG from 'loglevel';

import { MapHelper } from 'map/MapHelper';

import { EngineContext } from '../EngineContext';
import { MovePointer } from './MovePointer';
import { NullPointer } from './NullPointer';
import { Pointer } from './Pointer';

/**
 * The manager is responsible for switching the indicator depending on the needs
 * of the engine. It listens to various events (usage of an item for example)
 * and in case of this will switch the indicator. This indicator then gets the
 * control of the inputs and must react accordingly.
 * <p>
 * The manager does also listen to change requests from the outside. So it is
 * possible to react upon hover effects over sprites for example.
 * </p>
 */
export class PointerManager {

  private pointers: Pointer[] = [];
  private pointerStack: Pointer[] = [];

  private activePointer: Pointer;
  private movePointer: Pointer;
  private nullIndicator: Pointer;

  constructor(
    private readonly engineContext: EngineContext
  ) {

    this.nullIndicator = new NullPointer(this, engineContext);
    this.activePointer = this.nullIndicator;
    this.movePointer = new MovePointer(this, engineContext);

    // Register the available indicators.
    this.pointers.push(this.movePointer);
    // this.pointers.push(new BasicAttackIndicator(this, engineContext));

    this.engineContext.game.input.on('pointermove', this.updatePointerPosition, this);
  }

  private updatePointerPosition(pointer: Phaser.Input.Pointer) {
    const cords = MapHelper.getClampedTilePixelXY(pointer.x, pointer.y);
    this.activePointer.updatePosition(cords);
  }

  public hide() {
    this.requestActive(this.nullIndicator, true);
  }

  public show() {
    // We can only show when previously hidden.
    if (this.activePointer !== this.nullIndicator) {
      return;
    }
    this.dismissActive();
  }

  public showDefault() {
    this.requestActive(this.movePointer);
    this.pointerStack = [];
  }

  public load(loader: Phaser.Loader.LoaderPlugin) {
    this.pointers.forEach(x => x.load(loader));
  }

  public create() {
    this.pointers.forEach(x => x.create());
    this.setActive(this.movePointer);
  }

  public update() {
    // LOG.debug(this.engineContext.game.input.mouse.target);
  }

  public requestActive(indicator, force = false) {
    // Ask the active pointer if he allows to be overwritten by the new
    // indicator.
    if (!force && !this.activePointer.allowOverwrite(indicator)) {
      return;
    }

    this.pointerStack.push(this.activePointer);
    this.activePointer.deactivate();
    this.activePointer = indicator;
    this.activePointer.activate();
  }

  private setActive(indicator) {
    // Ask the active pointer if he allows to be overwritten by the new
    // indicator.
    if (!this.activePointer.allowOverwrite(indicator)) {
      return;
    }
    this.activePointer.deactivate();
    this.activePointer = indicator;
    this.activePointer.activate();
  }

  public dismissActive() {
    if (this.pointerStack.length === 0) {
      this.activePointer = this.movePointer;
    } else {
      this.setActive(this.pointerStack.pop());
    }
  }
}
