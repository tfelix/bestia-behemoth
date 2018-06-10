import { ActionsRenderer } from './ActionsRenderer';
import { Entity } from 'entities';
import { ChatAction } from 'entities/actions/ChatAction';
import { EngineConfig, EngineContext } from '../../EngineContext';
import { ComponentType, VisualComponent } from 'entities/components';
import { Px } from 'model';

const CHAT_DISPLAY_DURATION_MS = 3500;
const SPRITE_Y_OFFSET = -10;
const CHAT_BORDER_PADDING = 4;

const CHAT_STYLE = {
  fontFamily: 'Arial',
  fontSize: 12,
  color: '#FFFFFF',
  boundsAlignH: 'center',
  boundsAlignV: 'middle'
};

export interface ChatData {
  background: Phaser.GameObjects.Graphics;
  text: Phaser.GameObjects.Text;
  deleteTimer: Phaser.Time.TimerEvent;
}

const rect = new Phaser.Geom.Rectangle();

export class ChatActionsRenderer extends ActionsRenderer {

  constructor(
    private readonly ctx: EngineContext
  ) {
    super(ctx.game);
  }

  public needsActionRender(entity: Entity): boolean {
    return entity.hasAction(ChatAction);
  }

  public render(entity: Entity) {
    this.clearChatData(entity);

    const entitySprite = entity.data.visual && entity.data.visual.sprite;
    if (!entitySprite) {
      return;
    }

    const spriteHeight = this.ctx.helper.sprite.getSpriteSize(entitySprite).height;
    const chatPos = new Px(entitySprite.x, entitySprite.y - spriteHeight + SPRITE_Y_OFFSET);

    const actions = this.getActionsFromEntity<ChatAction>(entity, ChatAction);
    // We only render the last one
    const action = actions.pop();

    const box = this.game.add.graphics();

    const chatMsg = (action.nickname) ? `${action.nickname}: ${action.text}` : action.text;
    const txt = this.game.add.text(
      chatPos.x,
      chatPos.y,
      chatMsg,
      CHAT_STYLE
    );
    txt.depth = 10000;
    txt.setOrigin(0.5, 0.5);


    const gfx = this.game.add.graphics({ fillStyle: { color: 0x00AA00 } });

    const topleft = txt.getTopLeft();
    rect.setPosition(topleft.x - CHAT_BORDER_PADDING / 2, topleft.y - CHAT_BORDER_PADDING / 2);
    rect.width = txt.width + CHAT_BORDER_PADDING;
    rect.height = txt.height + CHAT_BORDER_PADDING;

    // Draw background
    gfx.fillStyle(0x000000, 0.5);
    gfx.fillRectShape(rect);

    gfx.displayOriginX = 0.5;
    gfx.displayOriginY = 0.5;

    // There seems to be a bug and the childs can not be added in the constructor.
    // Thats why they are added in an extra call.
    entity.data.chat = {
      background: gfx,
      text: txt,
      deleteTimer: this.game.time.addEvent({
        delay: CHAT_DISPLAY_DURATION_MS,
        callback: () => this.clearChatData(entity)
      })
    };
  }

  private clearChatData(entity: Entity) {
    if (entity.data.chat) {
      entity.data.chat.deleteTimer.destroy();
      entity.data.chat.background.destroy();
      entity.data.chat.text.destroy();
      entity.data.chat = null;
    }
  }
}
