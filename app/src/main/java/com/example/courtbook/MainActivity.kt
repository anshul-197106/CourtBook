package com.example.courtbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Green = Color(0xFF176B45)
private val LightGreen = Color(0xFFE9F5EE)
private val Page = Color(0xFFF7F9F7)
private val Ink = Color(0xFF173B2B)

data class Venue(
    val id: String = "",
    val name: String = "",
    val sport: String = "",
    val location: String = "",
    val price: Long = 0,
    val rating: String = "New",
    val ownerId: String = "",
    val description: String = "",
    val active: Boolean = true
)

data class CourtUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "PLAYER"
)

data class CourtBooking(
    val id: String = "",
    val venueId: String = "",
    val venueName: String = "",
    val userId: String = "",
    val date: String = "",
    val time: String = "",
    val price: Long = 0,
    val status: String = "CONFIRMED",
    val paymentStatus: String = "DEMO_PAID",
    val slotKey: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthGate()
            }
        }
    }
}

@Composable
fun CourtBookApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    var profile by remember { mutableStateOf<CourtUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    var authError by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(firebaseUser?.uid) {
        val uid = firebaseUser?.uid
        profile = null
        if (uid == null) {
            loading = false
        } else {
            loading = true
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        profile = CourtUser(
                            uid = uid,
                            name = doc.getString("name") ?: "CourtBook User",
                            email = doc.getString("email") ?: firebaseUser?.email.orEmpty(),
                            role = doc.getString("role") ?: "PLAYER"
                        )
                    } else {
                        authError = "Your profile is missing. Please sign out and register again."
                    }
                    loading = false
                }
                .addOnFailureListener {
                    authError = it.localizedMessage ?: "Could not load your profile."
                    loading = false
                }
        }
    }

    when {
        loading -> CenterLoading()
        firebaseUser == null -> AuthScreen(auth, db, authError)
        profile != null -> MainApp(profile!!, db) { auth.signOut() }
        else -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(authError.ifBlank { "Unable to load account." }, color = MaterialTheme.colorScheme.error)
            Button(onClick = { auth.signOut() }) { Text("Sign out") }
        }
    }
}

@Composable
private fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Green)
    }
}

@Composable
fun AuthScreen(auth: FirebaseAuth, db: FirebaseFirestore, initialError: String) {
    var registering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("PLAYER") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialError) }

    Column(
        Modifier.fillMaxSize().background(Page).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("COURTBOOK", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(12.dp))
        Text(if (registering) "Create your account" else "Welcome back.", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Book a court. Bring your team.", color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        if (registering) {
            Spacer(Modifier.height(14.dp))
            Text("Register as", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(role == "PLAYER", { role = "PLAYER" }, label = { Text("Player") })
                FilterChip(role == "OWNER", { role = "OWNER" }, label = { Text("Venue owner") })
            }
            Text("For this demo, owner registration is open. A real service should approve owners.",
                color = Color.Gray, fontSize = 11.sp)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                message = ""
                if (email.isBlank() || !email.contains("@") || password.length < 6 || (registering && name.isBlank())) {
                    message = "Enter a valid email, name and password (at least 6 characters)."
                    return@Button
                }
                busy = true
                if (registering) {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid
                            if (uid == null) {
                                busy = false
                                message = "Account created, but user ID was unavailable."
                            } else {
                                val data = mapOf(
                                    "uid" to uid, "name" to name.trim(),
                                    "email" to email.trim(), "role" to role,
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("users").document(uid).set(data)
                                    .addOnSuccessListener { busy = false }
                                    .addOnFailureListener {
                                        busy = false
                                        message = "Profile save failed: ${it.localizedMessage}"
                                    }
                            }
                        }
                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Registration failed." }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { busy = false }
                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Login failed." }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text(if (registering) "Create account" else "Login")
        }
        TextButton(
            onClick = { registering = !registering; message = "" },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (registering) "Already have an account? Login" else "New to CourtBook? Create account", color = Green)
        }
    }
}

