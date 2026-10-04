# StockSteps agent context

Read `PROJECT_HANDOFF.md` before planning or changing this project. It records
completed features, architecture decisions, setup, verification limits, user
preferences, and pending work. Consult `README.md` for API and run details.
Verify the current code and Git status instead of assuming the handoff is always
up to date. Update the handoff when meaningful work changes the project state.

For every commit, update `PROJECT_HANDOFF.md` in the same commit with completed
work, validation performed, remaining limitations, and the next pending items.
Do not leave the handoff describing committed work as uncommitted. Identify the
current commit by its title; its own hash cannot be embedded before creation.

Navigation conventions: Route files contain destination identity/arguments only.
Scene composables/views own or acquire ViewModels, collect/read observable state,
and wire navigation/events. Screen composables/views receive UI state and action
callbacks (plus native input bindings when needed), and render content without
resolving ViewModels or dependency containers. Preserve this boundary on both
platforms when adding destinations.
