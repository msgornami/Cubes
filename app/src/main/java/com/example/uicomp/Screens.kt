package com.example.uicomp

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.api.LeaderboardClient
import com.example.api.LeaderboardEntry
import com.example.game.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// Simple state holder for global language setting
var isPersian = mutableStateOf(false)

// Translation Helper
fun t(en: String, fa: String): String {
    return if (isPersian.value) fa else en
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppContent() {
    var currentScreen by remember { mutableStateOf("lobby") } // lobby, matchmaking, game, leaderboard
    var username by remember { mutableStateOf("Player_${Random.nextInt(100, 999)}") }
    var selectedRegion by remember { mutableStateOf("Asia-Pacific") }
    
    // In-game persistent engine
    val engine = remember { GameEngine() }
    var userFinalScore by remember { mutableIntStateOf(0) }
    var userMaxCube by remember { mutableIntStateOf(2) }
    var userKills by remember { mutableIntStateOf(0) }
    var defeatCause by remember { mutableStateOf("") }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("cubes_prefs", Context.MODE_PRIVATE) }
    
    // Load saved name on first boot
    LaunchedEffect(Unit) {
        val savedName = sharedPrefs.getString("username", "") ?: ""
        if (savedName.isNotEmpty()) {
            username = savedName
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
    ) {
        // Floating Cyber Background for menu aesthetics
        if (currentScreen != "game") {
            MenuBackgroundAesthetics()
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(350)) with fadeOut(animationSpec = tween(280))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                "lobby" -> {
                    LobbyScreen(
                        username = username,
                        onUsernameChange = {
                            username = it
                            sharedPrefs.edit().putString("username", it).apply()
                        },
                        selectedRegion = selectedRegion,
                        onRegionChange = { selectedRegion = it },
                        onPlayClick = { currentScreen = "matchmaking" },
                        onLeaderboardClick = { currentScreen = "leaderboard" }
                    )
                }
                "matchmaking" -> {
                    MatchmakingScreen(
                        playerName = username,
                        region = selectedRegion,
                        onFinished = {
                            engine.startNewGame(username)
                            currentScreen = "game"
                        },
                        onCancel = { currentScreen = "lobby" }
                    )
                }
                "game" -> {
                    GameScreen(
                        engine = engine,
                        onGameOver = { score, maxC, kills, reason ->
                            userFinalScore = score
                            userMaxCube = maxC
                            userKills = kills
                            defeatCause = reason
                            currentScreen = "gameover"
                        },
                        onLeave = { currentScreen = "lobby" }
                    )
                }
                "leaderboard" -> {
                    LeaderboardScreen(
                        onBack = { currentScreen = "lobby" }
                    )
                }
                "gameover" -> {
                    GameOverScreen(
                        score = userFinalScore,
                        maxCube = userMaxCube,
                        kills = userKills,
                        reason = defeatCause,
                        playerName = username,
                        region = selectedRegion,
                        onReplay = { currentScreen = "matchmaking" },
                        onBack = { currentScreen = "lobby" }
                    )
                }
            }
        }
    }
}

// Drifting vector particles behind menus
@Composable
fun MenuBackgroundAesthetics() {
    val infiniteTransition = rememberInfiniteTransition(label = "drift")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val floatX by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Solid background fill of the Bento theme backdrop
        drawRect(color = CyberBackground)

        // Draw soft ambient circles simulating the premium bento backlight gradients
        drawCircle(
            color = BentoPastelPurple.copy(alpha = alpha),
            radius = canvasWidth * 0.5f,
            center = Offset(canvasWidth * 0.2f + floatX, canvasHeight * 0.25f)
        )

        drawCircle(
            color = BentoPastelBlue.copy(alpha = alpha),
            radius = canvasWidth * 0.6f,
            center = Offset(canvasWidth * 0.8f - floatX, canvasHeight * 0.75f)
        )
    }
}

