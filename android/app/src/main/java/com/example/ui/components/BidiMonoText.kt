package com.example.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHoyaamColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun BidiMonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalHoyaamColors.current.text,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = MonoFontFamily,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
    }
}
