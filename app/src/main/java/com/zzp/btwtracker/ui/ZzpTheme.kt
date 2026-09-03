package com.zzp.btwtracker.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors=lightColorScheme(primary=Color(0xFF006B5E),onPrimary=Color.White,primaryContainer=Color(0xFF9EF2DF),onPrimaryContainer=Color(0xFF00201B),secondary=Color(0xFF4A635C),surface=Color(0xFFF7FBF8),surfaceVariant=Color(0xFFDBE5E0),error=Color(0xFFBA1A1A))
private val DarkColors=darkColorScheme(primary=Color(0xFF82D5C3),onPrimary=Color(0xFF00382F),primaryContainer=Color(0xFF005047),secondary=Color(0xFFB1CCC3),surface=Color(0xFF0F1513),surfaceVariant=Color(0xFF3F4945),error=Color(0xFFFFB4AB))

@Composable fun ZzpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme())DarkColors else LightColors,shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(26.dp)),content=content)
}
