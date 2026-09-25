# SEMHAS — Design System

## 1. Design Direction
Premium, minimal, modern, professional smart-home energy application.
It must look like a polished commercial product, not an Arduino/student dashboard and not a website converted into an app.

## 2. Platform
Native Android with Jetpack Compose and Material 3.

## 3. Visual Principles
- Strong hierarchy
- Generous but efficient spacing
- Clear cards
- Minimal visual noise
- Consistent iconography
- Consistent button dimensions
- Clear active/inactive states
- Easy one-handed interaction
- No unnecessary decoration
- No gradients unless required by an explicitly defined visual element
- No random colors chosen per screen

## 4. Theme
Support:
- Light
- Dark
- System default

All colors must be centralized in the theme/design system.

Use semantic roles rather than hardcoding colors inside screens:
- background
- surface
- elevated surface
- primary
- secondary
- success
- warning
- error
- text primary
- text secondary
- divider

## 5. Spacing
Use centralized spacing tokens:
- 4dp
- 8dp
- 12dp
- 16dp
- 20dp
- 24dp
- 32dp

Default screen horizontal padding: 16–20dp.
Cards: consistent internal padding, normally 16dp.
Do not create arbitrary spacing values throughout the codebase.

## 6. Shapes
Use a consistent modern rounded shape system.
Suggested:
- small: 8dp
- medium: 12dp
- large cards: 16dp
- major surfaces: 20dp

Do not mix many unrelated corner radii.

## 7. Typography
Use Material 3 typography with a restrained hierarchy:
- Screen title
- Section title
- Card title
- Primary metric
- Supporting metric
- Body
- Caption

Avoid oversized typography that wastes mobile space.

## 8. Buttons
Primary and secondary buttons should use consistent dimensions.
Minimum interactive height: 48dp.
Buttons must have clear pressed/disabled/loading states.
Do not create a new button style for every screen.

## 9. Touch Targets
Interactive elements should provide approximately 48dp touch targets.
Small icon actions must remain comfortably tappable.

## 10. Cards
Reusable card patterns:
- Summary card
- Metric card
- Channel card
- Status card
- Chart card
- Alert card
- Billing card

Cards should not contain excessive nested cards.

## 11. Status Indicators
Use consistent:
- ON / active
- OFF / inactive
- Connected
- Disconnected
- Warning
- Error
- Loading

Status should be communicated through text/icon plus visual treatment, not color alone.

## 12. Dashboard Layout
Top-to-bottom priority:
1. SEMHAS/device status
2. Main energy summary
3. Current power
4. Cost summary
5. Five channel summaries
6. Alerts
7. Quick actions

Do not overcrowd the first screen.

## 13. Monitoring UI
Prioritize readable metrics:
- Voltage
- Current
- Power
- Energy
- Runtime
- Cost

Use charts only when they improve understanding.

## 14. Control UI
Each channel should have a clear identity and one obvious ON/OFF control.
Avoid ambiguous controls.

## 15. Analytics UI
Charts must have:
- Clear title
- Useful axis/labels
- Time period
- Supporting summary
- Empty state when data is unavailable

Do not use decorative charts without meaningful data.

## 16. Billing UI
Billing should make these values visually obvious:
- Rate per kWh
- Energy consumed
- Estimated bill
- Billing period

The running estimated bill is the primary number.

## 17. Navigation
Bottom navigation uses exactly:
Home | Monitor | Control | More

Use Material navigation patterns.
More is the entry point for secondary screens.

## 18. Dialogs / Bottom Sheets
Use dialogs for short confirmations or focused editing.
Use bottom sheets for compact contextual actions where appropriate.
Do not use dialogs for every interaction.

## 19. Responsive Behavior
Design for Android phone screens first.
Avoid fixed widths that break on different phone sizes.
Use Compose adaptive/layout primitives where useful, without changing the product structure.

## 20. Component Rule
Create reusable components for repeated visual patterns.
Do not duplicate nearly identical cards/buttons across screens.

## 21. Prohibited Design Changes
Do not:
- add extra screens
- add extra navigation items
- invent a new design language
- introduce web-style layouts
- use random colors
- create inconsistent button sizes
- create tiny controls
- overload screens with information
