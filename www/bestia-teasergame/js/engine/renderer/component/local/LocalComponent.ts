import { Component, ComponentType } from 'entities/components';

export const LOCAL_COMPONENT_ID = -1;

class LocalComponent extends Component {
  constructor(entityId: number, componentType: ComponentType) {
    super(LOCAL_COMPONENT_ID, entityId, componentType);
  }
}
