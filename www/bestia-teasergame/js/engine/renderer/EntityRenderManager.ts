import * as LOG from 'loglevel';

import { EntityStore, EntityUpdate, Entity } from 'entities';
import { Component, ComponentType } from 'entities/components';
import { ComponentRenderer } from './component/ComponentRenderer';
import { VisualComponentRenderer } from './component/VisualComponentRenderer';
import { DebugComponentRenderer } from './component/DebugComponentRenderer';
import { MoveComponentRenderer } from './component/MoveComponentRenderer';
import { ConditionComponentRenderer } from './component/ConditionComponentRenderer';
import { EngineContext } from '../EngineContext';

export class EntityRenderManager {

  private componentRenderer = new Map<ComponentType, ComponentRenderer<Component>>();

  constructor(
    private readonly context: EngineContext
  ) {
    this.addComponentRenderer(new VisualComponentRenderer(this.context.game));
    this.addComponentRenderer(new DebugComponentRenderer(this.context.game));
    this.addComponentRenderer(new MoveComponentRenderer(context));
    this.addComponentRenderer(new ConditionComponentRenderer(context));
  }

  private addComponentRenderer(renderer: ComponentRenderer<Component>) {
    this.componentRenderer.set(renderer.supportedComponent, renderer);
  }

  public update() {
    for (const e of this.context.entityStore.entities.values()) {
      for (const c of e.getComponentIterator()) {
        const renderer = this.componentRenderer.get(c.type);
        if (renderer) {
          renderer.render(e, c);
        }
      }
    }
  }

  private handleUpdateEntity(data: EntityUpdate) {
    LOG.debug(`Processing component: ${data.changedComponentType}.`);
    const renderer = this.componentRenderer.get(data.changedComponentType);
    if (renderer) {
      const component = data.entity.getComponent(data.changedComponentType);
      renderer.render(data.entity, component);
    }
  }
}
