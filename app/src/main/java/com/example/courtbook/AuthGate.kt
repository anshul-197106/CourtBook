
package com.example.courtbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private const val PREFS_NAME = "courtbook_auth"
private const val REMEMBER_KEY = "remember_me"



@Composable
fun AuthGate() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    val prefs = remember {
        context.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
    }

    var currentUser by remember {
        mutableStateOf<com.google.firebase.auth.FirebaseUser?>(null)
    }

    var isCheckingAuth by remember { mutableStateOf(true) }

    // Check the saved login session when the app starts.
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean(REMEMBER_KEY, true)) {
            auth.signOut()
            currentUser = null
            isCheckingAuth = false
        } else {
            val user = auth.currentUser

            if (user == null) {
                isCheckingAuth = false
            } else {
                user.reload().addOnCompleteListener { task ->
                    val refreshedUser = auth.currentUser

                    if (task.isSuccessful &&
                        refreshedUser?.isEmailVerified == true
                    ) {
                        currentUser = refreshedUser
                    } else {
                        currentUser = null
                        auth.signOut()
                    }

                    isCheckingAuth = false
                }
            }
        }
    }

    // Listen for login/logout without replacing the login screen
    // with a loading screen during authentication.
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser

            if (user == null) {
                currentUser = null
            } else {
                user.reload().addOnCompleteListener { task ->
                    val refreshedUser = firebaseAuth.currentUser

                    if (task.isSuccessful &&
                        refreshedUser?.isEmailVerified == true
                    ) {
                        currentUser = refreshedUser
                    }
                    // For unverified users, leave AuthScreen visible.
                    // AuthScreen displays the verification message.
                }
            }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    when {
        isCheckingAuth -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF7F9F7)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF176B45)
                    )
                }
            }
        }

        currentUser != null -> CourtBookApp()

        else -> AuthScreen()
    }
}

@Composable
fun AuthScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    var isRegistering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("PLAYER") }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var rememberMe by remember {
        mutableStateOf(prefs.getBoolean(REMEMBER_KEY, true))
    }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val green = Color(0xFF176B45)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F9F7)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "COURTBOOK",
                color = green,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                if (isRegistering) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Book a court. Bring your team.",
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            if (isRegistering) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            if (isRegistering) {
                Spacer(Modifier.height(12.dp))

                Text("Register as", fontWeight = FontWeight.SemiBold)

                Box {
                    OutlinedButton(
                        onClick = { roleMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedRole)
                    }

                    DropdownMenu(
                        expanded = roleMenuExpanded,
                        onDismissRequest = { roleMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("PLAYER") },
                            onClick = {
                                selectedRole = "PLAYER"
                                roleMenuExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("OWNER") },
                            onClick = {
                                selectedRole = "OWNER"
                                roleMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (!isRegistering) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it }
                        )
                        Text("Remember me")
                    }

                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                message = "Enter your email first."
                            } else {
                                isLoading = true
                                auth.sendPasswordResetEmail(email.trim())
                                    .addOnSuccessListener {
                                        isLoading = false
                                        message =
                                            "Password reset email sent. Check your inbox."
                                    }
                                    .addOnFailureListener { error ->
                                        isLoading = false
                                        message = error.localizedMessage
                                            ?: "Could not send reset email."
                                    }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("Forgot password?", color = green)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (message.isNotBlank()) {
                Text(
                    message,
                    color = if (
                        message.contains("sent", ignoreCase = true) ||
                        message.contains("verify", ignoreCase = true)
                    ) green else MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    message = ""

                    if (email.isBlank() || password.isBlank()) {
                        message = "Enter your email and password."
                        return@Button
                    }

                    if (isRegistering && name.isBlank()) {
                        message = "Enter your full name."
                        return@Button
                    }

                    if (password.length < 6) {
                        message = "Password must contain at least 6 characters."
                        return@Button
                    }

                    isLoading = true

                    if (isRegistering) {
                        auth.createUserWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnSuccessListener { result ->

                            val user = result.user

                            if (user == null) {
                                isLoading = false
                                message = "Account creation failed."
                                return@addOnSuccessListener
                            }

                            val profile = hashMapOf(
                                "uid" to user.uid,
                                "name" to name.trim(),
                                "email" to email.trim(),
                                "role" to selectedRole,
                                "createdAt" to System.currentTimeMillis()
                            )

                            db.collection("users")
                                .document(user.uid)
                                .set(profile)
                                .addOnSuccessListener {

                                    user.sendEmailVerification()
                                        .addOnSuccessListener {
                                            isLoading = false

                                            prefs.edit()
                                                .putBoolean(REMEMBER_KEY, rememberMe)
                                                .apply()

                                            auth.signOut()

                                            isRegistering = false
                                            password = ""

                                            message =
                                                "Account created successfully! To complete your registration, you must verify your email. We have sent a verification link to ${email.trim()}. Open your inbox, click the link, and then log in to CourtBook."
                                        }
                                        .addOnFailureListener { error ->
                                            isLoading = false
                                            message = error.localizedMessage
                                                ?: "Could not send verification email."
                                            auth.signOut()
                                        }
                                }
                                .addOnFailureListener { error ->
                                    isLoading = false
                                    message =
                                        "Profile saving failed: ${error.localizedMessage}"
                                    auth.signOut()
                                }
                        }.addOnFailureListener { error ->
                            isLoading = false
                            message = error.localizedMessage
                                ?: "Registration failed."
                        }

                    } else {
                        auth.signInWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnSuccessListener { result ->

                            val user = result.user

                            if (user == null) {
                                isLoading = false
                                message = "Login failed."
                                auth.signOut()
                                return@addOnSuccessListener
                            }

                            user.reload().addOnSuccessListener {

                                val refreshedUser = auth.currentUser


                                if (refreshedUser?.isEmailVerified != true) {
                                    isLoading = false

                                    message =
                                        "EMAIL VERIFICATION REQUIRED!\n\nYour email address has not been verified. Please open the verification email sent to ${email.trim()}, click the verification link, and then log in again."

                                    refreshedUser?.sendEmailVerification()
                                    auth.signOut()
                                }

                            else {
                                    prefs.edit()
                                        .putBoolean(REMEMBER_KEY, rememberMe)
                                        .apply()

                                    isLoading = false
                                }
                            }.addOnFailureListener { error ->
                                isLoading = false
                                message = error.localizedMessage
                                    ?: "Could not verify your account."
                                auth.signOut()
                            }
                        }.addOnFailureListener { error ->
                            isLoading = false
                            message = "Please verify your email and check your password."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = green
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        if (isRegistering) "Create account" else "Login"
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = {
                    isRegistering = !isRegistering
                    message = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRegistering)
                        "Already have an account? Login"
                    else
                        "New to CourtBook? Create account",
                    color = green
                )
            }
        }
    }
}
