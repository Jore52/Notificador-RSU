package com.example.notificadorrsuv5.ui.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.ui.AppRoutes
import com.example.notificadorrsuv5.ui.CustomNavigationDrawer
import com.example.notificadorrsuv5.ui.screens.main.components.FeatureCard
import com.example.notificadorrsuv5.ui.screens.project_list.ProjectsListViewModel
import com.example.notificadorrsuv5.ui.theme.BrandBluePrimary
import com.example.notificadorrsuv5.ui.theme.SearchBarBackground
import kotlinx.coroutines.launch

data class Feature(val title: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: ProjectsListViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val features = listOf(
        Feature("Proyectos", Icons.Default.Folder, AppRoutes.PROJECTS_SCREEN),
        Feature("Integrantes", Icons.Default.Groups, AppRoutes.PROJECT_SELECTION_MEMBERS_SCREEN),
        Feature("Certificados", Icons.Default.WorkspacePremium, AppRoutes.CERTIFICATES)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CustomNavigationDrawer(
                navController = navController,
                drawerState = drawerState,
                scope = scope,
                currentRoute = AppRoutes.MAIN_SCREEN,
                currentUser = currentUser,
                onLogout = {
                    viewModel.onLogoutClicked {
                        navController.navigate(AppRoutes.SPLASH_LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Box(modifier = Modifier.background(BrandBluePrimary)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = currentUser?.photoUrl?.toString(),
                                    placeholder = painterResource(id = R.drawable.ic_google_logo)
                                ),
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { /* TODO */ }) {
                            BadgedBox(badge = { Badge { Text("3") } }) {
                                Icon(Icons.Default.Notifications, "Notificaciones", tint = Color.White)
                            }
                        }
                    }
                }
            },
            bottomBar = {
                CustomBottomAppBarWithFab(navController = navController)
            },
            floatingActionButton = {},
            floatingActionButtonPosition = FabPosition.Center
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = SearchBarBackground,
                        focusedContainerColor = SearchBarBackground,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(features) { feature ->
                        FeatureCard(
                            icon = feature.icon,
                            title = feature.title,
                            onClick = { navController.navigate(feature.route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomBottomAppBarWithFab(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        BottomAppBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = BrandBluePrimary,
            contentColor = Color.White,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(icon = Icons.Outlined.Home, label = "Inicio", isSelected = true, onClick = {})
                BottomNavItem(icon = Icons.Outlined.BarChart, label = "Dashboard", isSelected = false, onClick = {})
                Spacer(modifier = Modifier.weight(1f))
                BottomNavItem(icon = Icons.Outlined.MailOutline, label = "Mensajes", isSelected = false, onClick = { navController.navigate(AppRoutes.SENT_EMAILS) })
                BottomNavItem(icon = Icons.Default.Settings, label = "Ajustes", isSelected = false, onClick = {})
            }
        }

        FloatingActionButton(
            onClick = { navController.navigate(AppRoutes.PROJECT_EDIT_BASE + "/-1") },
            shape = CircleShape,
            containerColor = Color.White,
            contentColor = BrandBluePrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(Icons.Filled.Add, "Agregar Proyecto")
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = contentColor)
        Text(text = label, color = contentColor, fontSize = 12.sp)
    }
}
