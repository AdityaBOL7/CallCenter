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
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callcenter.ui.theme.AppColor
import com.example.callcenter.ui.theme.AppGradients
import com.example.callcenter.ui.theme.Brand50
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.OrbIndigo
import com.example.callcenter.ui.theme.OrbPink
import com.example.callcenter.ui.theme.OrbSky
import com.example.callcenter.ui.theme.OrbViolet
import com.example.callcenter.ui.theme.Success

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onForgotPassword: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardOpen = imeBottomPx > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColor.bg),
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
                onMode = viewModel::setMode,
                onIdentifier = viewModel::setIdentifier,
                onRememberMe = viewModel::setRememberMe,
                onOtp = viewModel::setOtp,
                onSendOtp = viewModel::sendOtp,
                onVerifyOtp = { viewModel.verifyOtp(onSuccess = onAuthenticated) },
                onResendOtp = viewModel::resendOtp,
                onChangeIdentifier = viewModel::changeIdentifier,
            )
            Spacer(Modifier.weight(1f))
            if (!keyboardOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = AppColor.ink400, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Secured by 256-bit encryption",
                        color = AppColor.ink400,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("v1.0.0", color = AppColor.ink400, fontSize = 11.sp)
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
                Icons.Outlined.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Agent Dialer", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColor.ink900)
        Spacer(Modifier.height(2.dp))
        Text("Sign in to start your shift", fontSize = 13.sp, color = AppColor.ink500)
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
    onMode: (LoginMode) -> Unit,
    onIdentifier: (String) -> Unit,
    onRememberMe: (Boolean) -> Unit,
    onOtp: (String) -> Unit,
    onSendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangeIdentifier: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = AppColor.surface,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val isOtpStep = state.step == LoginStep.OTP
            val isPhone = state.mode == LoginMode.PHONE

            Text(
                if (isOtpStep) "Enter code" else "Welcome back",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.ink900,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (isOtpStep) "We sent a 6-character code to verify it's you"
                else "Sign in with a one-time code",
                color = AppColor.ink500,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (!isOtpStep) {
                FieldLabel("LOGIN WITH")
                Spacer(Modifier.height(6.dp))
                ModeToggle(mode = state.mode, onMode = onMode)
                Spacer(Modifier.height(14.dp))

                FieldLabel(if (isPhone) "PHONE NUMBER" else "EMAIL ADDRESS")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.identifier,
                    onValueChange = onIdentifier,
                    placeholder = {
                        Text(
                            if (isPhone) "98765xxxxx" else "you@example.com",
                            color = AppColor.ink400,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isPhone) Icons.Outlined.Phone else Icons.Outlined.AlternateEmail,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = brandFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPhone) KeyboardType.Phone else KeyboardType.Email,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                RememberMeRow(checked = state.rememberMe, onCheckedChange = onRememberMe)
            } else {
                FieldLabel("VERIFICATION CODE")
                Spacer(Modifier.height(10.dp))
                OtpBoxes(
                    value = state.otp,
                    onValueChange = onOtp,
                    enabled = !state.submitting,
                )
            }

            if (state.info != null && state.error == null) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(state.info)
            }
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(state.error)
            }

            Spacer(Modifier.height(16.dp))
            SignInGradientButton(
                text = when {
                    state.submitting && isOtpStep -> "Verifying…"
                    state.submitting -> "Sending code…"
                    isOtpStep -> "Verify & sign in"
                    isPhone -> "Send OTP via SMS"
                    else -> "Send OTP via Email"
                },
                loading = state.submitting,
                onClick = if (isOtpStep) onVerifyOtp else onSendOtp,
            )

            if (isOtpStep) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Change email/phone",
                        color = AppColor.ink500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !state.submitting) { onChangeIdentifier() },
                    )
                    Text(
                        text = "Resend code",
                        color = Brand600,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !state.submitting) { onResendOtp() },
                    )
                }
            }
        }
    }
}

/** Segmented Email / Phone switcher, styled like the reference. */
@Composable
private fun ModeToggle(mode: LoginMode, onMode: (LoginMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColor.surfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeTab(
            label = "Email",
            icon = Icons.Outlined.AlternateEmail,
            selected = mode == LoginMode.EMAIL,
            onClick = { onMode(LoginMode.EMAIL) },
            modifier = Modifier.weight(1f),
        )
        ModeTab(
            label = "Phone",
            icon = Icons.Outlined.Phone,
            selected = mode == LoginMode.PHONE,
            onClick = { onMode(LoginMode.PHONE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            // Selected tab gets a solid brand fill so it reads as clearly selected
            // even on the dark card (a plain surface fill was the same color as the
            // card behind it and looked unselected).
            .background(if (selected) Brand600 else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else AppColor.ink500,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            color = if (selected) Color.White else AppColor.ink500,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Six individual OTP boxes backed by a single hidden text field. */
@Composable
private fun OtpBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    Box {
        // Invisible field that actually owns the input + keyboard + paste.
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { focusRequester.requestFocus() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(OTP_LENGTH) { i ->
                val char = value.getOrNull(i)?.uppercaseChar()?.toString() ?: ""
                val filled = char.isNotEmpty()
                val active = i == value.length
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (filled) Brand50 else AppColor.ink50)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active || filled) Brand500 else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = enabled) { focusRequester.requestFocus() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        char,
                        color = AppColor.ink900,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun RememberMeRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.size(4.dp))
        Text("Remember me", color = AppColor.ink700, fontSize = 13.sp)
    }
}

private const val OTP_LENGTH = 6

@Composable
private fun brandFieldColors() = OutlinedTextFieldDefaults.colors(
    // Pin the input/cursor/icon colors so the typed text stays readable on the
    // card in BOTH light and dark mode (default onSurface was too low-contrast
    // against the dark filled container).
    focusedTextColor = AppColor.ink900,
    unfocusedTextColor = AppColor.ink900,
    // Theme-aware filled container in BOTH states so ink900 text always contrasts
    // (a light Brand50 fill would wash out the dark-mode text). The focus accent
    // is carried by the border + cursor instead of the fill color.
    focusedContainerColor = AppColor.surfaceAlt,
    unfocusedContainerColor = AppColor.surfaceAlt,
    cursorColor = Brand500,
    focusedLeadingIconColor = Brand600,
    unfocusedLeadingIconColor = AppColor.ink500,
    focusedPlaceholderColor = AppColor.ink400,
    unfocusedPlaceholderColor = AppColor.ink400,
    focusedBorderColor = Brand500,
    unfocusedBorderColor = AppColor.ink200,
)

@Composable
private fun InfoBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECFDF5))
            .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, color = Color(0xFF047857), fontSize = 13.sp)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = AppColor.ink500,
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
