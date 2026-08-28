package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@Composable
fun ConfirmedChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Box(
        modifier = modifier
            .background(colors.good.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .border(1.dp, colors.good.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.good,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ArabicSansFontFamily
        )
    }
}

@Composable
fun ProvisionalChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Box(
        modifier = modifier
            .background(colors.heroBg, RoundedCornerShape(100.dp))
            .border(1.dp, colors.heroDecor, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.heroText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ArabicSansFontFamily
        )
    }
}

@Composable
fun DangerChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Box(
        modifier = modifier
            .background(colors.danger.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ArabicSansFontFamily
        )
    }
}

@Composable
fun StageChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Box(
        modifier = modifier
            .background(colors.inset, RoundedCornerShape(100.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ArabicSansFontFamily
        )
    }
}

@Composable
fun GoldChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    Box(
        modifier = modifier
            .background(colors.heroBg, RoundedCornerShape(100.dp))
            .border(1.dp, colors.heroDecor, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.heroText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ArabicSansFontFamily
        )
    }
}

@Composable
fun ConfidenceChip(
    confidence: Double,
    modifier: Modifier = Modifier
) {
    val colors = LocalHoyaamColors.current
    val pct = (confidence * 100).toInt()
    val (bg, textCol) = when {
        pct >= 85 -> colors.good.copy(alpha = 0.15f) to colors.good
        pct >= 60 -> colors.heroBg to colors.heroText
        else -> colors.danger.copy(alpha = 0.15f) to colors.danger
    }

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "ثقة: $pct%",
            color = textCol,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

