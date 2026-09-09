package game.vinto.app.link

/**
 * Whether an opened invitation stops and asks before it takes a seat.
 *
 * A per-client answer to one question: **can a link reach this client without anybody having
 * decided to come here?**
 *
 * In a browser, yes, constantly. A URL is opened by a QR scanner's preview, by the in-app
 * browser of whichever app the invitation was sent through, by a second tab, by somebody
 * checking where a link goes. Every one of those used to seat a stranger — a room has four
 * chairs, and one glance took one of them for as long as the lobby lived. It is how a host who
 * scanned their own QR ended up at their own table twice, under a name they had never seen.
 *
 * On a phone, no. An App Link or a `vinto://` link is resolved by the system and handed to the
 * app because somebody tapped an invitation, which is a decision — and the app already holds
 * the seat token for a room it has been in, so walking straight in returns to the same seat
 * rather than taking a second.
 */
expect val invitationsAskFirst: Boolean
