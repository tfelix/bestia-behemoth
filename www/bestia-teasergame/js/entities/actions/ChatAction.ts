export class ChatAction {
  constructor(
    public readonly text: string,
    public readonly nickname?: string
  ) {
  }
}
