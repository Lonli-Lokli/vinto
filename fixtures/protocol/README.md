# The wire, frozen per protocol version

One encoded sample of every client message, server message and game action, as the wire
carries it, under `v<PROTOCOL_VERSION>/`. Held by `WireSamplesTest` in `shared/protocol`:
the current encoder must reproduce the current version's samples byte for byte, and every
older version's samples must still decode with the current decoder.

**Regenerate only after a bump, only into the new version's directory:**

    ./gradlew :shared:protocol:jvmTest --tests '*WireSamplesTest*' -Pwire=write

An older version's directory is what an older build sent and read; rewriting it rewrites
history. There is no `v1/`: the samples were born with the number, and what version 1 sent is
on `master` before it.

The reason this exists is in `docs/kotlin/PROTOCOL.md` under "Compatibility rule": a payload
that changed shape under an old tag froze every store build in the final round, and a list of
tags could not see it.
