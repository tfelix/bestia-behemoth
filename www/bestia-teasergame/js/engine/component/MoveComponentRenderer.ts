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

interface MoveData {
  assumedWalkspeed: number;
  currentPathPosition: number;
  timeline: Phaser.Tweens.Timeline;
}

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

  private clearMovementData(entity: Entity) {
    entity.gameData[MoveComponentRenderer.DAT_MOVE] = null;
    entity.removeComponentByType(ComponentType.MOVE);
  }

  private performNextMovement(entity: Entity, component: MoveComponent) {
    const moveData = entity.gameData[MoveComponentRenderer.DAT_MOVE] as MoveData;
    const currentPos = component.path[moveData.currentPathPosition];
    const nextPathPosition = moveData.currentPathPosition + 1;

    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    const visual = entity.getComponent(ComponentType.VISUAL) as VisualComponent;
    if (!visual || !sprite) {
      LOG.warn('Can not display walking animation because no visual component exists.');
      return;
    }

    // Subtract one because we added start position of entity to the path queue before.
    const hasNextStep = (component.path.length - 1) > nextPathPosition;
    if (!hasNextStep) {

      const lastPos = component.path[moveData.currentPathPosition - 1];
      const standAnimation = getStandAnimationName(lastPos, currentPos);
      visual.animation = standAnimation;
      this.clearMovementData(entity);
    } else {

      const nextPosition = component.path[nextPathPosition];
      const speed = component.walkspeed;
      const walkAnimation = getWalkAnimationName(currentPos, nextPosition);
      const stepDuration = getWalkDuration(currentPos.getDistance(nextPosition), 1);
      visual.animation = walkAnimation;

      const nextPosPx = MapHelper.pointToPixelCentered(nextPosition);

      const timeline = this.game.tweens.timeline({
        targets: sprite,
        ease: 'Linear',
        totalDuration: stepDuration,
        tweens: [{
          x: nextPosPx.x,
          y: nextPosPx.y,
          onComplete: () => this.performNextMovement(entity, component)
        }]
      });

      moveData.currentPathPosition = nextPathPosition;
    }
  }

  protected createGameData(entity: Entity, component: MoveComponent) {
    const path = component.path;

    const position = entity.getComponent(ComponentType.POSITION) as PositionComponent;

    // We need the current position to calculate the walk direction.
    path.unshift(position.position);

    const moveData: MoveData = {
      assumedWalkspeed: component.walkspeed,
      currentPathPosition: 0,
      timeline: null
    };
    entity.gameData[MoveComponentRenderer.DAT_MOVE] = moveData;

    this.performNextMovement(entity, component);
  }

  protected updateGameData(entity: Entity, component: MoveComponent) {
  }

  protected removeComponent(entity: Entity, component: MoveComponent) {
    this.clearMovementData(entity);
  }

  private isMoving(sprite): boolean {
    return sprite._movingTween !== null && sprite._movingTween.isRunning;
  }
}
