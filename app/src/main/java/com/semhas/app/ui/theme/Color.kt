package com.semhas.app.ui.theme

import androidx.compose.ui.graphics.Color

// AMOLED Black Theme Tokens (Pure AMOLED Black Aesthetic)
val AmoledBackground = Color(0xFF000000)      // Pure Pitch Black
val AmoledCard = Color(0xFF181818)            // Elevated AMOLED Card Surface
val AmoledElevatedSurface = Color(0xFF222222) // Higher elevation / chips
val AmoledBorder = Color(0xFF2A2A2A)          // Crisp Card / Element Border
val AmoledAccentGreen = Color(0xFF00E676)     // Vibrant Electric Green Accent
val AmoledPrimaryContainer = Color(0xFF00381C)// Dark Green Container
val AmoledTextPrimary = Color(0xFFFFFFFF)     // Pure White Primary Text
val AmoledTextSecondary = Color(0xFFB3B3B3)   // Readable Muted Secondary Text

// Dark Theme Aliases (Mapped to AMOLED Tokens)
val DarkBackground = AmoledBackground
val DarkSurface = AmoledCard
val DarkElevatedSurface = AmoledElevatedSurface
val DarkPrimary = AmoledAccentGreen
val DarkPrimaryContainer = AmoledPrimaryContainer
val DarkSecondary = AmoledAccentGreen
val DarkSecondaryContainer = AmoledPrimaryContainer
val DarkTertiary = AmoledAccentGreen
val DarkTextPrimary = AmoledTextPrimary
val DarkTextSecondary = AmoledTextSecondary
val DarkDivider = AmoledBorder

// Light Theme Colors (Clean, Modern, Architectural)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightElevatedSurface = Color(0xFFF1F5F9)
val LightPrimary = Color(0xFF00C853)
val LightPrimaryContainer = Color(0xFFD1FAE5)
val LightSecondary = Color(0xFF00B0FF)
val LightSecondaryContainer = Color(0xFFE0F2FE)
val LightTertiary = Color(0xFF6366F1)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
val LightDivider = Color(0xFFE2E8F0)

// Semantic Status Colors
val StatusActive = Color(0xFF00E676) // Accent Green
val StatusInactive = Color(0xFF757575) // Muted Grey
val StatusWarning = Color(0xFFFFB300) // Amber
val StatusError = Color(0xFFFF3D00) // Red
val StatusConnected = Color(0xFF00E676)
val StatusDisconnected = Color(0xFFFF3D00)
