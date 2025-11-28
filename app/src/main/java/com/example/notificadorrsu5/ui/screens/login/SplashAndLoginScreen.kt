package com.example.notificadorrsuv5.ui.screens.login

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.ui.theme.AppBackground
import com.example.notificadorrsuv5.ui.theme.BrandBluePrimary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.sin

private enum class ScreenState { Splash, Login }

@Composable
fun SplashAndLoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    var screenState by remember { mutableStateOf(ScreenState.Splash) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Obtenemos el usuario directamente de Firebase para la comprobación rápida del Splash
    val currentUser = FirebaseAuth.getInstance().currentUser

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.google_web_client_id))
            .requestScopes(Scope(GmailScopes.GMAIL_SEND))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    viewModel.onSignInSuccess(account)
                } else {
                    viewModel.onSignInError()
                    Toast.makeText(context, "No se pudo obtener la cuenta.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                viewModel.onSignInError()
                Toast.makeText(context, "Error al iniciar sesión: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.onSignInError()
            // Comentado para no molestar si el usuario solo da atrás
            // Toast.makeText(context, "Inicio de sesión cancelado.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        delay(1500L) // Un poco de delay para ver el logo
        // Como arreglamos el AuthRepository, ahora es seguro navegar si currentUser existe
        if (currentUser != null) {
            onLoginSuccess()
        } else {
            screenState = ScreenState.Login
        }
    }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess()
            viewModel.onLoginHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BrandBluePrimary)) {
        AnimatedWaveBackground()

        val transition = updateTransition(targetState = screenState, label = "Splash to Login")
        val logoBlockOffsetY by transition.animateDp(label = "Logo Block Offset Y", transitionSpec = { tween(1000, easing = EaseInOutCubic) }) { state ->
            if (state == ScreenState.Splash) 0.dp else (-100).dp
        }
        val buttonAlpha by transition.animateFloat(label = "Button Alpha", transitionSpec = { tween(800, delayMillis = 400) }) { state ->
            if (state == ScreenState.Splash) 0f else 1f
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = logoBlockOffsetY - 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.logo_drsu_2),
                contentDescription = "Logo Institucional",
                modifier = Modifier.fillMaxWidth(0.8f),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notificador RSU",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (screenState == ScreenState.Login) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 80.dp)
                    .padding(horizontal = 32.dp)
                    .alpha(buttonAlpha)
            ) {
                LoginButton(
                    isLoading = uiState.isLoading,
                    onClick = {
                        viewModel.onSignInInitiated()
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "Button Scale")

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).scale(scale),
        enabled = !isLoading,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppBackground,
            contentColor = BrandBluePrimary
        ),
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 3.dp)
    ) {
        AnimatedVisibility(visible = !isLoading, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Iniciar sesión con Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = BrandBluePrimary,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun AnimatedWaveBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "Wave Transition")
    val waveColor = Color.White.copy(alpha = 0.3f)

    val dx1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "Wave Dx1"
    )
    val dx2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -1000f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "Wave Dx2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val waveHeight = 150f
        val path1 = Path().apply {
            moveTo(-1000f + dx1, size.height)
            for (i in -1000..size.width.toInt() + 1000 step 10) {
                val x = i.toFloat() + dx1
                val y = size.height - waveHeight + sin(x / 180f) * 30f
                lineTo(x, y)
            }
            lineTo(size.width + 1000f + dx1, size.height)
            close()
        }
        val path2 = Path().apply {
            moveTo(-1000f + dx2, size.height)
            for (i in -1000..size.width.toInt() + 1000 step 10) {
                val x = i.toFloat() + dx2
                val y = size.height - waveHeight + 20f + sin(x / 220f) * 40f
                lineTo(x, y)
            }
            lineTo(size.width + 1000f + dx2, size.height)
            close()
        }

        drawPath(path1, color = waveColor.copy(alpha = 0.5f))
        drawPath(path2, color = waveColor.copy(alpha = 0.8f))
    }
}