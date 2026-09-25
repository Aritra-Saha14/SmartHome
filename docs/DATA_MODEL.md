# SEMHAS — Data Model

## Device
Fields:
- id
- name
- isActive
- connectivityStatus
- lastSeen
- firmwareVersion (mock/display only if useful)

## Channel
Fields:
- channelId
- channelNumber (1–5)
- applianceName
- relayState
- voltage
- current
- power
- energy
- runtime
- estimatedCost
- isHealthy

Exactly five initial channels.

## PowerReading
Fields:
- channelId
- timestamp
- voltage
- current
- power
- energy

## EnergyUsage
Fields:
- period
- totalEnergy
- estimatedCost
- ratePerKwh

## Notification
Fields:
- id
- type
- title
- message
- timestamp
- isRead
- severity

## Billing
Fields:
- billingPeriodStart
- billingPeriodEnd
- ratePerKwh
- consumedEnergyKwh
- estimatedCost

## History Event
Fields:
- id
- timestamp
- channelId (nullable where appropriate)
- eventType
- description

## Rules
- Use typed Kotlin models.
- Keep models independent of Compose UI.
- Do not put UI-specific state into backend/data models unless necessary.
- Preserve enough precision for energy/cost calculations.
