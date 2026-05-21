package com.example.callcenter.ui.screens.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand50
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Ink400
import com.example.callcenter.ui.theme.Ink50
import com.example.callcenter.ui.theme.Ink500
import com.example.callcenter.ui.theme.Ink900
import com.example.callcenter.ui.theme.OrbIndigo
import com.example.callcenter.ui.theme.OrbPink
import com.example.callcenter.ui.theme.OrbSky
import com.example.callcenter.ui.theme.OrbViolet
import com.example.callcenter.ui.theme.Success

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardOpen = imeBottomPx > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FE)),
    ) {
        // Floating orbs (decorative background)
        FloatingOrb(220.dp, OrbIndigo, delayMs = 0L,
            modifier = Modifier.offset(x = 260.dp, y = (-60).dp))
        FloatingOrb(180.dp, OrbPink, delayMs = 1500L,
            modifier = Modifier.offset(x = (-60).dp, y = 120.dp))
        FloatingOrb(140.dp, OrbSky, delayMs = 3000L,
            modifier = Modifier.offset(x = 280.dp, y = 260.dp))
        FloatingOrb(160.dp, OrbViolet, delayMs = 500L,
            modifier = Modifier.offset(x = (-40).dp, y = 540.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(if (keyboardOpen) 0.1f else 0.6f))
            if (!keyboardOpen) {
                BrandMark()
                Spacer(Modifier.height(24.dp))
            }
            LoginCard(
                state = state,
                onUsername = viewModel::setUsername,
                onPassword = viewModel::setPassword,
                onToggleShowPassword = viewModel::toggleShowPassword,
                onSignIn = { viewModel.signIn(onSuccess = onAuthenticated) },
                onForgotPassword = onForgotPassword,
            )
            Spacer(Modifier.weight(1f))
            if (!keyboardOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ink400, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Secured by 256-bit encryption",
                        color = Ink400,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("v1.0.0", color = Ink400, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp))
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppGradients.loginLogo()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Agent Dialer", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink900)
        Spacer(Modifier.height(2.dp))
        Text("Sign in to start your shift", fontSize = 13.sp, color = Ink500)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Success.copy(alpha = 0.1f))
                .border(1.dp, Success.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Success))
                Spacer(Modifier.size(6.dp))
                Text(
                    "SYSTEMS ONLINE",
                    color = Color(0xFF047857),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

@Composable
private fun LoginCard(
    state: LoginUiState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShowPassword: () -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Welcome back", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink900)
            Spacer(Modifier.height(2.dp))
            Text("Please enter your credentials", color = Ink500, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            FieldLabel("USERNAME")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsername,
                placeholder = { Text("agent.username", color = Ink400) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Brand50,
                    unfocusedContainerColor = Ink50,
                    focusedBorderColor = Brand500,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(12.dp))

            FieldLabel("PASSWORD")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                placeholder = { Text("Enter password", color = Ink400) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = onToggleShowPassword) {
                        Icon(
                            imageVector = if (state.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.RemoveRedEye,
                            contentDescription = null,
                            tint = Ink500,
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Brand50,
                    unfocusedContainerColor = Ink50,
                    focusedBorderColor = Brand500,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(state.error)
            }

            Spacer(Modifier.height(16.dp))
            SignInGradientButton(
                text = if (state.submitting) "Signing in…" else "Sign in",
                loading = state.submitting,
                onClick = onSignIn,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Forgot your password?",
                color = Brand600,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onForgotPassword() },
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = Ink500,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFEF2F2))
            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, color = Color(0xFFB91C1C), fontSize = 13.sp)
    }
}

@Composable
private fun SignInGradientButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "btnScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(if (pressed) 8.dp else 6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(AppGradients.loginButton(pressed))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
        Spacer(Modifier.size(8.dp))
        Icon(
            imageVector = if (loading) Icons.Rounded.HourglassEmpty else Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}