@Composable
fun MainApp(user: CourtUser, db: FirebaseFirestore, onLogout: () -> Unit) {
    var venues by remember { mutableStateOf<List<Venue>>(emptyList()) }
    var venuesLoading by remember { mutableStateOf(true) }
    var venuesError by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("All") }
    var bookingVenue by remember { mutableStateOf<Venue?>(null) }
    var infoMessage by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val listener: ListenerRegistration = db.collection("venues")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    venuesError = error.localizedMessage ?: "Could not load venues"
                    venuesLoading = false
                } else {
                    venues = snapshot?.documents?.map { d ->
                        Venue(
                            id = d.id, name = d.getString("name") ?: "",
                            sport = d.getString("sport") ?: "",
                            location = d.getString("location") ?: "",
                            price = d.getLong("price") ?: 0L,
                            rating = d.getString("rating") ?: "New",
                            ownerId = d.getString("ownerId") ?: "",
                            description = d.getString("description") ?: "",
                            active = d.getBoolean("active") ?: true
                        )
                    } ?: emptyList()
                    venuesLoading = false
                }
            }
        onDispose { listener.remove() }
    }

    val tabs = if (user.role == "OWNER") listOf("Explore", "Bookings", "Owner", "Profile")
               else listOf("Explore", "Bookings", "Profile")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; bookingVenue = null },
                        icon = { Text(when (label) { "Explore" -> "⌂"; "Bookings" -> "▣"; "Owner" -> "▤"; else -> "♙" }) },
                        label = { Text(label) }
                    )
                }
            }
        },
        containerColor = Page
    ) { padding ->
        if (bookingVenue != null) {
            BookingScreen(bookingVenue!!, user, db, { bookingVenue = null }) {
                bookingVenue = null
                selectedTab = 1
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxWidth().background(Green).padding(20.dp)) {
                    Text("COURTBOOK", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (tabs[selectedTab]) {
                            "Explore" -> "Find your game."
                            "Bookings" -> "Your bookings"
                            "Owner" -> "Venue dashboard"
                            else -> "Your profile"
                        },
                        color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Book a court. Bring your team.", color = Color(0xFFD9F0E2), fontSize = 14.sp)
                    if (tabs[selectedTab] == "Explore") {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            search, { search = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search sport or city...") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
                            )
                        )
                    }
                }
                when (tabs[selectedTab]) {
                    "Explore" -> {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            Text("Choose your sport", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("All", "Box Cricket", "Football", "Badminton").forEach {
                                    FilterChip(sport == it, { sport = it }, label = { Text(it, fontSize = 10.sp) })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Popular venues", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                        when {
                            venuesLoading -> CenterLoading()
                            venuesError.isNotBlank() -> Text(venuesError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                            else -> {
                                val filtered = venues.filter {
                                    (sport == "All" || it.sport == sport) &&
                                    "${it.name} ${it.sport} ${it.location}".contains(search, true)
                                }
                                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(filtered, key = { it.id }) { VenueCard(it) { bookingVenue = it } }
                                    if (filtered.isEmpty()) item { Text("No venues yet. Owners can add venues from the Owner tab.") }
                                }
                            }
                        }
                    }
                    "Bookings" -> BookingList(user, db)
                    "Owner" -> OwnerDashboard(user, db, venues.filter { it.ownerId == user.uid }) { infoMessage = it }
                    else -> ProfileScreen(user, onLogout)
                }
            }
        }
    }
    if (infoMessage.isNotBlank()) {
        AlertDialog(onDismissRequest = { infoMessage = "" }, confirmButton = {
            TextButton(onClick = { infoMessage = "" }) { Text("OK") }
        }, text = { Text(infoMessage) })
    }
}

@Composable
fun VenueCard(venue: Venue, onBookClick: (Venue) -> Unit) {
    var details by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).background(LightGreen, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("🏟️", fontSize = 28.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(venue.name, fontWeight = FontWeight.Bold, color = Ink)
                    Text("${venue.sport} • ${venue.location}", fontSize = 13.sp, color = Color.Gray)
                    Text("★ ${venue.rating}", color = Color(0xFFB77900))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Starting from", fontSize = 12.sp, color = Color.Gray)
                    Text("₹${venue.price} / hour", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Green)
                }
                Button(onClick = { details = true }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("View venue") }
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(venue.name) },
        text = { Text("${venue.sport}\n${venue.location}\n${venue.description}\n₹${venue.price} per hour") },
        confirmButton = {
            Button(onClick = { details = false; onBookClick(venue) }, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text("Book this venue")
            }
        },
        dismissButton = { TextButton(onClick = { details = false }) { Text("Close") } }
    )
}