@Composable
fun LobbyScreen(
    username: String,
    onUsernameChange: (String) -> Unit,
    selectedRegion: String,
    onRegionChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onLeaderboardClick: () -> Unit
) {
    var showHowToPlay by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP GREETING BAR
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = t("Welcome back,", "خوش آمدید،"),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Text(
                    text = username,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
            
            // Initials Avatar
            val initials = if (username.length >= 2) username.substring(0, 2).uppercase() else "PL"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BentoPastelPurple)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = CyberSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // TILE 1: ONLINE BATTLE (Large Bento Box)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPlayClick() }
                .shadow(4.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CyberPrimary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Background decoration "2048" floating
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 10.dp, y = 14.dp)
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "2048",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = t("ONLINE BATTLE", "رقابت آنلاین نبرد"),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = t("RANKED\nMATCH", "رقابت انتخابی\nمیدان نبرد"),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 32.sp
                        )
                        
                        Text(
                            text = t("Win to earn +45 Trophies", "پیروز شوید تا ۴۵+ کاپ دریافت کنید"),
                            fontSize = 12.sp,
                            color = BentoPastelPurple,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEADDFF)),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = t("PLAY NOW", "شروع بازی"),
                                color = Color(0xFF21005D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF39FF14), CircleShape))
                            Text(
                                text = t("1.2k Live", "۱.۲ هزار زنده"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ROW OF SPLITS: TILE 2 (Rank Stats) & TILE 3 (Region/Ping)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tile 2: Global Record Stats
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp)
                    .border(1.dp, BentoOutline, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BentoPastelPurple, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏆", fontSize = 16.sp)
                    }
                    
                    Column {
                        Text(
                            text = t("GLOBAL rank", "رتبه جهانی"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        
                        Text(
                            text = "#128",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        
                        Text(
                            text = "▲ 12 positions",
                            fontSize = 10.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tile 3: Response latency ping info
            val simulatedPing = when (selectedRegion) {
                "Middle East" -> 16
                "Europe" -> 72
                "Asia-Pacific" -> 118
                else -> 190
            }
            
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                colors = CardDefaults.cardColors(containerColor = BentoPastelBlue),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 14.sp)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = t("ONLINE", "آنلاین"),
                                color = CyberSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = "$simulatedPing ms",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberSecondary
                        )
                        
                        Text(
                            text = selectedRegion.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // TILE 4: PROFILE & SERVERS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BentoOutline, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = t("CHOOSE PLAYER NICKNAME", "نام کاربری خود را وارد کنید"),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isPersian.value) TextAlign.Right else TextAlign.Left
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 14) onUsernameChange(it) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BentoOutline,
                        focusedContainerColor = CyberSurfaceVariant,
                        unfocusedContainerColor = CyberSurfaceVariant,
                        cursorColor = CyberPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CyberPrimary)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = t("SELECT SERVER ZONE", "انتخاب منطقه سرور"),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (isPersian.value) TextAlign.Right else TextAlign.Left
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val displayRegions = listOf(
                        "Eur" to "Europe",
                        "Asia" to "Asia-Pacific",
                        "Amer" to "Americas",
                        "Me" to "Middle East"
                    )

                    displayRegions.forEach { (lbl, regFull) ->
                        val isSelected = selectedRegion == regFull
                        Box(
                          modifier = Modifier
                              .weight(1f)
                              .clip(RoundedCornerShape(12.dp))
                              .background(if (isSelected) CyberPrimary else CyberSurfaceVariant)
                              .clickable { onRegionChange(regFull) }
                              .padding(vertical = 10.dp),
                          contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                color = if (isSelected) Color.White else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // TILE 5: LEADERBOARD PREVIEW
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BentoOutline, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Leaderboard", "رتبه بندی"),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = t("VIEW ALL", "مشاهده همه"),
                        color = CyberPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onLeaderboardClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Leo_Pro
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurfaceVariant, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.size(28.dp).background(BentoPastelPurple, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🦁", fontSize = 14.sp)
                        }
                        Text("Leo_Pro", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Text("824,000", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberPrimary)
                }

                // SarahK
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BentoOutline, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.size(28.dp).background(BentoPastelBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🦊", fontSize = 14.sp)
                        }
                        Text("SarahK", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Text("791,250", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberPrimary)
                }
            }
        }

        // TILE 6: HELP CONSOLE & LANG TAB
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { isPersian.value = !isPersian.value },
                colors = ButtonDefaults.buttonColors(containerColor = BentoPastelPurple),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(
                    text = if (isPersian.value) "🇺🇸 English" else "🇮🇷 فارسی",
                    color = CyberSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { showHowToPlay = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = t("How to Play", "راهنمای بازی"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // How to Play visual Modal dialogue
    if (showHowToPlay) {
        Dialog(onDismissRequest = { showHowToPlay = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, BentoOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("HOW TO CONQUER", "آموزش بقا در میدان نبرد"),
                        fontSize = 20.sp,
                        color = CyberPrimary,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    val tutorialItems = listOf(
                        Pair(Icons.Default.PlayArrow, t(
                            "DRAG TO SLIDE: Guide your snake of numerical cubes inside the arena boundaries.",
                            "حرکت در زمین: دست خود را بکشید تا زنجیره مکعب‌های شما هموار حرکت کند."
                        )),
                        Pair(Icons.Default.Check, t(
                            "COLLECT & MERGE: Moving over free cubes adds them behind you. Nodes with matched adjacent values automatically chainmerge!",
                            "ادغام مکعب‌ها: مکعب‌های هم‌اندازه به سرعت ادغام می‌شوند تا زنجیره کلی را دو برابر کنند."
                        )),
                        Pair(Icons.Default.Send, t(
                            "SPEED BOOST: Press and hold the virtual boost pad on bottom-right to accelerate. Drips smaller body nodes slowly behind.",
                            "شتاب نهایی: دکمه BOOST را نگه‌دارید تا با سرعت دوبرابر حمله کنید (از دست دادن کوچک‌ترین مکعب)."
                        )),
                        Pair(Icons.Default.Warning, t(
                            "EAT OTHERS: You can devour any runner whose head is smaller than your own head cube! Scatter their loops upon victory.",
                            "شکار رقبا: مکعب‌های کوچک‌تر از سر مار شما، طعمه هستند. آن‌ها را ببلعید!"
                        ))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        tutorialItems.forEach { item ->
                            val icon = item.first
                            val desc = item.second
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = CyberPrimary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = desc,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showHowToPlay = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)
                    ) {
                        Text(t("I'M READY", "متوجه شدم"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchmakingScreen(
    playerName: String,
    region: String,
    onFinished: () -> Unit,
    onCancel: () -> Unit
) {
    var searchStep by remember { mutableStateOf(0) }
    val progressPulse = rememberInfiniteTransition(label = "pulse")
    val dotAnimation by progressPulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot"
    )

    val currentMsg = when (searchStep) {
        0 -> t("Authenticating identity secure signature token...", "در حال تایید هویت و امضای امنیتی...")
        1 -> t("Accessing regional server: $region...", "اتصال به نزدیک‌ترین سرور منطقه: $region...")
        2 -> t("Filtering matchups for fair lobby level...", "فیلتر کردن رقبای هم‌سطح در لابی...")
        else -> t("Warm compiling rendering arena grids (15/15)...", "بهینه‌سازی نهایی زمین نبرد (۱۵ از ۱۵)...")
    }

    LaunchedEffect(Unit) {
        delay(1000)
        searchStep = 1
        delay(1200)
        searchStep = 2
        delay(1100)
        searchStep = 3
        delay(800)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High fidelity Radar Loader
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            val scalePulse by progressPulse.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = EaseOutQuad),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scale"
            )
            val alphaPulse by progressPulse.animateFloat(
                initialValue = 0.8f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = EaseOutQuad),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )

            // Radar Ripple circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        drawCircle(
                            color = CyberPrimary.copy(alpha = alphaPulse),
                            radius = size.width / 2f * scalePulse,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
            )

            // Solid inner pulsing core
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(16.dp, CircleShape)
                    .background(CyberPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyberBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = t("MATCHMAKING ENTRANCE", "در حال بخش‌یابی و لابی‌سازی"),
            fontSize = 22.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = currentMsg,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
            shape = RoundedCornerShape(100.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Text(text = t("CANCEL QUEUE", "لغو صف نبرد"), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GameScreen(
    engine: GameEngine,
    onGameOver: (score: Int, maxC: Int, kills: Int, reason: String) -> Unit,
    onLeave: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var frameTrigger by remember { mutableStateOf(0) }
    
    // Touch Joystick vectors
    var joystickActive by remember { mutableStateOf(false) }
    var joystickStartX by remember { mutableFloatStateOf(0f) }
    var joystickStartY by remember { mutableFloatStateOf(0f) }
    var joystickDx by remember { mutableFloatStateOf(0f) }
    var joystickDy by remember { mutableFloatStateOf(0f) }

    // Steer mapping variables using relative pointer dragging anywhere
    var steerX by remember { mutableFloatStateOf(0f) }
    var steerY by remember { mutableFloatStateOf(0f) }

    // Screen dimensions (captured relative to canvas size)
    var viewWidth by remember { mutableFloatStateOf(1080f) }
    var viewHeight by remember { mutableFloatStateOf(2000f) }

    // Game update clock loop
    LaunchedEffect(Unit) {
        while (engine.isRunning) {
            engine.gameTick(steerX, steerY)
            
            // Re-trigger visual recomposition
            frameTrigger++
            
            // Check terminal state
            if (!engine.player.isAlive) {
                // Find killer's head value
                val finalScore = engine.player.score
                val finalMax = engine.player.maxCube
                val finalKills = engine.player.killCount
                onGameOver(finalScore, finalMax, finalKills, t("Devoured by larger cyber rival!", "توسط مکعب قدرتمندتری بلعیده شدید!"))
                break
            }
            delay(16) // Solid ~60 FPS update delays
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        joystickActive = true
                        joystickStartX = offset.x
                        joystickStartY = offset.y
                        joystickDx = 0f
                        joystickDy = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        joystickDx += dragAmount.x
                        joystickDy += dragAmount.y

                        // Clamp joystick range
                        val dist = sqrt(joystickDx * joystickDx + joystickDy * joystickDy)
                        val maxR = 90f
                        if (dist > maxR) {
                            steerX = joystickDx / dist
                            steerY = joystickDy / dist
                        } else {
                            steerX = joystickDx / maxR
                            steerY = joystickDy / maxR
                        }
                    },
                    onDragEnd = {
                        joystickActive = false
                        steerX = 0f
                        steerY = 0f
                    }
                )
            }
    ) {
        // MAIN BATTLEFIELD SCREEN RENDER
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    viewWidth = size.width
                    viewHeight = size.height
                }
        ) {
            val user = engine.player
            val head = user.cubeChain.getOrNull(0) ?: return@Canvas

            // Camera offset centering user head
            val cameraX = head.x - (viewWidth / 2f)
            val cameraY = head.y - (viewHeight / 2f)

            // Draw cyber dots background grid representing moving coordinate space
            val gridSize = 140f
            val startGridX = (cameraX / gridSize).toInt() * gridSize
            val startGridY = (cameraY / gridSize).toInt() * gridSize
            
            val columns = (viewWidth / gridSize).toInt() + 2
            val rows = (viewHeight / gridSize).toInt() + 2

            for (c in 0 until columns) {
                for (r in 0 until rows) {
                    val gx = startGridX + c * gridSize
                    val gy = startGridY + r * gridSize

                    // Screen coordinate transformation
                    val scX = gx - cameraX
                    val scY = gy - cameraY

                    // Draw subtle cyberpunk grid dot
                    drawCircle(
                        color = Color(0xFF181F42),
                        radius = 2.dp.toPx(),
                        center = Offset(scX, scY)
                    )
                }
            }

            // Draw absolute border fences of cyber-dome
            val fenceMinX = 0f - cameraX
            val fenceMaxX = engine.mapWidth - cameraX
            val fenceMinY = 0f - cameraY
            val fenceMaxY = engine.mapHeight - cameraY

            // Draw border bounding lines
            drawLine(
                color = CyberSecondary.copy(alpha = 0.4f),
                start = Offset(fenceMinX, fenceMinY),
                end = Offset(fenceMaxX, fenceMinY),
                strokeWidth = 4.dp.toPx()
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.4f),
                start = Offset(fenceMaxX, fenceMinY),
                end = Offset(fenceMaxX, fenceMaxY),
                strokeWidth = 4.dp.toPx()
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.4f),
                start = Offset(fenceMaxX, fenceMaxY),
                end = Offset(fenceMinX, fenceMaxY),
                strokeWidth = 4.dp.toPx()
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.4f),
                start = Offset(fenceMinX, fenceMaxY),
                end = Offset(fenceMinX, fenceMinY),
                strokeWidth = 4.dp.toPx()
            )

            // Draw floating food cubes
            engine.floatingCubes.forEach { cube ->
                val cx = cube.x - cameraX
                val cy = cube.y - cameraY

                // Ensure it is visible inside the viewport frame to optimize GPU bounds
                if (cx >= -60f && cx <= viewWidth + 60f && cy >= -60f && cy <= viewHeight + 60f) {
                    drawFloatingLootCube(this, cx, cy, cube.value, cube.color)
                }
            }

            // Draw opponent snake chains
            engine.opponents.forEach { opp ->
                if (opp.isAlive && opp.cubeChain.isNotEmpty()) {
                    opp.cubeChain.forEachIndexed { idx, node ->
                        val nx = node.x - cameraX
                        val ny = node.y - cameraY

                        if (nx >= -80f && nx <= viewWidth + 80f && ny >= -80f && ny <= viewHeight + 80f) {
                            val rScale = if (idx == 0) 1.2f else 1.0f
                            drawSnakeCubeNode(
                                drawContext = this,
                                rx = nx,
                                ry = ny,
                                sizeDp = (34f * rScale),
                                value = node.value,
                                themeColor = node.getThemeColor(),
                                isHead = idx == 0,
                                label = if (idx == 0) opp.name else "",
                                angle = if (idx == 0) opp.angle else 0f
                            )
                        }
                    }
                }
            }

            // Draw Local User Snake Chain
            if (user.isAlive && user.cubeChain.isNotEmpty()) {
                user.cubeChain.forEachIndexed { idx, node ->
                    val nx = node.x - cameraX
                    val ny = node.y - cameraY

                    // Render with glowing shadow core
                    val rScale = if (idx == 0) 1.25f else 1.0f
                    drawSnakeCubeNode(
                        drawContext = this,
                        rx = nx,
                        ry = ny,
                        sizeDp = (34f * rScale),
                        value = node.value,
                        themeColor = if (idx == 0) CyberPrimary else node.getThemeColor(),
                        isHead = idx == 0,
                        label = if (idx == 0) t("YOU", "شما") else "",
                        angle = if (idx == 0) user.angle else 0f
                    )
                }
            }
        }

        // VISUAL VIRTUAL JOYSTICK OVERLAY (Only when active drag is occurring)
        if (joystickActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // outer bounding cylinder
                Box(
                    modifier = Modifier
                        .offset(
                            x = (joystickStartX - 50).dp,
                            y = (joystickStartY - 50).dp
                        )
                        .size(100.dp)
                        .border(2.dp, CyberPrimary.copy(alpha = 0.4f), CircleShape)
                        .background(CyberBackground.copy(alpha = 0.2f), CircleShape)
                )

                // sliding thumb controller
                Box(
                    modifier = Modifier
                        .offset(
                            x = (joystickStartX - 20 + steerX * 35f).dp,
                            y = (joystickStartY - 20 + steerY * 35f).dp
                        )
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(colors = listOf(CyberPrimary, Color.Cyan)),
                            shape = CircleShape
                        )
                        .shadow(8.dp, CircleShape)
                )
            }
        }

        // HUD: TOP HEADING COMPRISES: IN-GAME LEAVE, PLAYER TOTAL SUM
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BACK BUTTON TO EXIT MATCH
            IconButton(
                onClick = onLeave,
                modifier = Modifier
                    .background(CyberSurface.copy(alpha = 0.7f), CircleShape)
                    .border(1.dp, CyberPrimary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CyberPrimary)
            }

            // USER TOTAL EXP VALUE CARD
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, CyberPrimary),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = CyberPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${t("Score: ", "امتیاز: ")}${engine.currentScore}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // TOP-RIGHT SUB-HUD: SURVIVOR LOBBY STANDINGS (Updated real-time scoreboard)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 70.dp, end = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .width(160.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(
                        text = t("SURVIVORS", "تابلو رقبا"),
                        fontSize = 10.sp,
                        color = CyberSecondary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Sort players by total score to find the current match leaders
                    val standings = remember(frameTrigger) {
                        val list = mutableListOf<GamePlayer>().apply {
                            if (engine.player.isAlive) add(engine.player)
                            addAll(engine.opponents.filter { it.isAlive })
                        }
                        list.sortByDescending { it.score }
                        list.take(5)
                    }

                    standings.forEachIndexed { rank, p ->
                        val isUser = p.id == "PLAYER_ID"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isUser) CyberPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rank + 1}. ${p.name}",
                                color = if (isUser) CyberPrimary else TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${p.score}",
                                color = if (isUser) CyberPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // TOP-LEFT HUD: HIGH FIDELITY CIRCULAR MINIMAP
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 70.dp, start = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(CyberSurface.copy(alpha = 0.8f), CircleShape)
                    .border(1.dp, CyberPrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mapW = engine.mapWidth
                    val mapH = engine.mapHeight
                    val mapRadius = size.width / 2f

                    // Draw center player
                    val head = engine.player.cubeChain.getOrNull(0)
                    if (head != null) {
                        // Plot other players relative to center
                        engine.opponents.filter { it.isAlive }.forEach { opp ->
                            val oppHead = opp.cubeChain.getOrNull(0)
                            if (oppHead != null) {
                                // Delta normalized coordinates
                                val dx = (oppHead.x - head.x) / mapW * size.width
                                val dy = (oppHead.y - head.y) / mapH * size.height
                                
                                // Clamped into circular mapping space
                                val hyp = sqrt(dx * dx + dy * dy)
                                if (hyp < mapRadius - 4f) {
                                    drawCircle(
                                        color = CyberSecondary,
                                        radius = 2.dp.toPx(),
                                        center = Offset(mapRadius + dx, mapRadius + dy)
                                    )
                                }
                            }
                        }

                        // Plot local player in center blinking cyan
                        drawCircle(
                            color = CyberPrimary,
                            radius = 3.dp.toPx(),
                            center = Offset(mapRadius, mapRadius)
                        )
                    }
                }
            }
        }

        // BOTTOM-LEFT HUD: SCROLLING FEED TICKERS (Kills and merges)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp)
                .width(220.dp)
                .height(130.dp)
        ) {
            val listState = remember(frameTrigger) { engine.killEvents.take(4) }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                listState.reversed().forEach { event ->
                    val phrase = if (event.victimName == "Mega Merge") {
                        t(
                            "${event.killerName} reached Mega Cube: [${event.killerValue}]! ⚡",
                            "${event.killerName} به مکعب غول‌پیکر رسید: [${event.killerValue}]! ⚡"
                        )
                    } else {
                        t(
                            "💥 ${event.killerName} consumed ${event.victimName}",
                            "💥 ${event.killerName} مکعب ${event.victimName} را متلاشی کرد"
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = phrase,
                            color = if (event.victimName == "Mega Merge") CyberPrimary else TextPrimary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // BOTTOM-RIGHT SPEED BOOST ACTIVE PAD
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, end = 24.dp)
        ) {
            var pressed by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(16.dp, CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (pressed) listOf(Color(0xFFFF0055), Color(0xFF7F00FF)) else listOf(
                                CyberSecondary,
                                Color(0xFF7F00FF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        2.dp,
                        if (pressed) Color.White else CyberSecondary.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                engine.player.isBoosting = true
                                tryAwaitRelease()
                                pressed = false
                                engine.player.isBoosting = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = t("BOOST", "شتاب"),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// Low-level high speed canvas helper to draw 3D cube nodes
private fun drawSnakeCubeNode(
    drawContext: androidx.compose.ui.graphics.drawscope.DrawScope,
    rx: Float,
    ry: Float,
    sizeDp: Float,
    value: Int,
    themeColor: Color,
    isHead: Boolean,
    label: String,
    angle: Float
) {
    val pixelSize = sizeDp // direct diameter pixel size represent
    val half = pixelSize / 2f

    // 1. Draw glowing background shadow drop
    drawContext.drawCircle(
        color = themeColor.copy(alpha = 0.28f),
        radius = half + 8f,
        center = Offset(rx, ry)
    )

    // 2. Draw actual cube (rounded rectangle representing projection of 3d mesh)
    drawContext.drawRoundRect(
        color = themeColor,
        topLeft = Offset(rx - half, ry - half),
        size = Size(pixelSize, pixelSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
    )

    // Inner glossy 3D facet overlay block
    drawContext.drawRoundRect(
        color = Color.White.copy(alpha = 0.15f),
        topLeft = Offset(rx - half + 4f, ry - half + 4f),
        size = Size(pixelSize - 8f, half - 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )

    // 3. Draw numerical value string on cube
    val valueStr = value.toString()
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = (half * 0.95f).coerceIn(24f, 44f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    
    // Draw string offset centered
    drawContext.drawContext.canvas.nativeCanvas.drawText(
        valueStr,
        rx,
        ry + (half * 0.35f),
        textPaint
    )

    // 4. Draw eyes or directional features on head cube
    if (isHead) {
        val eyeRadius = 4f
        val pupilRadius = 1.8f
        
        // Coordinates offset rotated by head angle
        val lookX = cos(angle)
        val lookY = sin(angle)
        
        // Left eye
        val leX = rx + (-lookY * half * 0.45f) + (lookX * half * 0.3f)
        val leY = ry + (lookX * half * 0.45f) + (lookY * half * 0.3f)
        
        // Right eye
        val reX = rx + (lookY * half * 0.45f) + (lookX * half * 0.3f)
        val reY = ry + (-lookX * half * 0.45f) + (lookY * half * 0.3f)

        // White circles
        drawContext.drawCircle(Color.White, radius = eyeRadius, center = Offset(leX, leY))
        drawContext.drawCircle(Color.White, radius = eyeRadius, center = Offset(reX, reY))

        // Pupil pointers
        drawContext.drawCircle(Color.Black, radius = pupilRadius, center = Offset(leX + lookX * 1.5f, leY + lookY * 1.5f))
        drawContext.drawCircle(Color.Black, radius = pupilRadius, center = Offset(reX + lookX * 1.5f, reY + lookY * 1.5f))

        // Draw crown or name label above head
        if (label.isNotEmpty()) {
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.YELLOW
                textSize = 28f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.drawContext.canvas.nativeCanvas.drawText(
                label,
                rx,
                ry - half - 10f,
                labelPaint
            )
        }
    }
}

// Small collectable floaters details
private fun drawFloatingLootCube(
    drawContext: androidx.compose.ui.graphics.drawscope.DrawScope,
    x: Float,
    y: Float,
    value: Int,
    color: Color
) {
    val size = 22f
    val half = size / 2f

    // Ambient halo
    drawContext.drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = size,
        center = Offset(x, y)
    )

    // Inner glowing block polygon
    drawContext.drawRoundRect(
        color = color,
        topLeft = Offset(x - half, y - half),
        size = Size(size, size),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )

    // Value indicator dots count
    val dotColor = Color.White.copy(alpha = 0.8f)
    when (value) {
        2 -> {
            drawContext.drawCircle(dotColor, radius = 2f, center = Offset(x, y))
        }
        4 -> {
            drawContext.drawCircle(dotColor, radius = 1.8f, center = Offset(x - 4f, y - 4f))
            drawContext.drawCircle(dotColor, radius = 1.8f, center = Offset(x + 4f, y + 4f))
        }
        else -> {
            // Draw mini text for high level items
            val miniPaint = android.graphics.Paint().apply {
                setColor(android.graphics.Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.drawContext.canvas.nativeCanvas.drawText(
                value.toString(),
                x,
                y + 5f,
                miniPaint
            )
        }
    }
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var scoreList by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    
    // Live REST database sync inside launching effect
    fun loadLeaderboard() {
        scope.launch {
            isLoading = true
            errorMsg = ""
            try {
                val dbMap = LeaderboardClient.api.getLeaderboard()
                if (dbMap != null) {
                    val list = dbMap.values.toList()
                    // Sort descending by highest score
                    scoreList = list.sortedByDescending { it.score }
                } else {
                    scoreList = emptyList()
                }
            } catch (e: Exception) {
                // Network breakdown triggers descriptive warning
                errorMsg = t(
                    "Network error sync database. Please check your internet connection.",
                    "عدم موفقیت در هماهنگ‌سازی جدول آنلاین. لطفا اتصال اینترنت را بررسی کنید."
                )
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLeaderboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // HEADER BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(CyberSurface, CircleShape)
                    .border(1.dp, CyberPrimary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CyberPrimary)
            }

            Text(
                text = t("GLOBAL SHIELD LEADERBOARD", "جدول امتیازات آنلاین"),
                fontSize = 18.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )

            IconButton(
                onClick = { loadLeaderboard() },
                modifier = Modifier
                    .background(CyberSurface, CircleShape)
                    .border(1.dp, CyberSecondary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberSecondary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyberPrimary)
            }
        } else if (errorMsg.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CyberSecondary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMsg,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else if (scoreList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("No scores registered yet. Write the first history!", "هیچ امتیازی ثبت نشده است. اولین قهرمان باشید!"),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // HIGH FIDELITY LEADERBOARD SCROLL LIST
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(scoreList) { index, entry ->
                    val isFirst = index == 0
                    val isSecond = index == 1
                    val isThird = index == 2

                    val borderColor = when {
                        isFirst -> Color(0xFFFFD700) // Gold
                        isSecond -> Color(0xFFC0C0C0) // Silver
                        isThird -> Color(0xFFCD7F32) // Bronze
                        else -> Color.Transparent
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (borderColor != Color.Transparent) 1.5.dp else 1.dp,
                                color = if (borderColor != Color.Transparent) borderColor else CyberSurfaceVariant,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Rank & Avatar Index
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = when {
                                                isFirst -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                                isSecond -> Color(0xFFC0C0C0).copy(alpha = 0.2f)
                                                isThird -> Color(0xFFCD7F32).copy(alpha = 0.2f)
                                                else -> CyberSurfaceVariant
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = when {
                                            isFirst -> Color(0xFFFFD700)
                                            isSecond -> Color(0xFFE0E0E0)
                                            isThird -> Color(0xFFCD7F32)
                                            else -> TextSecondary
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = entry.username,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${t("Zone: ", "سرور: ")}${entry.region}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Right Max cube indicator and Top sum score
                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(CyberPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Max [${entry.maxCube}]",
                                        color = CyberPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.score.toString(),
                                    color = CyberSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameOverScreen(
    score: Int,
    maxCube: Int,
    kills: Int,
    reason: String,
    playerName: String,
    region: String,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var uploadSuccess by remember { mutableStateOf<Boolean?>(null) }
    var uploadError by remember { mutableStateOf("") }

    // Upload to Firebase Rest Endpoint
    fun uploadHighscore() {
        scope.launch {
            isUploading = true
            uploadError = ""
            try {
                val entry = LeaderboardEntry(
                    username = playerName,
                    score = score,
                    maxCube = maxCube,
                    region = region,
                    timestamp = System.currentTimeMillis()
                )
                val response = LeaderboardClient.api.submitScore(entry)
                if (response != null) {
                    uploadSuccess = true
                } else {
                    uploadError = "Failed empty response"
                }
            } catch (e: Exception) {
                uploadError = t("Network error. Cannot submit.", "خطای شبکه. امتیاز ثبت نشد.")
            } finally {
                isUploading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP LOGO DEFEAT HEADER
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = CyberSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = t("DEVASTATED", "متلاشی شدید!"),
                fontSize = 32.sp,
                color = CyberSecondary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = reason,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        // MIDDLE CARD STATS DISCLOSURES
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, BentoOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = t("MATCH STATISTICS SUMMARY", "خلاصه وضعیت نبرد"),
                    fontSize = 12.sp,
                    color = CyberPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(18.dp))

                val statPairs = listOf(
                    Triple(Icons.Default.Star, t("Final Score", "امتیاز کسب شده"), score.toString()),
                    Triple(Icons.Default.Info, t("Max Cube Size", "بزرگترین مکعب"), "[$maxCube]"),
                    Triple(Icons.Default.Warning, t("Opponents Devoured", "حریفان نابود شده"), "$kills $playerName")
                )

                statPairs.forEach { triple ->
                    val icon = triple.first
                    val name = triple.second
                    val valStr = triple.third
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = TextSecondary, fontSize = 13.sp)
                        }
                        Text(text = valStr, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CyberSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                // Score upload console button
                if (uploadSuccess == true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(Color(0xFFE2F3E3), RoundedCornerShape(100.dp))
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E6F40))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t("SCORE COMMITTED ONLINE!", "امتیاز شما در جدول جهانی ثبت شد!"),
                            color = Color(0xFF2E6F40),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { uploadHighscore() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                            enabled = !isUploading,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = t("SUBMIT SCORE ONLINE", "ثبت آنلاین امتیاز"),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
// Line endings matched beautifully
                                )
                            }
                        }
                    }

                    if (uploadError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uploadError,
                            color = CyberSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // BOTTOM MAIN MENU REDIRECT ACTIONS
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReplay,
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("START NEW COMBAT", "شروع مبارزه جدید"),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = BentoPastelPurple),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = t("RETURN TO LOBBY", "بازگشت به لابی"),
                    color = CyberSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
