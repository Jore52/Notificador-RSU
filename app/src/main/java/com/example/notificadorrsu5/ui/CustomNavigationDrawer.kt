package com.example.notificadorrsuv5.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.notificadorrsuv5.ui.theme.BrandBluePrimary
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CustomNavigationDrawer(
    navController: NavController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    currentRoute: String,
    currentUser: FirebaseUser?,
    onLogout: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    ModalDrawerSheet(
        modifier = Modifier.width(screenWidth / 2),
        drawerContainerColor = BrandBluePrimary
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DrawerHeader(
                user = currentUser
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DrawerItem(
                    icon = Icons.Default.Home,
                    label = "Inicio",
                    isSelected = currentRoute == AppRoutes.MAIN_SCREEN,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (currentRoute != AppRoutes.MAIN_SCREEN) {
                            navController.navigate(AppRoutes.MAIN_SCREEN) { popUpTo(0) }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DrawerItem(
                    icon = Icons.Default.Email,
                    label = "Historial de Correos",
                    isSelected = currentRoute == AppRoutes.SENT_EMAILS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (currentRoute != AppRoutes.SENT_EMAILS) {
                            navController.navigate(AppRoutes.SENT_EMAILS)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            DrawerItem(
                icon = Icons.Default.Logout,
                label = "Cerrar Sesión",
                isSelected = false,
                onClick = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun DrawerHeader(user: FirebaseUser?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val photoUrl = user?.photoUrl

        if (photoUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(model = photoUrl),
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Foto de perfil por defecto",
                        tint = BrandBluePrimary,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = user?.displayName ?: "Usuario",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = user?.email ?: "email@ejemplo.com",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontWeight = fontWeight,
                fontSize = 16.sp
            )
        }
    }
}