@Composable
fun BookingScreen(venue: Venue, user: CourtUser, db: FirebaseFirestore, onBack: () -> Unit, onDone: () -> Unit) {
    val dateOptions = remember { listOf(todayLabel(0) to dateValue(0), todayLabel(1) to dateValue(1)) }
    var date by remember { mutableStateOf(dateOptions.first().second) }
    var time by remember { mutableStateOf("6:00 PM") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showPayment by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Page).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("← Back to venues", color = Green) }
        Text("Book your court", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(venue.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("${venue.sport} • ${venue.location}", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Text("Choose date", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dateOptions.forEach { (label, value) ->
                FilterChip(date == value, { date = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Choose time", fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("4:00 PM", "5:00 PM", "6:00 PM", "7:00 PM", "8:00 PM").forEach {
                FilterChip(time == it, { time = it }, label = { Text(it) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Total: ₹${venue.price}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Green)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showPayment = true; error = "" }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green), enabled = !busy) {
            Text("Continue to demo payment")
        }
        Text("Demo payment only. No real money will be charged.", color = Color.Gray, fontSize = 12.sp)
    }

    if (showPayment) AlertDialog(
        onDismissRequest = { if (!busy) showPayment = false },
        title = { Text("Demo checkout") },
        text = { Text("Venue: ${venue.name}\nDate: $date\nTime: $time\nAmount: ₹${venue.price}\n\nChoose success to simulate payment. No real payment is processed.") },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                error = ""
                val bookingRef = db.collection("bookings").document()
                val slotKey = "${venue.id}_${date}_${time.replace(":", "").replace(" ", "")}"
                    .replace("/", "-").replace(" ", "")
                val slotRef = db.collection("slotReservations").document(slotKey)
                val bookingData = hashMapOf<String, Any>(
                    "bookingId" to bookingRef.id,
                    "slotKey" to slotKey,
                    "venueId" to venue.id,
                    "venueName" to venue.name,
                    "sport" to venue.sport,
                    "location" to venue.location,
                    "userId" to user.uid,
                    "userEmail" to user.email,
                    "date" to date,
                    "time" to time,
                    "price" to venue.price,
                    "status" to "CONFIRMED",
                    "paymentStatus" to "DEMO_PAID",
                    "createdAt" to System.currentTimeMillis()
                )
                db.runTransaction { transaction ->
                    val existing = transaction.get(slotRef)
                    if (existing.exists()) throw IllegalStateException("This time slot has already been booked. Please choose another.")
                    transaction.set(slotRef, mapOf(
                        "slotKey" to slotKey, "bookingId" to bookingRef.id,
                        "venueId" to venue.id, "userId" to user.uid,
                        "date" to date, "time" to time
                    ))
                    transaction.set(bookingRef, bookingData)
                    null
                }.addOnSuccessListener {
                    busy = false
                    showPayment = false
                    onDone()
                }.addOnFailureListener {
                    busy = false
                    error = it.localizedMessage ?: "Booking failed. Please try another slot."
                    showPayment = false
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White) else Text("Simulate payment success")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { showPayment = false }) { Text("Cancel") } }
    )
}

@Composable
fun BookingList(user: CourtUser, db: FirebaseFirestore) {
    var bookings by remember { mutableStateOf<List<CourtBooking>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    DisposableEffect(user.uid) {
        val registration = db.collection("bookings").whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { error = e.localizedMessage ?: "Could not load bookings"; loading = false }
                else {
                    bookings = snapshot?.documents?.map { d ->
                        CourtBooking(
                            id = d.id, venueId = d.getString("venueId") ?: "",
                            venueName = d.getString("venueName") ?: "Venue",
                            userId = d.getString("userId") ?: "",
                            date = d.getString("date") ?: "", time = d.getString("time") ?: "",
                            price = d.getLong("price") ?: 0L,
                            status = d.getString("status") ?: "CONFIRMED",
                            paymentStatus = d.getString("paymentStatus") ?: "",
                            slotKey = d.getString("slotKey") ?: ""
                        )
                    }?.sortedByDescending { it.id } ?: emptyList()
                    loading = false
                }
            }
        onDispose { registration.remove() }
    }
    when {
        loading -> CenterLoading()
        error.isNotBlank() -> Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        bookings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No bookings yet. Your confirmed bookings will appear here.") }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(bookings, key = { it.id }) { booking ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(booking.venueName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${booking.date} • ${booking.time}")
                        Text("₹${booking.price}")
                        Text("Booking ID: ${booking.id}", fontSize = 11.sp, color = Color.Gray)
                        Text("Status: ${booking.status} • ${booking.paymentStatus}", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        QrCode(booking.id)
                    }
                }
            }
        }
    }
}

