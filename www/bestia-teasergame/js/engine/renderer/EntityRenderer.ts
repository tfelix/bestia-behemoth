import * as LOG from 'loglevel';

import { EntityStore, EntityUpdate, Entity } from 'entities';
import { Component, ComponentType } from 'entities/components';
import { ComponentRenderer } from './component/ComponentRenderer';
import { VisualComponentRenderer } from './component/VisualComponentRenderer';
import { DebugComponentRenderer } from './component/DebugComponentRenderer';
import { MoveComponentRenderer } from './component/MoveComponentRenderer';

export class EntityRenderer {

  private componentRenderer = new Map<ComponentType, ComponentRenderer<Component>>();

  constructor(
    private readonly game: Phaser.Scene,
    private readonly entityStore: EntityStore
  ) {
    this.addComponentRenderer(new VisualComponentRenderer(game));
    this.addComponentRenderer(new DebugComponentRenderer(game));
    this.addComponentRenderer(new MoveComponentRenderer(game));
  }

  private addComponentRenderer(renderer: ComponentRenderer<Component>) {
    this.componentRenderer.set(renderer.supportedComponent, renderer);
  }

  public update() {
    for (const e of this.entityStore.entities.values()) {
      for (const c of e.getComponentIterator()) {
        const renderer = this.componentRenderer.get(c.type);
        if (renderer) {
          renderer.render(e, c);
        }
      }
    }
  }

  private handleUpdateEntity(data: EntityUpdate) {
    LOG.debug(`Processing component: ${data.changedComponent}.`);
    const renderer = this.componentRenderer.get(data.changedComponent);
    if (renderer) {
      const component = data.entity.getComponent(data.changedComponent);
      renderer.render(data.entity, component);
    }
  }
}
