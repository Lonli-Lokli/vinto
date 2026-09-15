package game.vinto.app

import game.vinto.client.Vault

/**
 * None. There is no store here to buy from, so there is no count to keep.
 *
 * `supportOffer()` answers [Support.Elsewhere] on this platform — the outside page, where the
 * amount is the giver's own — and nothing on the other end of that link reports back. A number
 * kept here would be a number that could only ever be zero.
 */
actual fun enduringVault(): Vault? = null