@Composable
fun QrCode(value: String) {
    val bitmap = remember(value) {
        try {
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 280, 280)
            Bitmap.createBitmap(280, 280, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until 280) for (y in 0 until 280) {
                    setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Booking QR code",
        modifier = Modifier.size(140.dp).background(Color.White))
}

@Composable
fun OwnerDashboard(user: CourtUser, db: FirebaseFirestore, ownVenues: List<Venue>, onMessage: (String) -> Unit) {
    var addDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("Box Cricket") }
    var location by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ownerBookings by remember { mutableStateOf<List<CourtBooking>>(emptyList()) }
    var revenue by remember { mutableStateOf(0L) }

    DisposableEffect(user.uid, ownVenues.map { it.id }) {
        val ids = ownVenues.map { it.id }
        if (ids.isEmpty()) {
            ownerBookings = emptyList()
            revenue = 0L
            onDispose { }
        } else {
            // Firestore whereIn supports up to 30 values per query.
            val registration = db.collection("bookings").whereIn("venueId", ids.take(30))
                .addSnapshotListener { snap, _ ->
                    val loaded = snap?.documents?.map { d ->
                        CourtBooking(
                            id = d.id,
                            venueId = d.getString("venueId") ?: "",
                            venueName = d.getString("venueName") ?: "Venue",
                            userId = d.getString("userId") ?: "",
                            date = d.getString("date") ?: "",
                            time = d.getString("time") ?: "",
                            price = d.getLong("price") ?: 0L,
                            status = d.getString("status") ?: "CONFIRMED",
                            paymentStatus = d.getString("paymentStatus") ?: "",
                            slotKey = d.getString("slotKey") ?: ""
                        )
                    } ?: emptyList()
                    ownerBookings = loaded
                    revenue = loaded.filter { it.status == "CONFIRMED" }.sumOf { it.price }
                }
            onDispose { registration.remove() }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Owner overview", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink)
                Text("${ownVenues.size} venue(s) listed", color = Color.Gray)
            }
            Button(onClick = { addDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Add venue") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Bookings", ownerBookings.size.toString(), Modifier.weight(1f))
            SummaryCard("Demo revenue", "₹$revenue", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("Your venues", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (ownVenues.isEmpty()) Text("You haven't added a venue yet.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        ownVenues.forEach { venue ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text(venue.name, fontWeight = FontWeight.Bold)
                    Text("${venue.sport} • ${venue.location}")
                    Text("₹${venue.price}/hour", color = Green, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        db.collection("venues").document(venue.id).update("active", false)
                            .addOnSuccessListener { onMessage("Venue removed from Explore.") }
                            .addOnFailureListener { onMessage(it.localizedMessage ?: "Could not update venue.") }
                    }) { Text("Deactivate venue", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Recent bookings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (ownerBookings.isEmpty()) {
            Text("No bookings for your venues yet.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        } else {
            ownerBookings.take(20).forEach { booking ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(booking.venueName, fontWeight = FontWeight.Bold)
                        Text("${booking.date} • ${booking.time}")
                        Text("₹${booking.price} • ${booking.status}", color = Green)
                        Text("Booking ID: ${booking.id}", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
        Text("Revenue is a demo total from confirmed bookings, not actual collected money.",
            color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
    }

    if (addDialog) AlertDialog(
        onDismissRequest = { if (!busy) addDialog = false },
        title = { Text("Add a venue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Venue name") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("City / location") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Price per hour (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                Text("Sport")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Box Cricket", "Football", "Badminton").forEach {
                        FilterChip(sport == it, { sport = it }, label = { Text(it, fontSize = 10.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val amount = price.toLongOrNull()
                if (name.isBlank() || location.isBlank() || amount == null || amount <= 0) {
                    onMessage("Enter a venue name, location and valid positive price.")
                } else {
                    busy = true
                    val data = mapOf(
                        "name" to name.trim(), "sport" to sport, "location" to location.trim(),
                        "price" to amount, "rating" to "New", "ownerId" to user.uid,
                        "description" to description.trim(), "active" to true,
                        "createdAt" to System.currentTimeMillis()
                    )
                    db.collection("venues").add(data)
                        .addOnSuccessListener {
                            busy = false; addDialog = false
                            name = ""; location = ""; price = ""; description = ""
                            onMessage("Venue added successfully.")
                        }
                        .addOnFailureListener { busy = false; onMessage(it.localizedMessage ?: "Could not add venue.") }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text(if (busy) "Saving..." else "Save venue") }
        },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, color = Green, fontSize = 20.sp)
        }
    }
}

@Composable
fun ProfileScreen(user: CourtUser, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(user.email, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Text("Account type: ${user.role}", color = Green, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Logout") }
    }
}

private fun dateValue(offset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, offset)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun todayLabel(offset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, offset)
    return if (offset == 0) "Today" else "Tomorrow"
}
