package com.example.notificadorrsuv5.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notificadorrsuv5.HiltActivityEntryPoint
import com.example.notificadorrsuv5.ui.screens.login.SplashAndLoginScreen
import com.example.notificadorrsuv5.ui.screens.main.MainScreen
import com.example.notificadorrsuv5.ui.screens.project_edit.AddEditProjectScreen
import com.example.notificadorrsuv5.ui.screens.project_list.ProjectsListScreen
import com.example.notificadorrsuv5.ui.screens.projects.ProjectsScreen
import com.example.notificadorrsuv5.ui.screens.sent.SentEmailsScreen
import dagger.hilt.android.EntryPointAccessors

object AppRoutes {
    const val SPLASH_LOGIN = "splash_login"
    const val MAIN_SCREEN = "main_screen"
    const val PROJECTS_SCREEN = "projects_screen"
    const val PROJECT_SELECTION_MEMBERS_SCREEN = "project_selection_members"
    const val CERTIFICATES = "certificates_screen"
    const val SENT_EMAILS = "sent_emails"
    const val PROJECT_EDIT_BASE = "project_edit"
    const val PROJECT_EDIT = "$PROJECT_EDIT_BASE/{projectId}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val fileNameResolver = remember(context) {
        EntryPointAccessors.fromActivity(
            context as Activity,
            HiltActivityEntryPoint::class.java
        ).getFileNameResolver()
    }

    NavHost(navController = navController, startDestination = AppRoutes.SPLASH_LOGIN) {

        composable(AppRoutes.SPLASH_LOGIN) {
            SplashAndLoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppRoutes.MAIN_SCREEN) {
                        popUpTo(AppRoutes.SPLASH_LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.MAIN_SCREEN) {
            MainScreen(navController = navController)
        }

        composable(AppRoutes.PROJECTS_SCREEN) {
            ProjectsScreen(navController = navController)
        }

        composable(AppRoutes.PROJECT_SELECTION_MEMBERS_SCREEN) {
            ProjectsListScreen(navController = navController)
        }

        composable(AppRoutes.SENT_EMAILS) {
            SentEmailsScreen(navController = navController)
        }

        composable(
            route = AppRoutes.PROJECT_EDIT,
            arguments = listOf(navArgument("projectId") {
                type = NavType.StringType
                nullable = true
            })
        ) {
            AddEditProjectScreen(
                navController = navController,
                fileNameResolver = fileNameResolver
            )
        }

        composable(AppRoutes.CERTIFICATES) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Pantalla de Certificados - En Construcción")
            }
        }
    }
}