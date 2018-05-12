import { EngineContext } from '../EngineContext';
import { MapHelper } from '../map/MapHelper';
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
    this.activePointer.updatePosition(cords, pointer);
  }

	/**
	 * This will hide and disable all indicators.
	 */
  hide() {
    this.requestActive(this.nullIndicator, true);
  }

	/**
	 * This will re-enable the indicators if they were hidden before.
	 */
  show() {
    // We can only show when previously hidden.
    if (this.activePointer !== this.nullIndicator) {
      return;
    }
    this.dismissActive();
  }

	/**
	 * Shows the default pointer. It will also clear the pointer stack.
	 */
  showDefault() {
    this.requestActive(this.movePointer);
    this.pointerStack = [];
  }

	/**
	 * Will trigger all load events on the registered indicators. This should be
	 * called in the initial load event of the engine to fetch all presets the indicator
	 * need.
	 */
  load(loader: Phaser.Loader.LoaderPlugin) {
    this.pointers.forEach(x => x.load(loader));
  }

	/**
	 * Triggers all create events on the registered indicators. This should be
	 * called in the create event of phaser.
	 */
  create() {
    this.pointers.forEach(x => x.create());
    this.setActive(this.movePointer);
  }

	/**
	 * Called each tick in case there is a need to perform some changes
	 * depending on the game tick.
	 */
  update() {

  }

	/**
	 * An indicator can request to get displayed via the manager. The current
	 * active indicator is pushed to the stack. With dismissActive it will
	 * re-appear again.
	 * 
	 * @param force -
	 *            The indicator will not be checked if its okay to replace him
	 *            with the new indicator.
	 */
  requestActive(indicator, force = false) {
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

	/**
	 * No pushing to the indicator stack will happen when using this method.
	 * Otherwise its the same as requestActive.
	 */
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

	/**
	 * The indicator can request to get dismissed. It will be replaced with last
	 * indicator.
	 */
  dismissActive() {
    if (this.pointerStack.length === 0) {
      this.activePointer = this.movePointer;
    } else {
      this.setActive(this.pointerStack.pop());
    }
  }
}