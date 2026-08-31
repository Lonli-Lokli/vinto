# Spec delta: app-distribution

## ADDED Requirements

### Requirement: An invitation link reaches the app

A room invitation SHALL be shareable as a link that opens the room directly in the installed
app, and SHALL remain usable when it does not.

The client SHALL accept an invitation in every form a person might paste: an `https` link on
the invitation host, a `vinto://` link, a bare path, and a bare code — in any case, with
surrounding whitespace tolerated. It SHALL refuse anything the registry could not have issued,
applying the same code test the room applies, so that a client and a room cannot disagree about
whether a code is well-formed.

An invitation SHALL carry the code in readable form alongside the link, because a code is the
one string in this game that gets read aloud down a telephone.

#### Scenario: An https invitation is opened on a device with the app installed

- **WHEN** a player opens an `https` invitation link and the app is installed
- **THEN** the app opens on the room the code names, and the join is recorded as the funnel
  step analytics counts

#### Scenario: The association files are not published

- **WHEN** the platform's association file is absent or does not name this build's credential
- **THEN** the `https` link SHALL open the website rather than failing, and the `vinto://`
  scheme and the written code SHALL still reach the room

#### Scenario: A code that could not have been issued

- **WHEN** an invitation carries a code the registry's own format test rejects
- **THEN** the client SHALL refuse it without contacting the room

### Requirement: Deep links are declared on every platform that has them

Each platform SHALL declare its own half of the link contract, and the website SHALL serve the
files those declarations are verified against.

- Android SHALL declare intent filters for both the `https` host and the `vinto://` scheme, and
  the site SHALL serve `/.well-known/assetlinks.json` naming the release signing certificate's
  SHA-256 fingerprint.
- iOS SHALL handle both forms, and the site SHALL serve
  `/.well-known/apple-app-site-association` as `application/json` with no file extension,
  naming the team and bundle identifiers.
- The web client SHALL read the code from its own path.

Both association files SHALL live in the web client's published resources, so that deploying
the site publishes them and they cannot drift from the host invitations name.

#### Scenario: The invitation host changes

- **WHEN** the host invitations are issued for no longer matches the host the site is served at
- **THEN** the build SHALL fail rather than publish invitations that resolve to nothing
