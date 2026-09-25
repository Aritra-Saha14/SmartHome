# SEMHAS — Product Requirements Document

## 1. Product
SEMHAS (Smart Energy Management and Home Automation System) is a native Android application for monitoring and controlling up to five appliance channels connected to an ESP32.

## 2. Current Development Scope
The first implementation is UI + architecture + realistic mock data only.
Do NOT implement Supabase, ESP32 networking, MQTT, Bluetooth control, INA219 hardware integration, authentication, or real voice-device control in this phase.

The architecture must make later replacement of mock data with real backend/hardware data possible without rewriting the UI.

## 3. Technology
- Android only
- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Repository pattern
- Navigation Compose
- ViewModel + StateFlow
- Kotlin Coroutines
- Mock/local repository for Phase 1
- Future backend: Supabase PostgreSQL + Realtime + Auth
- Future hardware: ESP32 + 5 INA219 + 5-channel relay

Do not use React, React Native, Flutter, HTML, CSS, JavaScript, or web UI.

## 4. Product Goals
- Give users one clear place to monitor household energy.
- Allow control of five appliance channels.
- Show realistic live electrical measurements.
- Show energy usage, cost, history, billing, alerts, and system health.
- Provide a polished commercial smart-home experience.
- Keep the first version fully testable without hardware/backend.

## 5. Channels
Exactly five channels in the initial product model:
1. Living Room Light
2. Bedroom Fan
3. TV
4. Kitchen
5. AC

Channel names must be editable by the user.

## 6. Core Screens
The application must contain these planned areas and must NOT invent additional product areas:
1. Home / Dashboard
2. Live Monitoring
3. Channel Control
4. Analytics
5. History
6. Billing
7. Notifications
8. System Health
9. Voice Control
10. Settings / Security

Manual control is part of Channel Control and must not become a separate top-level screen.

## 7. Navigation
Primary bottom navigation:
- Home
- Monitor
- Control
- More

The More section provides:
- Analytics
- History
- Billing
- Notifications
- System Health
- Voice Control
- Settings / Security

Do not add other bottom-navigation destinations.

## 8. Home / Dashboard
Show:
- SEMHAS Active / Not Active
- Connectivity status
- Overall current power
- Today's energy usage
- Current estimated cost
- Total monthly estimated electricity cost
- Five channel status summaries
- Abnormal consumption / fault alerts
- Quick access to monitoring and control

## 9. Live Monitoring
For every channel show:
- Voltage
- Current
- Power
- Energy
- Runtime
- Estimated cost

Provide clear live/updated visual states and charts where appropriate.

## 10. Channel Control
For each of the five channels:
- User-defined appliance name
- ON/OFF state
- Control switch/button
- Current power
- Basic status
- Optional schedule state if represented by the mock model

Do not create complex automation builders in this version.

## 11. Analytics
Show:
- Energy consumption trends
- Daily / weekly / monthly views
- Total consumption
- Highest consuming appliance
- Comparative appliance usage
- Cost trend

Use clear charts and summary cards.

## 12. History
Show historical:
- Energy usage
- Appliance activity
- Control events
- Important alerts

Use filters only where useful and explicitly supported by the mock model. Do not invent unnecessary filters.

## 13. Billing
Billing is an estimated running bill.

User can define electricity rate in ₹/kWh, for example ₹8.00/kWh.

Core formula:
estimated cost = consumed energy (kWh) × user-defined rate (₹/kWh)

Requirements:
- User-editable per-unit rate.
- Continuously accumulating estimated bill as mock energy increases.
- Daily estimated cost.
- Monthly estimated cost.
- Appliance-wise estimated cost.
- Current billing period.
- Energy consumed in current period.
- Estimated current bill.
- Internal calculations retain precision; display currency to 2 decimals.
- Changing the rate recalculates displayed estimated costs.

This is an estimate. Do not represent it as an official electricity-company bill.
Do not implement slab tariffs, taxes, fixed charges, or utility-specific rules in this version.

## 14. Notifications
Show:
- Abnormal consumption
- Device connectivity problems
- Appliance/control events
- System warnings

Include read/unread state and timestamps.

## 15. System Health
Show:
- SEMHAS status
- Connectivity
- Device last seen
- Channel/sensor status
- Relay status
- General system health indicators

All data is mocked in Phase 1.

## 16. Voice Control
Provide a dedicated Voice Control screen as a UI concept only.
Show:
- Voice control status
- Example supported commands
- Listening/idle visual state

Do not implement a wake word or real hardware voice execution in Phase 1.

## 17. Settings / Security
Only include planned settings:
- Theme: Light / Dark / System
- Appliance/channel naming access
- Electricity rate access
- Notification preferences
- Basic app/device preferences
- Security section placeholder

Do not invent account management or authentication flows in Phase 1.

## 18. Mock Data
Mock data must be generated in the data layer, never directly inside Composables.

Mock data must feel realistic:
- Different voltage/current/power per appliance
- Energy values that can update over time
- Runtime values
- Estimated costs
- Device status
- Notifications
- History
- Billing values

Use a repository abstraction so the UI does not know whether data is mock or real.

## 19. Quality
The app must:
- Build in Android Studio.
- Navigate without crashes.
- Have loading, empty, error, and normal states where relevant.
- Use reusable components.
- Use consistent spacing, typography, buttons, cards, icons, and touch targets.
- Support Light, Dark, and System theme.

## 20. Explicit Scope Rule
The implementation must follow this document exactly.
Do not add features, screens, navigation items, technologies, or architecture decisions that are not specified here.
If a visual/detail decision is necessary, follow DESIGN.md and UX_GUIDELINES.md.
