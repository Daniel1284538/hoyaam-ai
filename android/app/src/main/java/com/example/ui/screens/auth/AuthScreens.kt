package com.example.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FactorDto
import com.example.ui.components.BidiMonoText
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors

@Composable
fun LoginScreen(
    onSignIn: (email: String, password: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    val colors = LocalHoyaamColors.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mascot / Logo — هويام, same illustration the web app uses
                // on every auth screen (AVATAR_SRC in litigation-agent.html),
                // rasterized from that same SVG rather than redrawn.
                Image(
                    painter = painterResource(id = R.drawable.mascot_hoyaam),
                    contentDescription = "هويام",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.gold, CircleShape)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "هويام المحامية الجامدة",
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "منظومة إدارة التقاضي الذكية — جمهورية مصر العربية",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = ArabicSansFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                if (!errorMessage.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colors.danger.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = errorMessage,
                            color = colors.danger,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني") },
                    leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null, tint = colors.accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent,
                        cursorColor = colors.accent,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.inset
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.accent) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent,
                        cursorColor = colors.accent,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.inset
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Sign In Button
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_in_button"),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جارٍ التحقق…", fontSize = 14.sp, fontFamily = ArabicSansFontFamily)
                    } else {
                        Text("دخول إلى النظام", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                    }
                }
            }
        }
    }
}

@Composable
fun MfaEnrollScreen(
    factor: FactorDto,
    onVerifyCode: (code: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    val colors = LocalHoyaamColors.current
    var code by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "تسجيل التحقق بخطوتين (MFA)",
                    color = colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "التحقق بخطوتين إلزامي لحماية القضايا والمستندات. استخدم تطبيق المصادقة (Google Authenticator أو 1Password أو Authy) لإدخال المفتاح أدناه:",
                    color = colors.textDim,
                    fontSize = 13.sp,
                    fontFamily = ArabicSansFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Secret Key Display
                factor.totp?.secret?.let { secret ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colors.inset,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "المفتاح السري (Secret Key):",
                                color = colors.textDim,
                                fontSize = 11.sp,
                                fontFamily = ArabicSansFontFamily
                            )
                            BidiMonoText(
                                text = secret,
                                color = colors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colors.danger.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = errorMessage,
                            color = colors.danger,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }

                // 6-digit Code Input
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("رمز التحقق المكون من ٦ أرقام") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("totp_code_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent,
                        cursorColor = colors.accent,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.inset
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onVerifyCode(code) },
                    enabled = !isLoading && code.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("verify_totp_button"),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("تأكيد والمتابعة", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                    }
                }
            }
        }
    }
}

@Composable
fun MfaChallengeScreen(
    onVerifyCode: (code: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    val colors = LocalHoyaamColors.current
    var code by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.heroBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.heroText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "التحقق بخطوتين (TOTP)",
                    color = colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AmiriFontFamily
                )

                Text(
                    text = "أدخل رمز المصادقة المكون من ٦ أرقام من تطبيقك:",
                    color = colors.textDim,
                    fontSize = 13.sp,
                    fontFamily = ArabicSansFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )

                if (!errorMessage.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.danger.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = errorMessage,
                            color = colors.danger,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp),
                            fontFamily = ArabicSansFontFamily
                        )
                    }
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("رمز الـ ٦ أرقام") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challenge_code_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent,
                        cursorColor = colors.accent,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.inset
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onVerifyCode(code) },
                    enabled = !isLoading && code.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("verify_challenge_button"),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("تأكيد", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = ArabicSansFontFamily)
                    }
                }
            }
        }
    }
}
