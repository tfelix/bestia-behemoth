import * as LOG from 'loglevel';

import { DebugComponent } from '../../entities/components/DebugComponent';
import { ComponentRenderer } from './ComponentRenderer';
import { ComponentType } from '../../entities/components/ComponentType';
import { Entity } from '../../entities/Entity';
import { VisualComponentRenderer } from './VisualComponentRenderer';
import { Component } from '../../entities/components/Component';
import { MoveComponent, VisualComponent, PositionComponent } from '../../entities/components';
import { Point } from 'model';
import { MapHelper } from '../map/MapHelper';

type WalkAnimationName = 'walk_up' | 'walk_up_right' | 'walk_right' | 'walk_down_right' |
  'walk_down' | 'walk_down_left' | 'walk_left' | 'walk_up_left';

type StandAnimationName = 'stand_up' | 'stand_up_right' | 'stand_right' | 'stand_down_right' |
  'stand_down' | 'stand_down_left' | 'stand_left' | 'stand_up_left';

/**
 * Calculates the duration in ms of the total walk of the given path.
 * Depends upon the relative walkspeed of the entity.
 * @returns Total walkspeed in ms.
 */
function getWalkDuration(length, walkspeed) {
  // Usual walkspeed is 1.4 tiles / s -> 0,74 s/tile.
  return Math.round((1 / 1.4) * length / walkspeed * 1000);
}

/**
 * Returns the animation name for walking to this position, from the old
 * position.
 */
function getWalkAnimationName(oldPos: Point, newPos: Point): WalkAnimationName {

  let x = newPos.x - oldPos.x;
  let y = newPos.y - oldPos.y;

  if (x > 1) {
    x = 1;
  }
  if (y > 1) {
    y = 1;
  }

  if (x === 0 && y === -1) {
    return 'walk_up';
  } else if (x === 1 && y === -1) {
    return 'walk_up_right';
  } else if (x === 1 && y === 0) {
    return 'walk_right';
  } else if (x === 1 && y === 1) {
    return 'walk_down_right';
  } else if (x === 0 && y === 1) {
    return 'walk_down';
  } else if (x === -1 && y === 1) {
    return 'walk_down_left';
  } else if (x === -1 && y === 0) {
    return 'walk_left';
  } else {
    return 'walk_up_left';
  }
}

/**
 * Returns the animation name for standing to this position.
 */
function getStandAnimationName(oldPos: Point, newPos: Point): StandAnimationName {
  let x = newPos.x - oldPos.x;
  let y = newPos.y - oldPos.y;

  if (x > 1) {
    x = 1;
  }
  if (y > 1) {
    y = 1;
  }

  if (x === 0 && y === -1) {
    return 'stand_up';
  } else if (x === 1 && y === -1) {
    return 'stand_up_right';
  } else if (x === 1 && y === 0) {
    return 'stand_right';
  } else if (x === 1 && y === 1) {
    return 'stand_down_right';
  } else if (x === 0 && y === 1) {
    return 'stand_down';
  } else if (x === -1 && y === 1) {
    return 'stand_down_left';
  } else if (x === -1 && y === 0) {
    return 'stand_left';
  } else {
    return 'stand_up_left';
  }
}

export class MoveComponentRenderer extends ComponentRenderer<MoveComponent> {
  public static readonly DAT_MOVE = 'move';

  constructor(
    game: Phaser.Scene
  ) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.MOVE;
  }

  protected hasNotSetup(entity: Entity, component: MoveComponent): boolean {
    return entity.gameData[MoveComponentRenderer.DAT_MOVE] === undefined;
  }

  protected createGameData(entity: Entity, component: MoveComponent) {

    // Temporary
    entity.gameData[MoveComponentRenderer.DAT_MOVE] = true;

    const path = component.path;
    const speed = component.walkspeed;

    const position = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;

    // We need the current position to calculate the walk
    // direction.
    path.unshift(position.position);
    // https://labs.phaser.io/edit.html?src=src\tweens\timelines\total%20duration.js
    const timeline = this.game.tweens.timeline({
      targets: sprite,
      ease: 'Linear',
      totalDuration: 5000,
      tweens: [
        {
          y: 300,
          onComplete: () => { console.log('onComplete'); }
        },
        {
          y: 400,
          onComplete: () => { console.log('onComplete'); }
        },
        {
          y: 600,
          onComplete: () => { console.log('onComplete'); }
        }],
      onStart: () => { console.log('onStart timeline'); },
      onComplete: () => { console.log('onComplete timeline'); }
    });

    /*
        const moveTween = this.game.tweens.add({
          targets: sprite,
          ease: 'Linear',
          y: 500,
          duration: 6000,
          onStart: () => { console.log('onStart'); },
          onComplete: () => { console.log('onComplete'); }
        });*/

    /*
    // Calculate coordinate arrays from path.
    path.forEach((pathPos, i) => {
      // This is our current position.
      // No need to move TO this position.
      if (i === 0) {
        return;
      }

      var cords = WorldHelper.getSpritePxXY(ele.x, ele.y);

      const previousTile = path[i - 1];
      const distance = previousTile.getDistance(pathPos);
      // Check if we go diagonal to adjust speed.
      const length = (distance > 1.01) ? 1 : 1.414;
      const duration = getWalkDuration(length, speed);

      // Start the animation.
      sprite.tweenMove.to({
        x: cords.x,
        y: cords.y
      }, duration, Phaser.Easing.Linear.None, false);
    }, this);

    // After each child tween has completed check the next walking direction and
    // update the entity movement.
    sprite.tweenMove.onChildComplete.add(function () {
      currentPathCounter++;

      var isLast = currentPath.length + 1 === currentPathCounter;
      if (isLast) {
        // We keep standing still now.
      } else {
        var currentPosition = currentPath[currentPathCounter];
        var nextAnim = getWalkAnimationName(currentPosition, path[currentPathCounter + 1]);
        playEntityAnimation(entity, nextAnim);
      }

    }, this);

    // At the end of the movement fetch the correct standing animation
    // and stop the movement.
    sprite.tweenMove.onComplete.addOnce(function () {

      var size = path.length;
      var currentPos = path[size - 1];
      var lastPos = path[size - 2];
      var nextAnim = getStandAnimationName(lastPos, currentPos);
      playEntityAnimation(entity, nextAnim);

      this.position = currentPos;
    }, this);

    // Start the first animation immediately, because the usual checks
    // only start to check after the first tween has finished.
    var nextAnim = getWalkAnimationName(path[0], path[1]);
    playEntityAnimation(entity, nextAnim);
    sprite.tweenMove.start();*/
  }

  protected updateGameData(entity: Entity, component: MoveComponent) {
  }

  protected removeComponent(entity: Entity, component: MoveComponent) {
  }

  private isMoving(sprite): boolean {
    return sprite._movingTween !== null && sprite._movingTween.isRunning;
  }
}
