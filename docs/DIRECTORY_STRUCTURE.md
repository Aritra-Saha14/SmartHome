# SEMHAS — Required Directory Structure

The following structure is the target architecture. Antigravity must create the actual project/files accordingly.

```text
SEMHAS/
├── docs/
│   ├── PRD.md
│   ├── DESIGN.md
│   ├── ARCHITECTURE.md
│   ├── PHASES.md
│   ├── DATA_MODEL.md
│   ├── BILLING_LOGIC.md
│   ├── UX_GUIDELINES.md
│   └── DEVELOPMENT_RULES.md
│
├── app/
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/
│               └── com/
│                   └── semhas/
│                       └── app/
│                           ├── MainActivity.kt
│                           │
│                           ├── navigation/
│                           │   ├── AppNavigation.kt
│                           │   └── Screen.kt
│                           │
│                           ├── ui/
│                           │   ├── theme/
│                           │   │   ├── Color.kt
│                           │   │   ├── Theme.kt
│                           │   │   ├── Type.kt
│                           │   │   └── Dimensions.kt
│                           │   │
│                           │   ├── components/
│                           │   │   ├── AppButton.kt
│                           │   │   ├── AppCard.kt
│                           │   │   ├── StatusIndicator.kt
│                           │   │   ├── PowerCard.kt
│                           │   │   ├── ChannelCard.kt
│                           │   │   ├── EnergyCard.kt
│                           │   │   └── SectionHeader.kt
│                           │   │
│                           │   ├── dashboard/
│                           │   │   ├── DashboardScreen.kt
│                           │   │   └── DashboardViewModel.kt
│                           │   │
│                           │   ├── monitoring/
│                           │   │   ├── MonitoringScreen.kt
│                           │   │   └── MonitoringViewModel.kt
│                           │   │
│                           │   ├── control/
│                           │   │   ├── ControlScreen.kt
│                           │   │   └── ControlViewModel.kt
│                           │   │
│                           │   ├── analytics/
│                           │   │   ├── AnalyticsScreen.kt
│                           │   │   └── AnalyticsViewModel.kt
│                           │   │
│                           │   ├── history/
│                           │   │   ├── HistoryScreen.kt
│                           │   │   └── HistoryViewModel.kt
│                           │   │
│                           │   ├── billing/
│                           │   │   ├── BillingScreen.kt
│                           │   │   └── BillingViewModel.kt
│                           │   │
│                           │   ├── notifications/
│                           │   │   ├── NotificationsScreen.kt
│                           │   │   └── NotificationsViewModel.kt
│                           │   │
│                           │   ├── health/
│                           │   │   ├── HealthScreen.kt
│                           │   │   └── HealthViewModel.kt
│                           │   │
│                           │   ├── voice/
│                           │   │   ├── VoiceScreen.kt
│                           │   │   └── VoiceViewModel.kt
│                           │   │
│                           │   └── settings/
│                           │       ├── SettingsScreen.kt
│                           │       └── SettingsViewModel.kt
│                           │
│                           ├── data/
│                           │   ├── model/
│                           │   │   ├── Device.kt
│                           │   │   ├── Channel.kt
│                           │   │   ├── PowerReading.kt
│                           │   │   ├── EnergyUsage.kt
│                           │   │   ├── Notification.kt
│                           │   │   ├── Billing.kt
│                           │   │   └── HistoryEvent.kt
│                           │   │
│                           │   ├── repository/
│                           │   │   └── SemhasRepository.kt
│                           │   │
│                           │   └── mock/
│                           │       ├── MockSemhasRepository.kt
│                           │       └── MockData.kt
│                           │
│                           └── utils/
│                               ├── Constants.kt
│                               └── Formatters.kt
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Structure Rules
- Do not rename packages without a real build requirement.
- Do not create extra top-level product modules.
- Do not create additional screen folders beyond those defined here.
- Reusable UI belongs in ui/components.
- Theme values belong in ui/theme.
- Models belong in data/model.
- Mock implementation belongs in data/mock.
- Repository contract belongs in data/repository.
- Navigation belongs in navigation.
