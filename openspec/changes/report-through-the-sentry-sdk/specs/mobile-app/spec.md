# Spec delta: mobile-app

## MODIFIED Requirements

### Requirement: Performance and stability

Bot thinking SHALL not cause dropped frames; the app SHALL start to the home screen in
under 2 s on a mid-range 2022 phone.

Crashes SHALL be reported to Sentry. On Android and iOS they SHALL be reported through the
platform's own Sentry SDK (`sentry-android`, `sentry-cocoa`), started before anything draws, so
that a native crash is caught as well as a Kotlin one; the web SHALL report through the app's own
reporter. Kotlin's own reports — an uncaught exception, and the failures the app reports without
dying — SHALL reach the SDK on both phones carrying their type and message, and an uncaught
exception SHALL be reported once. Every report SHALL carry the surface and the deal's game id,
round and turn.

A report from a release build SHALL be readable in Sentry: it SHALL name the symbols it is read
through, and the symbols Sentry holds for every binary a release ships SHALL carry debug
information. A release whose symbols cannot reach Sentry SHALL NOT be built.

No report SHALL carry anything that identifies a person or a device: no user, no per-install
id, no device id or name, and no room code.

#### Scenario: Frame pacing during bot turn

- **WHEN** a hard bot thinks for up to its time budget
- **THEN** UI animations continue at the display refresh rate

#### Scenario: A release crash reads as code

- **WHEN** a release build on a phone reports a Kotlin exception
- **THEN** Sentry shows the exception's type and message and its frames as named functions,
  read through that build's own mapping or dSYM

#### Scenario: An uncaught exception on iOS is one report

- **WHEN** a Kotlin exception escapes to the top on an iPhone
- **THEN** Sentry receives one event for it, with its Kotlin type and message, and no second
  event for the same death

#### Scenario: Nothing identifying leaves the phone

- **WHEN** any report is sent from a phone
- **THEN** it carries no user, no per-install id and no device id or name, even though the SDK
  adds a per-install id by default
