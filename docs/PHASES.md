# SEMHAS — Development Phases

## Phase 1 — Foundation
- Create native Android project.
- Kotlin + Compose + Material 3.
- Establish package structure.
- Establish theme/design system.
- Establish navigation.
- Establish repository interfaces.
- Establish models.
- Establish ViewModels.

## Phase 2 — Complete UI
Build all specified screens:
- Home
- Monitor
- Control
- Analytics
- History
- Billing
- Notifications
- System Health
- Voice Control
- Settings / Security

All screens must be navigable.

## Phase 3 — Mock Data
Connect all screens to MockSemhasRepository.
Use realistic five-channel data.
Simulate live changes where appropriate.
Implement running estimated billing.

## Phase 4 — Android Studio Testing
- Open/build project in Android Studio.
- Resolve compile errors.
- Test navigation.
- Test light/dark/system theme.
- Test controls.
- Test billing rate changes.
- Test mock realtime updates.
- Test different phone sizes.

## Phase 5 — Supabase
Later:
- Create database schema.
- Create required tables.
- Configure Realtime.
- Add authentication only if later approved.
- Implement Supabase repository.
- Replace mock repository without rewriting UI.

## Phase 6 — ESP32
Later:
- ESP32 communication.
- Device registration/status.
- Five relay states.
- Five INA219 readings.
- Backend synchronization.

## Phase 7 — End-to-End
Phone <-> Supabase <-> ESP32 <-> sensors/relay/appliances.

## Scope Rule
Do not implement later-phase technology during earlier phases unless explicitly requested.
