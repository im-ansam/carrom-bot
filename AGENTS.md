# Carrom Bot engineering instructions

## Ticket and delivery workflow
- Read the relevant Jira issue, parent epic and dependencies before implementing. Site: https://ansamcd.atlassian.net, project CB.
- Work on one Jira ticket at a time. Respect existing architecture and dependency order.
- Inspect existing files before editing. Make focused, reviewable changes; avoid unrelated cleanup.
- Use branches `codex/CB-<number>-<description>` and commits `CB-<number>: <summary>`.
- Add tests alongside implementation, including regression tests for bug fixes where practical.
- Run relevant compilation, build, lint and tests. Never claim a command succeeded without executing it.
- Never silently ignore failures. Report exact commands, outcomes, NO-SOURCE tasks and outstanding validation.
- Create a PR against the default branch with implementation summary, Jira link and actual test evidence.
- Update Jira with the PR and validation when integration is available. Do not mark Done before review.
- Standing user authorization (2026-11-15): continue each ticket through PR review and merge without
  requesting routine confirmation. Wait for automatic Codex review; address findings, validate and push
  fixes, then wait for re-review. If a push does not trigger review, request it with `@codex review`.
- Merge only after Codex review explicitly completes with no unresolved findings and a thumbs-up on
  the latest PR head, all required CI checks pass, and merge requirements are satisfied. Never bypass
  branch protection or treat a stale review as approval. This authorizes normal PR merges, not direct
  pushes to protected branches.
- After verifying merge, update Jira with review/CI/merge evidence and mark the ticket Done. Start the
  next dependency-ready Jira ticket in a new Codex chat, carrying forward these instructions and
  relevant state. Repeat this one-ticket-per-chat loop without waiting for user intervention.
- Pause only for a concrete blocker requiring user input, unavailable authorization or external
  access; report the blocker clearly. Do not create concurrent implementation chats for this loop.
- Never commit secrets, credentials, signing keys, local SDK paths or sensitive captured screen data.

## Kotlin conventions
- Use idiomatic, null-safe Kotlin; prefer immutable values and collections.
- Declare explicit types and visibility at public API boundaries. JVM libraries enable explicit API mode.
- Avoid unnecessary `!!`, reflection, global mutable state and speculative abstractions.
- Prefer composition; introduce interfaces only at meaningful substitution or platform boundaries.
- Use structured concurrency and lifecycle-owned scopes. Propagate cancellation; never swallow CancellationException.
- Make failures explicit; do not substitute plausible data for failed capture, vision or simulation.
- Packages use `io.github.carrombot`, then `core.<module>` for library code. Types use PascalCase,
  functions/properties camelCase, constants UPPER_SNAKE_CASE; test names describe behavior.
- Keep functions focused and classes cohesive. Document contracts, units, ownership and failure semantics in KDoc.

## Architecture
- `:app` composes dependencies. `:core:model` owns shared platform-free data contracts.
- `:core:physics` depends on model. `:core:planner` depends on physics and model.
- `:core:vision`, `:core:capture` and `:core:overlay` are Android adapters depending on model.
- Never introduce Android, OpenCV, native or UI dependencies into model, physics or planner.
- Keep physics/planning runnable and testable on the JVM without an Android SDK.
- Future simulation must use fixed steps, stable iteration order, explicit units and injected seeds.
  Do not read wall-clock time or use uncontrolled randomness in simulation.
- Share game-state models across vision, simulation, planning and overlay; specify coordinate transforms explicitly.
- Isolate future OpenCV objects/native memory in vision. Define ownership and deterministic release.
- Request Android permissions at the point of use, handle denial/revocation and bind resources to lifecycle.
- Screen capture and overlays require explicit user consent and resource cleanup.
- Do not add gameplay, CV, physics, planner or capture placeholder behavior in CB-10.

## Testing and review
- Add JUnit JVM tests for domain behavior, including failure/edge cases.
- Physics changes need deterministic regression fixtures with versioned inputs and tolerances.
- Add integration tests for module contracts; add instrumentation tests for Android-specific behavior.
- Use red/green/refactor for meaningful logic changes. Do not create dummy tests to inflate coverage.
- Review correctness, dependency direction, concurrency/cancellation, resource leaks, security and performance.
- Keep ordinary unit tests independent of secrets, external services and devices.
- Review the complete diff and staged file list before committing.
- Record significant architectural choices as ADRs in docs/adr; keep README and module graph current.
- Prefer the small reviewed official skills listed in docs/codex-setup.md. Repository guidance supplies
  Android, architecture, TDD, review and documentation conventions absent from the official catalog.

## Commands
Use `gradlew.bat` on Windows and `./gradlew` on Unix.
- Full validation: `./gradlew projects assembleDebug test check lint`
- SDK-free domain validation: `./gradlew -PjvmOnly projects test check`
- Formatting: `./gradlew -PjvmOnly ktlintCheck` or `ktlintFormat`
- Static analysis: `./gradlew -PjvmOnly detekt`
- Instrumentation (when tests/device exist): `./gradlew connectedDebugAndroidTest`
