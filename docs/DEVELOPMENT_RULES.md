# SEMHAS — Development Rules

## Rule 1 — Android Only
This is a native Android application.
Use Kotlin + Jetpack Compose + Material 3.

Never generate:
- HTML
- CSS
- React
- React Native
- Flutter
- web dashboards
- browser UI

## Rule 2 — Follow the Documents
PRD.md, DESIGN.md, ARCHITECTURE.md, PHASES.md, DATA_MODEL.md, BILLING_LOGIC.md and UX_GUIDELINES.md are the source of truth.

Do not independently redesign the product.

## Rule 3 — No Feature Invention
Do not add screens, features, navigation items, technologies, or workflows that are not specified.

If something is unspecified, choose the smallest implementation consistent with the documents. Do not expand scope.

## Rule 4 — Mock First
Phase 1 uses mock/local data only.
No Supabase.
No ESP32 communication.
No MQTT.
No Bluetooth hardware control.
No INA219 hardware integration.

## Rule 5 — Repository Boundary
UI never accesses mock data directly.
All data goes through repository interfaces.

## Rule 6 — No Hardcoded UI Mock Data
Do not place appliance readings or billing values directly inside Composables.

## Rule 7 — Reusable Components
Create shared components for repeated:
- cards
- buttons
- status indicators
- metric displays
- channel controls
- section headers
- empty/error states

## Rule 8 — Design System
Centralize:
- colors
- typography
- dimensions
- spacing
- shapes

Do not scatter arbitrary design constants.

## Rule 9 — Screen Responsibility
One screen = one clear responsibility.
Avoid giant Composable files.

## Rule 10 — Buildability
Every generated stage must remain buildable in Android Studio.

Do not leave fake imports, unresolved references, placeholder TODOs that break compilation, or pseudocode in production source files.

## Rule 11 — Navigation
Centralize routes and navigation.

## Rule 12 — Billing Accuracy
Use precise numeric calculations.
Round only for display.

## Rule 13 — Future Backend Compatibility
Do not couple UI code to MockSemhasRepository.
The mock implementation will later be replaced by Supabase.

## Rule 14 — No Unnecessary Architecture
Do not add complexity merely because it is common in large apps.
Keep the architecture simple enough for this project to understand and maintain.

## Rule 15 — Final Product Standard
The result must feel like a professional smart-home application:
clean, consistent, responsive, polished, and usable.
