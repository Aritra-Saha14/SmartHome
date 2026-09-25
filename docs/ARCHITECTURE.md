# SEMHAS — Architecture

## 1. Architecture
Use:
- MVVM
- Repository pattern
- Jetpack Compose
- ViewModel
- StateFlow
- Kotlin Coroutines
- Navigation Compose

## 2. Current Phase
The application uses mock data only.

Data flow:
UI -> ViewModel -> Repository -> Mock Repository

## 3. Future Phase
Data flow:
UI -> ViewModel -> Repository -> Supabase

Hardware flow:
Appliance -> INA219 -> ESP32 -> Supabase -> Android

Control flow:
Android -> Supabase -> ESP32 -> Relay -> Appliance

The UI must not directly communicate with Supabase or ESP32.

## 4. Repository Contract
Create a repository interface that exposes the data required by screens.
MockSemhasRepository implements it now.

A future Supabase repository will implement the same interface.

## 5. UI Layer
Each screen owns its UI.
ViewModels own screen state and user actions.
Composable functions must not contain business/data-source logic.

## 6. Data Layer
Models are centralized under data/model.
Repository interfaces are under data/repository.
Mock implementation/data generation is under data/mock.

## 7. State
Use StateFlow for observable screen state.
Use immutable UI state models where practical.

## 8. Navigation
Centralize route definitions.
Do not scatter string routes through Composables.

## 9. Dependency Rule
UI must depend on ViewModels.
ViewModels depend on repository interfaces.
Repository implementations depend on data sources.
Never reverse this direction.

## 10. Billing
Billing calculations belong in the appropriate data/domain logic, not directly inside UI Composables.

## 11. Mock Data
Mock values must not be hardcoded inside UI functions.
The mock repository should simulate changing readings where appropriate.

## 12. Future Replaceability
Replacing MockSemhasRepository with a Supabase implementation should require minimal/no UI changes.

## 13. Avoid Overengineering
Do not add unnecessary Clean Architecture layers, dependency-injection frameworks, services, modules, or abstractions unless required by this specification.
Keep the project understandable and maintainable.
