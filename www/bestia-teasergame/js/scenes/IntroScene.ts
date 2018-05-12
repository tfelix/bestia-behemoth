export class GameScene extends Phaser.Scene {

  private introTextStyle =  { fontFamily: 'Verdana', fontSize: 12, color: '#ffffff' };

  constructor() {
    super({
      key: 'IntroScene'
    });
  }

  public create() {
    const introText = [
      'In an everchanging world the secrets lurk inside the shadows.'
    ];

    const texts = introText.map(txt => this.add.text(100, 200, txt, this.introTextStyle));
  }
}
