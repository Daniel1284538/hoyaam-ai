package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Matches the real web app's design tokens (litigation-agent.html) —
// previously this file used an unrelated warm beige/gold palette with no
// connection to the actual product, so the Android app didn't read as
// the same brand. Values below are the same hex the web app's :root CSS
// variables use, extended only where this app's structure needs a field
// the web app doesn't have (cardMuted, borderStrong, text2, accentHover,
// hero*) — those are derived, not spec'd, and noted as such.

// Light theme — matches --bg/--card/--border/--text/--text-dim/--accent/
// --good/--warn/--danger/--gold exactly.
val LightBg = Color(0xFFF4F5F7)
val LightCard = Color(0xFFFFFFFF)
val LightCardMuted = Color(0xFFEAECF0) // derived: same as inset
val LightInset = Color(0xFFEAECF0)
val LightBorder = Color(0xFFDCE0E6)
val LightBorderStrong = Color(0xFFC3C9D2) // derived: darker border for emphasis
val LightText = Color(0xFF2A1B5E)
val LightText2 = Color(0xFF5D4A8C) // derived: between text and textDim
val LightTextDim = Color(0xFF8C6BAE)
val LightAccent = Color(0xFFFD6262)
val LightAccentHover = Color(0xFFE14747) // derived: darker accent for pressed/hover
val LightHeroBg = Color(0xFFFFE2E2) // derived: light accent-tinted background
val LightHeroText = Color(0xFFB23232) // derived: dark accent tone for text-on-heroBg
val LightHeroSub = Color(0xFF8C6BAE) // derived: reuse textDim
val LightHeroDecor = Color(0xFFFFC9C9) // derived: decorative light accent tint
val LightGood = Color(0xFF16A34A)
val LightWarn = Color(0xFFD9720A)
val LightDanger = Color(0xFFC3272C)
val LightGold = Color(0xFF9C7A3C)

// Dark theme — matches --bg/--card/--border/--text/--text-2/--text-dim/
// --accent/--good/--warn/--danger/--gold exactly.
val DarkBg = Color(0xFF10131A)
val DarkCard = Color(0xFF171B23)
val DarkCardMuted = Color(0xFF0C0F14) // derived: same as inset
val DarkInset = Color(0xFF0C0F14)
val DarkBorder = Color(0xFF262D39)
val DarkBorderStrong = Color(0xFF3A4356) // derived: lighter border for emphasis
val DarkText = Color(0xFFE8EAED)
val DarkText2 = Color(0xFF8B94A3) // matches --text-2 exactly
val DarkTextDim = Color(0xFF4E5768)
val DarkAccent = Color(0xFFFD6262)
val DarkAccentHover = Color(0xFFFF8080) // derived: lighter accent for dark-mode hover
val DarkHeroBg = Color(0xFF3A1F1F) // derived: dark accent-tinted background
val DarkHeroText = Color(0xFFFF9B9B) // derived: light accent tone for text-on-heroBg
val DarkHeroSub = Color(0xFF8B94A3) // derived: reuse text2
val DarkHeroDecor = Color(0xFF4A2626) // derived: decorative dark accent tint
val DarkGood = Color(0xFF22C55E)
val DarkWarn = Color(0xFFF5A623)
val DarkDanger = Color(0xFFE5484D)
val DarkGold = Color(0xFFC9A66B)
