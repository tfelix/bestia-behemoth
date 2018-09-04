import * as LOG from 'loglevel';

import {
  DebugComponent, Component, ComponentType, MoveComponent,
  VisualComponent, PositionComponent
} from 'entities/components';
import { ComponentRenderer } from './ComponentRenderer';
import { Entity } from 'entities';
import { Point } from 'model';
import { VisualComponentRenderer, SpriteData } from './VisualComponentRenderer';
import { MapHelper } from 'map';
import { EngineConfig, EngineContext } from '../../EngineContext';

type WalkAnimationName = 'walk_up' | 'walk_up_right' | 'walk_right' | 'walk_down_right' |
  'walk_down' | 'walk_down_left' | 'walk_left' | 'walk_up_left';

type StandAnimationName = 'stand_up' | 'stand_up_right' | 'stand_right' | 'stand_down_right' |
  'stand_down' | 'stand_down_left' | 'stand_left' | 'stand_up_left';

export interface MoveData {
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

  constructor(
    private readonly ctx: EngineContext,
  ) {
    super(ctx.game);

    this.ctx.entityStore.onUpdateEntity.subscribe(msg => {
      if (msg.changedComponentType === ComponentType.MOVE) {
        LOG.debug('Received new movement data.');
        // TODO das hier noch debuggen.
        // this.clearMovementData(msg.entity);
      }
    });
  }

  get supportedComponent(): ComponentType {
    return ComponentType.MOVE;
  }

  protected hasNotSetup(entity: Entity, component: MoveComponent): boolean {
    return !entity.data.move;
  }

  private clearMovementData(entity: Entity) {
    LOG.debug(`Clearing movement data for entity: ${entity.id}`);
    if (entity.data.move && entity.data.move.timeline) {
      entity.data.move.timeline.stop();
    }
    entity.data.move = null;
    entity.removeComponentByType(ComponentType.MOVE);
  }

  private performNextMovement(entity: Entity, component: MoveComponent) {
    LOG.debug(`performNextMovement for entity: ${entity.id}`);
    const moveData = entity.data.move;

    if (!moveData) {
      this.clearMovementData(entity);
    }

    const currentPos = component.path[moveData.currentPathPosition];
    const nextPathPosition = moveData.currentPathPosition + 1;

    this.updatePositionComponentLocalOnly(entity, currentPos);

    const spriteData = entity.data.visual;
    const visual = entity.getComponent(ComponentType.VISUAL) as VisualComponent;
    if (!visual || !spriteData) {
      LOG.warn('Can not display walking animation because no visual component exists.');
      return;
    }

    // Subtract one because we added start position of entity to the path queue before.
    const hasNextStep = (component.path.length - 1) >= nextPathPosition;
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

      moveData.timeline = this.game.tweens.timeline({
        targets: spriteData.sprite,
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
    LOG.debug(`Creating movement data for entity: ${entity.id}`);
    const path = component.path;
    const position = entity.getComponent(ComponentType.POSITION) as PositionComponent;

    const moveData: MoveData = {
      assumedWalkspeed: component.walkspeed,
      currentPathPosition: 0,
      timeline: null
    };
    entity.data.move = moveData;

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

  /**
   * Later when connected to the server only the server has the right to update position
   * components. This wont be done in the client anymore.
   */
  private updatePositionComponentLocalOnly(entity: Entity, position: Point) {
    LOG.debug(`Updating entity ${entity.id} position: ${JSON.stringify(position)}`);
    const posComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
    if (!posComp) {
      return;
    }
    posComp.position = position;
  }
}
