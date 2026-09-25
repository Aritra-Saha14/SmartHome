# SEMHAS — UX Guidelines

## 1. Goal
The app must be fast to understand and easy to operate with one hand.

## 2. Information Hierarchy
Every screen must have one obvious primary purpose.
Primary information appears first.
Secondary information remains visually quieter.

## 3. Interaction
- Minimum comfortable touch target around 48dp.
- Clear pressed/disabled/loading states.
- Confirm important actions where needed.
- Never hide critical device state behind ambiguous icons.

## 4. Feedback
Show appropriate:
- loading
- success
- error
- empty
- disconnected
- unavailable
states.

## 5. Controls
For ON/OFF:
- clear current state
- obvious action
- no ambiguous toggle behavior

## 6. Live Data
Live values should look live through subtle updates and timestamps/status where appropriate.
Do not create distracting animations.

## 7. Navigation
Use exactly the navigation defined in PRD.md.
Do not add navigation destinations.

## 8. Forms
For editing:
- channel name
- electricity rate
Use clear labels, validation, sensible keyboard/input types, and confirmation where useful.

## 9. Accessibility
- readable contrast
- meaningful content descriptions for icons
- do not rely only on color
- reasonable text scaling
- touch-friendly controls

## 10. Error Prevention
Prevent invalid electricity rates and malformed names.
Never silently fail.

## 11. Consistency
Same action = same component and visual language throughout the app.

## 12. Prohibited UX
Do not:
- add onboarding unless explicitly requested
- add login unless explicitly requested
- add a web dashboard
- add unnecessary popups
- add unrelated features
- create complex automation flows
