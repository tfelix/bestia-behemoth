/** Must match the server's Letter.MAX_SUBJECT_LENGTH and MAX_BODY_LENGTH, and the game's letter form. */
export const MAX_SUBJECT_LENGTH = 60;
export const MAX_BODY_LENGTH = 1000;

/** The postage, in gold. Only shown; the zone charges it. */
export const POSTAGE = 1;

/** What a refused letter tells its writer, by the status the api-server answered with. */
export function letterRefusal(status: number): string | null {
	switch (status) {
		case 403:
			return 'Letters need Basic Skill Lv. 2.';
		case 422:
			return 'Nobody is called that. Check the name.';
		case 409:
			return 'Their mailbox is full. The pigeon came back.';
		case 402:
			return `You cannot pay the postage of ${POSTAGE} gold.`;
		case 400:
			return `A letter needs a subject or a text, of at most ${MAX_SUBJECT_LENGTH} and ${MAX_BODY_LENGTH} characters.`;
		default:
			return null;
	}
}

/** How long a letter stays in the mailbox, in words: "29 days", "5 h", "under an hour". */
export function keptFor(expiresAt: number, now: number = Date.now()): string {
	const hours = Math.floor((expiresAt - now) / 3_600_000);
	if (hours >= 48) return `${Math.floor(hours / 24)} days`;
	if (hours >= 1) return `${hours} h`;
	return 'under an hour';
}

/** "Re: Bridge", without piling up "Re: Re:" on a long exchange. */
export function replySubject(subject: string): string {
	const reply = subject.startsWith('Re: ') ? subject : `Re: ${subject}`;
	return reply.slice(0, MAX_SUBJECT_LENGTH);
}
