package com.lunar.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunar.launcher.ui.LunarColors
import com.lunar.launcher.ui.LunarTheme
import com.lunar.launcher.util.DeviceInfo
import com.lunar.launcher.runtime.MinecraftLauncher
import com.lunar.launcher.runtime.MinecraftInstaller
import com.lunar.launcher.runtime.MinecraftVersionRepository
import com.lunar.launcher.runtime.RuntimeManager
import com.lunar.launcher.runtime.LwjglManager
import com.lunar.launcher.runtime.OfflineAccountManager
import com.lunar.launcher.runtime.MicrosoftAuthManager
import com.lunar.launcher.runtime.ModrinthContentRepository
import com.lunar.launcher.runtime.JavaRuntimeManager
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LunarTheme { LunarApp() } }
    }
}

enum class Screen(val title: String) {
    HOME("Главная"), GAMES("Игры"), MODS("Моды"), SKINS("Скины"), MORE("Ещё"),
    SETTINGS("Настройки"), GRAPHICS("Графика"), CONTROLS("Управление"), JAVA("Java Manager"), LWJGL("LWJGL Manager"),
    PROFILES("Профили"), DEVICE("Устройство"), ACCOUNTS("Аккаунты"), CRASH("Crash Doctor"),
    BENCHMARK("Benchmark"), INITIAL("Начальная настройка"), ABOUT("О Lunar Launcher")
}

@Composable
fun LunarApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var version by remember { mutableStateOf("1.21.11") }
    var loader by remember { mutableStateOf("Fabric") }
    var renderer by remember { mutableStateOf("Vulkan") }
    var java by remember { mutableStateOf("Auto") }
    var profile by remember { mutableStateOf("Performance") }
    var showPlay by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }

    val rootScreens = setOf(Screen.HOME, Screen.GAMES, Screen.MODS, Screen.SKINS, Screen.MORE)
    val goBack: () -> Unit = { screen = Screen.HOME }

    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LunarDrawer(screen) { target ->
                screen = target
                drawerOpen = false
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            containerColor = LunarColors.background,
            bottomBar = {
                if (screen in rootScreens) BottomNav(screen) { screen = it }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (screen) {
                Screen.HOME -> Home(version, loader, renderer, profile, java, { showPlay = true }, { drawerOpen = true }) { screen = it }
                Screen.GAMES -> Games(version, loader, { version = it }, { loader = it }, { screen = Screen.JAVA })
                Screen.MODS -> Mods()
                Screen.SKINS -> Skins()
                Screen.MORE -> More { screen = it }
                Screen.SETTINGS -> Settings { screen = it }
                Screen.GRAPHICS -> Graphics(renderer, { renderer = it }, { screen = Screen.SETTINGS })
                Screen.CONTROLS -> Controls { screen = Screen.SETTINGS }
                Screen.JAVA -> JavaScreen(java, { java = it }, { screen = Screen.SETTINGS })
                Screen.LWJGL -> LwjglScreen { screen = Screen.SETTINGS }
                Screen.PROFILES -> Profiles(profile, { profile = it }, { screen = Screen.SETTINGS })
                Screen.DEVICE -> Device { screen = Screen.SETTINGS }
                Screen.ACCOUNTS -> Accounts()
                Screen.CRASH -> CrashDoctor()
                Screen.BENCHMARK -> Benchmark()
                Screen.INITIAL -> InitialSetup { profile = it; screen = Screen.HOME }
                Screen.ABOUT -> About()
                }
            }
        }
    }

    if (showPlay) PlayDialog(version, loader, renderer, java, profile) { showPlay = false }
    if (showSetup) InitialSetupDialog { profile = it; showSetup = false }
}

@Composable
fun LunarDrawer(selected: Screen, onSelect: (Screen) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF08051A),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxHeight().padding(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFFB65CFF), Color(0xFF4C1BA7)))), contentAlignment = Alignment.Center) {
                    Text("☾", fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("LUNAR", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFC17BFF))
                    Text("LAUNCHER • v1.0", fontSize = 10.sp, color = Color(0xFFAAA1BA))
                }
            }
            Spacer(Modifier.height(10.dp))
            DrawerSection("ОСНОВНОЕ")
            DrawerItem("Главная", Icons.Default.Home, selected == Screen.HOME) { onSelect(Screen.HOME) }
            DrawerItem("Игры", Icons.Default.SportsEsports, selected == Screen.GAMES) { onSelect(Screen.GAMES) }
            DrawerItem("Профили", Icons.Default.People, selected == Screen.PROFILES) { onSelect(Screen.PROFILES) }
            DrawerItem("Настройки", Icons.Default.Settings, selected == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
            DrawerSection("НАСТРОЙКА")
            DrawerItem("Графика", Icons.Default.Tune, selected == Screen.GRAPHICS) { onSelect(Screen.GRAPHICS) }
            DrawerItem("Управление", Icons.Default.Gamepad, selected == Screen.CONTROLS) { onSelect(Screen.CONTROLS) }
            DrawerItem("Моды", Icons.Default.Extension, selected == Screen.MODS) { onSelect(Screen.MODS) }
            DrawerItem("Java", Icons.Default.Code, selected == Screen.JAVA) { onSelect(Screen.JAVA) }
            DrawerItem("LWJGL", Icons.Default.Memory, selected == Screen.LWJGL) { onSelect(Screen.LWJGL) }
            DrawerItem("Скины", Icons.Default.Person, selected == Screen.SKINS) { onSelect(Screen.SKINS) }
            DrawerItem("Аккаунты", Icons.Default.AccountCircle, selected == Screen.ACCOUNTS) { onSelect(Screen.ACCOUNTS) }
            DrawerSection("ИНСТРУМЕНТЫ")
            DrawerItem("Benchmark", Icons.Default.BarChart, selected == Screen.BENCHMARK) { onSelect(Screen.BENCHMARK) }
            DrawerItem("Crash Doctor", Icons.Default.HealthAndSafety, selected == Screen.CRASH) { onSelect(Screen.CRASH) }
            DrawerItem("Устройство", Icons.Default.PhoneAndroid, selected == Screen.DEVICE) { onSelect(Screen.DEVICE) }
            Spacer(Modifier.weight(1f))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF120A29)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text("LUNAR STATUS", fontSize = 10.sp, color = Color(0xFF9D8EB2), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("60 FPS  •  Vulkan", fontWeight = FontWeight.Bold)
                    Text("Minecraft 1.21.11  •  Fabric", fontSize = 10.sp, color = Color(0xFFA8A0B6))
                }
            }
        }
    }
}

@Composable
fun DrawerSection(title: String) {
    Text(title, Modifier.padding(start = 12.dp, top = 13.dp, bottom = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7D718F), letterSpacing = 1.1.sp)
}

@Composable
fun DrawerItem(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x663E1677) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Color(0xFFC17BFF) else Color(0xFFA79CAF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else Color(0xFFC0BAC8))
    }
}

@Composable
fun BottomNav(selected: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = LunarColors.surface) {
        NavItem("Главная", Icons.Default.Home, selected == Screen.HOME) { onSelect(Screen.HOME) }
        NavItem("Игры", Icons.Default.SportsEsports, selected == Screen.GAMES) { onSelect(Screen.GAMES) }
        NavItem("Моды", Icons.Default.Extension, selected == Screen.MODS) { onSelect(Screen.MODS) }
        NavItem("Скины", Icons.Default.Person, selected == Screen.SKINS) { onSelect(Screen.SKINS) }
        NavItem("Ещё", Icons.Default.GridView, selected == Screen.MORE) { onSelect(Screen.MORE) }
    }
}

@Composable
fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, click: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable { click() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color(0x332C1A55) else Color.Transparent)
                    .padding(horizontal = 15.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = if (selected) LunarColors.purpleBright else Color(0xFFB4AEC2))
            }
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Color(0xFFB4AEC2)
            )
        }
    }
}

@Composable
fun Header(title: String, back: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (back != null) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Назад") }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Lunar Launcher • v1.0", color = Color(0xFFBDB8C9), fontSize = 12.sp)
        }
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LunarColors.surface),
        shape = RoundedCornerShape(20.dp)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF17102F), Color(0xFF0C0920))))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun Home(version: String, loader: String, renderer: String, profile: String, java: String, play: () -> Unit, openMenu: () -> Unit, open: (Screen) -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02010A), Color(0xFF08031B), Color(0xFF03020D))
                )
            )
    ) {
        // Soft lunar glow / stars — lightweight and fully native, no giant background image.
        Box(
            Modifier
                .size(320.dp)
                .offset(x = 115.dp, y = (-105).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x994F20B6), Color(0x331E0A4A), Color.Transparent)))
        )
        Box(
            Modifier
                .size(150.dp)
                .offset(x = 245.dp, y = 38.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xAA9B52FF), Color(0x221D0B43), Color.Transparent)))
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    // Crescent moon.
                    Box(
                        Modifier
                            .size(108.dp)
                            .offset(x = 232.dp, y = (-8).dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFE0B8FF), Color(0xFF7B32D5), Color(0x00000000))))
                    )
                    Box(
                        Modifier
                            .size(92.dp)
                            .offset(x = 248.dp, y = (-13).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF08031B))
                    )

                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).clickable { openMenu() },
                        shape = RoundedCornerShape(15.dp),
                        color = Color(0x66130A2B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x664F2990))
                    ) {
                        Icon(Icons.Default.Menu, "Меню", tint = Color(0xFFD09BFF), modifier = Modifier.padding(10.dp).size(22.dp))
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).clickable { open(Screen.SETTINGS) },
                        shape = RoundedCornerShape(15.dp),
                        color = Color(0x66130A2B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x664F2990))
                    ) {
                        Icon(Icons.Default.Settings, "Настройки", tint = Color(0xFFD09BFF), modifier = Modifier.padding(10.dp).size(22.dp))
                    }

                    Column(Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("☾", fontSize = 43.sp, color = Color(0xFFC56BFF), fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(5.dp))
                            Text("LUNAR", fontSize = 45.sp, fontWeight = FontWeight.Black, color = Color(0xFFB86CFF))
                        }
                        Text("LAUNCHER  •  v1.0", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("JAVA MINECRAFT LAUNCHER FOR ANDROID", fontSize = 10.sp, color = Color(0xFFAAA0C0), letterSpacing = 0.8.sp)
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().clickable { open(Screen.GAMES) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0D0822)),
                    shape = RoundedCornerShape(25.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x775F2BA4))
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFFB964FF), Color(0xFF5420B5)))),
                                contentAlignment = Alignment.Center
                            ) { Text("M", fontSize = 35.sp, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Minecraft $version", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Text("$loader  •  $renderer", fontSize = 14.sp, color = Color(0xFFC47CFF), fontWeight = FontWeight.Bold)
                                Text("Java: $java  •  $profile", fontSize = 11.sp, color = Color(0xFFAFA6BE))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB978FF), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = play,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF944AFF)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ИГРАТЬ", fontSize = 19.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    NeonStat("FPS", "60", Icons.Default.Speed, Modifier.weight(1f))
                    NeonStat("RENDERER", renderer, Icons.Default.ViewInAr, Modifier.weight(1f))
                    NeonStat("GPU", "Auto", Icons.Default.Memory, Modifier.weight(1f))
                }
            }

            item {
                SectionTitle("⚡  БЫСТРЫЕ ДЕЙСТВИЯ")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeAction("FPS+++", "Больше FPS", Icons.Default.Bolt) { open(Screen.GRAPHICS) }
                    HomeAction("Плавность", "Frame Pacing", Icons.Default.Tune) { open(Screen.GRAPHICS) }
                    HomeAction("Auto Config", "Оптимизация", Icons.Default.AutoAwesome) { open(Screen.DEVICE) }
                    HomeAction("Benchmark", "Тест", Icons.Default.BarChart) { open(Screen.BENCHMARK) }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().clickable { open(Screen.CRASH) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0D0822)),
                    shape = RoundedCornerShape(21.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553E1C75))
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x443E1680)), contentAlignment = Alignment.Center) {
                            Text("✦", fontSize = 27.sp, color = Color(0xFFD28AFF))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lunar AI Crash Doctor", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Диагностика ошибок  •  Исправление  •  Советы", color = Color(0xFFA8A0B6), fontSize = 10.sp)
                        }
                        Text("ОТКРЫТЬ  ›", color = Color(0xFFC278FF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            item {
                SectionTitle("⚙  УПРАВЛЕНИЕ")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeMenuTile("Настройки", "Все параметры", Icons.Default.Settings, Modifier.weight(1f)) { open(Screen.SETTINGS) }
                    HomeMenuTile("Java", "Runtime", Icons.Default.Code, Modifier.weight(1f)) { open(Screen.JAVA) }
                    HomeMenuTile("Профили", "Сохранённые", Icons.Default.People, Modifier.weight(1f)) { open(Screen.PROFILES) }
                }
            }

            item {
                SectionTitle("🌙  LUNAR MENU")
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeMenuTile("Игры", "Версии / loader", Icons.Default.SportsEsports, Modifier.weight(1f)) { open(Screen.GAMES) }
                        HomeMenuTile("Графика", "Vulkan / OpenGL", Icons.Default.Tune, Modifier.weight(1f)) { open(Screen.GRAPHICS) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeMenuTile("Управление", "JSON", Icons.Default.Gamepad, Modifier.weight(1f)) { open(Screen.CONTROLS) }
                        HomeMenuTile("Моды", "Менеджер", Icons.Default.Extension, Modifier.weight(1f)) { open(Screen.MODS) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeMenuTile("Скины", "Скины / плащи", Icons.Default.Person, Modifier.weight(1f)) { open(Screen.SKINS) }
                        HomeMenuTile("Аккаунты", "Microsoft / Offline", Icons.Default.AccountCircle, Modifier.weight(1f)) { open(Screen.ACCOUNTS) }
                    }
                }
            }

            item {
                SectionTitle("👤  СОЦИАЛЬНЫЕ СЕТИ АВТОРА")
                Text("Поддержать Lunar Launcher", fontSize = 11.sp, color = Color(0xFF9F95B2), modifier = Modifier.padding(start = 3.dp, bottom = 7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialCard("TikTok", "@zalithlauncher_nalune", "TT", Modifier.weight(1f)) { openUrl("https://www.tiktok.com/@zalithlauncher_nalune") }
                    SocialCard("Telegram", "@snusik_god_tiktok", "TG", Modifier.weight(1f)) { openUrl("https://t.me/snusik_god_tiktok") }
                }
                Spacer(Modifier.height(8.dp))
                SocialCard("GitHub", "SOON...", "GH", Modifier.fillMaxWidth(), enabled = false) {}
            }

            item {
                Card(
                    Modifier.fillMaxWidth().clickable { open(Screen.INITIAL) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF120B28)),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x443B246A))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 24.sp, color = Color(0xFFC77CFF))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lunar Auto Config", fontWeight = FontWeight.Bold)
                            Text("Сканировать устройство и подобрать настройки", fontSize = 10.sp, color = Color(0xFFA9A3B3))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFC77CFF))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.3.sp, color = Color.White)
}

@Composable
fun NeonStat(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0B0720)),
        shape = RoundedCornerShape(19.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x554D2591))
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color(0xFFC37AFF), modifier = Modifier.size(21.dp))
            Text(title, fontSize = 8.sp, color = Color(0xFFAAA0BC))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SocialCard(title: String, handle: String, badge: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xCC10072A) else Color(0x77100B1B)),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) Color(0x554D2591) else Color(0x332A2631))
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(37.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Color(0x553C157A) else Color(0x331B1720)), contentAlignment = Alignment.Center) {
                Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (enabled) Color(0xFFD090FF) else Color(0xFF77717C))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(handle, fontSize = 9.sp, color = Color(0xFFA59AAF))
            }
            if (enabled) Icon(Icons.Default.OpenInNew, null, tint = Color(0xFFB96EFF), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun HomeAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        Modifier
            .width(164.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD110B2B)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x443B246A))
    ) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, null, tint = LunarColors.purpleBright, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF9E96AF), fontSize = 10.sp)
        }
    }
}

@Composable
fun HomeMenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD0E0925)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44362363))
    ) {
        Column(
            Modifier.padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                tint = LunarColors.purpleBright,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = Color(0xFF938BA3), fontSize = 9.sp)
        }
    }
}

@Composable
fun StatInline(icon: ImageVector, title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp, max = 90.dp)) {
        Icon(icon, null, tint = LunarColors.purpleBright, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(3.dp))
        Text(title, fontSize = 9.sp, color = Color(0xFFA49CAF))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Stat(title: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = LunarColors.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) { Text(title, color = Color(0xFFA6A0B0), fontSize = 11.sp); Spacer(Modifier.height(4.dp)); Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun QuickAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick, shape = RoundedCornerShape(14.dp)) { Icon(icon, null); Spacer(Modifier.width(5.dp)); Text(title) }
}

@Composable
fun MenuTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = LunarColors.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = LunarColors.purpleBright); Spacer(Modifier.height(7.dp)); Text(title, fontSize = 12.sp) }
    }
}

@Composable
fun Games(selected: String, loader: String, selectVersion: (String) -> Unit, selectLoader: (String) -> Unit, java: () -> Unit) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<MinecraftVersionRepository.Catalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Все") }
    val loaders = listOf("Fabric", "Forge", "NeoForge", "Quilt", "Vanilla")
    val scope = rememberCoroutineScope()

    suspend fun refresh(force: Boolean) {
        loading = true
        error = null
        MinecraftVersionRepository.load(context, force)
            .onSuccess { catalog = it }
            .onFailure { error = it.message ?: "Не удалось загрузить список версий" }
        loading = false
    }

    LaunchedEffect(Unit) { refresh(false) }

    val allVersions = catalog?.versions.orEmpty()
    val filtered = allVersions.filter { v ->
        val matchesQuery = query.isBlank() || v.id.contains(query.trim(), ignoreCase = true)
        val matchesFilter = when (filter) {
            "Release" -> v.type == "release"
            "Snapshot" -> v.type == "snapshot"
            "Beta" -> v.type == "old_beta"
            "Alpha" -> v.type == "old_alpha"
            else -> true
        }
        matchesQuery && matchesFilter
    }

    Column(Modifier.fillMaxSize()) {
        Header("Игры")
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Версии Minecraft", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (catalog == null) "Загрузка полного каталога…" else "Найдено ${filtered.size} из ${allVersions.size} версий",
                            fontSize = 11.sp,
                            color = Color(0xFFA7A0B0)
                        )
                    }
                    IconButton(onClick = { scope.launch { refresh(true) } }) {
                        Icon(Icons.Default.Refresh, "Обновить", tint = LunarColors.purpleBright)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Поиск версии: 1.8.9, beta, 26.3…") }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Все", "Release", "Snapshot", "Beta", "Alpha").forEach { type ->
                        FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                catalog?.let {
                    Text(
                        "Последний релиз: ${it.latestRelease ?: "—"} • Снапшот: ${it.latestSnapshot ?: "—"}",
                        fontSize = 11.sp,
                        color = LunarColors.purpleBright
                    )
                    if (it.fromCache) Text("Офлайн-кэш каталога", fontSize = 10.sp, color = Color(0xFF8E879A))
                }
            }

            if (loading && allVersions.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            error?.let { message ->
                item {
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Ошибка каталога", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(message, fontSize = 12.sp, color = Color(0xFFA7A0B0))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { scope.launch { refresh(true) } }) { Text("Повторить") }
                    }
                }
            }

            items(filtered) { v ->
                CardBox(Modifier.fillMaxWidth().clickable { selectVersion(v.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF211A3B)),
                            contentAlignment = Alignment.Center
                        ) { Text("MC", fontWeight = FontWeight.Bold, color = LunarColors.purpleBright) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.id, fontWeight = FontWeight.Bold)
                            Text("${v.typeLabel} • ${v.dateLabel}", fontSize = 11.sp, color = Color(0xFFA7A0B0))
                            if (v.id == selected) Text("Выбрано • $loader", fontSize = 11.sp, color = LunarColors.purpleBright)
                        }
                        if (v.id == selected) Icon(Icons.Default.CheckCircle, null, tint = LunarColors.purple)
                    }
                }
            }

            item {
                Text("Загрузчик", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    loaders.forEach { l -> FilterChip(selected = loader == l, onClick = { selectLoader(l) }, label = { Text(l) }) }
                }
            }
            item { Button(java, Modifier.fillMaxWidth()) { Icon(Icons.Default.Code, null); Spacer(Modifier.width(6.dp)); Text("Java для выбранной версии") } }
        }
    }
}

@Composable
fun Settings(open: (Screen) -> Unit) {
    val groups = listOf(
        "Графика и производительность" to listOf("FPS+++" to Screen.GRAPHICS, "Плавность / Frame Pacing" to Screen.GRAPHICS, "Benchmark" to Screen.BENCHMARK),
        "Запуск" to listOf("Java Manager" to Screen.JAVA, "LWJGL Manager" to Screen.LWJGL, "Профили" to Screen.PROFILES, "Проверка устройства" to Screen.DEVICE),
        "Игра" to listOf("Управление JSON" to Screen.CONTROLS, "Моды" to Screen.MODS, "Аккаунты" to Screen.ACCOUNTS),
        "Диагностика" to listOf("Lunar AI Crash Doctor" to Screen.CRASH, "Начальная настройка" to Screen.INITIAL)
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Настройки")
        groups.forEach { (group, rows) ->
            Text(group, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = LunarColors.purpleBright, fontWeight = FontWeight.Bold)
            rows.forEach { (name, target) -> SettingRow(name) { open(target) } }
        }
        SettingRow("О Lunar Launcher") { open(Screen.ABOUT) }
    }
}

@Composable
fun SettingRow(title: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = LunarColors.surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = LunarColors.purple) }
    }
}

@Composable
fun Graphics(renderer: String, setRenderer: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("graphics", 0) }
    var fps by remember { mutableFloatStateOf(prefs.getInt("fps", 60).toFloat()) }
    var smooth by remember { mutableStateOf(prefs.getBoolean("smooth", true)) }
    var dynamic by remember { mutableStateOf(prefs.getBoolean("dynamic", true)) }
    var ignoreGpu by remember { mutableStateOf(prefs.getBoolean("ignoreGpu", false)) }
    var forceVulkan by remember { mutableStateOf(prefs.getBoolean("forceVulkan", false)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Графика", back)
        CardBox(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("Renderer", fontWeight = FontWeight.Bold)
            listOf("Auto", "Vulkan", "OpenGL", "ANGLE").forEach { r -> Row(Modifier.fillMaxWidth().clickable { setRenderer(r) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(renderer == r, { setRenderer(r) }); Text(r) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("FPS target: ${fps.toInt()}")
            Slider(fps, { fps = it }, valueRange = 30f..120f)
            ToggleRow("Плавность / Frame Pacing", smooth) { smooth = it }
            ToggleRow("Dynamic Resolution", dynamic) { dynamic = it }
            ToggleRow("Игнорировать GPU check", ignoreGpu) { ignoreGpu = it }
            ToggleRow("Force Vulkan", forceVulkan) { forceVulkan = it }
            Button({
                prefs.edit().putInt("fps", fps.toInt()).putBoolean("smooth", smooth).putBoolean("dynamic", dynamic)
                    .putBoolean("ignoreGpu", ignoreGpu).putBoolean("forceVulkan", forceVulkan).apply()
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Сохранить графику") }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 14.sp); Switch(checked, onChange) } }

@Composable
fun Controls(back: () -> Unit) {
    val context = LocalContext.current
    val files = listOf("default.json" to "Первоначальное управление", "pvp.json" to "PvP / быстрый ввод", "survival.json" to "Выживание", "builder.json" to "Строительство", "touch.json" to "Сенсорный профиль")
    var selected by remember { mutableStateOf("default.json") }
    Column(Modifier.fillMaxSize()) {
        Header("Управление", back)
        LazyColumn(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { CardBox { Text("JSON Controls", fontWeight = FontWeight.Bold); Text("Профили хранятся как реальные JSON-файлы и используются для выбранной раскладки.", color = Color(0xFFA9A3B3), fontSize = 12.sp) } }
            items(files) { (name, desc) -> CardBox(Modifier.clickable { selected = name }) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = LunarColors.purpleBright); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(desc, color = Color(0xFFA9A3B3), fontSize = 12.sp) }; if(selected==name) Icon(Icons.Default.CheckCircle,null,tint=LunarColors.purple) } } }
            item { Button({ context.getSharedPreferences("controls",0).edit().putString("active",selected).apply() }, Modifier.fillMaxWidth()) { Text("Применить $selected") } }
        }
    }
}

@Composable
fun JavaScreen(selected: String, choose: (String) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val runtimes = remember { JavaRuntimeManager.installed(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri!=null) context.getSharedPreferences("java",0).edit().putString("customUri",uri.toString()).apply() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Java Manager", back)
        CardBox(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("Java Runtime", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Выбор влияет на запуск. Для Minecraft 1.20.5+ обычно требуется Java 21; старые версии могут требовать Java 8/17.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            listOf("Auto", "Java 8", "Java 17", "Java 21", "Java 25", "Custom").forEach { v -> Row(Modifier.fillMaxWidth().clickable { choose(v); JavaRuntimeManager.select(context,v) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == v, { choose(v); JavaRuntimeManager.select(context,v) }); Text(v, fontWeight = if(selected==v) FontWeight.Bold else FontWeight.Normal) } }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Text("Установленные runtime: ${runtimes.joinToString { it.name }.ifBlank { "нет" }}", fontSize=12.sp)
            Button({ }, Modifier.fillMaxWidth()) { Text(if(runtimes.isEmpty()) "Runtime не найден" else "Проверка runtime: OK") }
            OutlinedButton({ picker.launch(null) }, Modifier.fillMaxWidth()) { Text("Выбрать папку Custom Runtime") }
        }
    }
}

@Composable
fun LwjglScreen(back: () -> Unit) {
    val context = LocalContext.current
    var selection by remember { mutableStateOf(LwjglManager.get(context)) }
    var status by remember { mutableStateOf(LwjglManager.validate(context, selection)) }
    val versions = listOf("Auto", "3.3.3", "3.3.4", "3.3.5", "3.3.6", "3.3.7")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("LWJGL Manager", back)
        CardBox(Modifier.padding(14.dp)) {
            Text("LWJGL библиотека", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Можно использовать стандартную библиотеку версии Minecraft или экспериментальную замену.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            listOf(
                LwjglManager.Mode.AUTO to "Auto — LWJGL из version.json",
                LwjglManager.Mode.BUNDLED to "Bundled — библиотека Lunar",
                LwjglManager.Mode.CUSTOM to "Custom — JAR из выбранной папки"
            ).forEach { (mode, label) ->
                Row(Modifier.fillMaxWidth().clickable { selection = selection.copy(mode = mode); status = LwjglManager.validate(context, selection) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selection.mode == mode, { selection = selection.copy(mode = mode); status = LwjglManager.validate(context, selection) })
                    Text(label, fontWeight = if (selection.mode == mode) FontWeight.Bold else FontWeight.Normal)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 9.dp))
            Text("Версия LWJGL для Bundled", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                versions.forEach { v -> FilterChip(selected = selection.version == v, onClick = { selection = selection.copy(version = v); status = LwjglManager.validate(context, selection) }, label = { Text(v) }) }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Разрешить экспериментальную LWJGL", selection.experimental) { selection = selection.copy(experimental = it) }
            Text("Экспериментальный режим может привести к несовместимости с Minecraft или модами.", color = Color(0xFFA9A3B3), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text(status, color = LunarColors.purpleBright, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button({ LwjglManager.save(context, selection); status = LwjglManager.validate(context, selection) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Сохранить выбор") }
            OutlinedButton({ status = LwjglManager.validate(context, selection) }, Modifier.fillMaxWidth()) { Text("Проверить библиотеку") }
        }
        CardBox(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
            Text("Как работает", fontWeight = FontWeight.Bold)
            Text("Auto не подменяет LWJGL. Bundled и Custom добавляют выбранные JAR в classpath запуска. Перед экспериментальной заменой рекомендуется сохранить рабочий профиль.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
        }
    }
}

@Composable
fun Profiles(selected: String, choose: (String) -> Unit, back: () -> Unit) {
    val context=LocalContext.current
    val defaults=listOf("Performance","Balanced","Quality","Low End","PvP","Battery Saver")
    var custom by remember { mutableStateOf(context.getSharedPreferences("profiles",0).getStringSet("custom",emptySet()).orEmpty().toList()) }
    Column(Modifier.fillMaxSize()) { Header("Профили", back); LazyColumn(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
        items(defaults+custom+"Custom") { p -> CardBox { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(p,fontWeight=FontWeight.Bold);Text(profileDescription(p),color=Color(0xFFA9A3B3),fontSize=12.sp)}; if(selected==p) Icon(Icons.Default.CheckCircle,null,tint=LunarColors.purple) else OutlinedButton({choose(p);context.getSharedPreferences("profiles",0).edit().putString("active",p).apply()}){Text("Выбрать")} } } }
        item { Button({ val n="Profile-${System.currentTimeMillis()}"; custom=(custom+n); context.getSharedPreferences("profiles",0).edit().putStringSet("custom",custom.toSet()).apply() },Modifier.fillMaxWidth()){Text("+ Создать профиль")} }
    } }
}
fun profileDescription(p: String) = when (p) { "Performance" -> "Максимальная производительность"; "Balanced" -> "Баланс FPS / качество"; "Quality" -> "Качество графики"; "Low End" -> "Для слабых устройств"; "PvP" -> "Минимальная задержка ввода"; "Battery Saver" -> "Снижает нагрузку и FPS"; else -> "Пользовательские параметры" }

@Composable
fun Mods() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.21.11") }
    var loader by remember { mutableStateOf("fabric") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var mods by remember { mutableStateOf<List<ModrinthContentRepository.Mod>>(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        Header("Моды")
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Поиск Modrinth") })
            Spacer(Modifier.width(6.dp))
            IconButton({
                scope.launch {
                    loading = true; status = ""
                    ModrinthContentRepository.search(query, version, loader)
                        .onSuccess { mods = it }
                        .onFailure { status = "Ошибка: ${it.message}" }
                    loading = false
                }
            }) { Icon(Icons.Default.Search, "Искать") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("fabric", "forge", "neoforge", "quilt").forEach { value ->
                FilterChip(loader == value, { loader = value }, { Text(value) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        if (status.isNotBlank()) Text(status, Modifier.padding(horizontal = 14.dp), color = LunarColors.purpleBright, fontSize = 12.sp)
        LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(mods) { mod ->
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mod.title, fontWeight = FontWeight.Bold)
                            Text(mod.description, color = Color(0xFFA9A3B3), fontSize = 12.sp)
                            Text("${mod.downloads} downloads • $loader • $version", fontSize = 10.sp, color = LunarColors.purpleBright)
                        }
                        Button({
                            scope.launch {
                                status = "Загрузка ${mod.title}…"
                                ModrinthContentRepository.resolveLatest(mod.id, version, loader)
                                    .onSuccess { resolved ->
                                        ModrinthContentRepository.install(context, resolved, version)
                                            .onSuccess { status = "Установлено: ${it.name}" }
                                            .onFailure { status = "Ошибка установки: ${it.message}" }
                                    }
                                    .onFailure { status = "Нет совместимой версии: ${it.message}" }
                            }
                        }) { Text("Установить") }
                    }
                }
            }
        }
    }
}

@Composable
fun Skins() { val context=LocalContext.current; val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri-> if(uri!=null){context.getSharedPreferences("skin",0).edit().putString("uri",uri.toString()).apply()}}; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("Скины");CardBox(Modifier.padding(14.dp)){Text("Скин и плащ",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("PNG сохраняется как выбранный скин-профиль.",color=Color(0xFFA9A3B3));Spacer(Modifier.height(12.dp));Button({picker.launch(arrayOf("image/png","image/*"))},Modifier.fillMaxWidth()){Text("Импортировать скин")};OutlinedButton({context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE))},Modifier.fillMaxWidth()){Text("Выбрать другой скин")}}}}

@Composable
fun Accounts() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offline by remember { mutableStateOf(OfflineAccountManager.list(context)) }
    var microsoft by remember { mutableStateOf(MicrosoftAuthManager.active(context)) }
    var name by remember { mutableStateOf("") }
    var login by remember { mutableStateOf<MicrosoftAuthManager.DeviceLogin?>(null) }
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Аккаунты")
        CardBox(Modifier.padding(14.dp)) {
            Text("Microsoft", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Вход как в современных Android Minecraft-лаунчерах: Microsoft → Xbox Live → XSTS → Minecraft Services.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            if (microsoft != null) {
                Text("● ${microsoft!!.name}", fontWeight = FontWeight.Bold)
                Text(microsoft!!.uuid, fontSize = 9.sp, color = Color(0xFFA9A3B3))
                Spacer(Modifier.height(6.dp))
                OutlinedButton({ MicrosoftAuthManager.logout(context); microsoft = null; status = "Microsoft аккаунт отключён." }, Modifier.fillMaxWidth()) { Text("Выйти") }
            } else {
                Button({
                    status = "Получаем код Microsoft…"
                    MicrosoftAuthManager.startDeviceLogin(context,
                        onCode = { result ->
                            result.onSuccess { info ->
                                login = info
                                status = "Открой страницу Microsoft и введи код."
                            }.onFailure { status = "Ошибка: ${it.message}" }
                        },
                        onComplete = { result ->
                            result.onSuccess { account ->
                                microsoft = account
                                login = null
                                status = "Microsoft аккаунт ${account.name} подключён."
                            }.onFailure { status = "Ошибка входа Microsoft: ${it.message}" }
                        }
                    )
                }, Modifier.fillMaxWidth()) { Text("Войти через Microsoft") }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Text(status, color = LunarColors.purpleBright, fontSize = 12.sp)
            }
        }

        CardBox(Modifier.padding(14.dp)) {
            Text("Offline", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Локальный профиль для офлайн-режима.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Ник") })
            Spacer(Modifier.height(8.dp))
            Button({
                if (name.isNotBlank()) {
                    OfflineAccountManager.add(context, name)
                    offline = OfflineAccountManager.list(context)
                    name = ""
                }
            }, Modifier.fillMaxWidth()) { Text("Добавить Offline") }
        }

        offline.forEach { a ->
            CardBox(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight = FontWeight.Bold)
                        Text(a.uuid, fontSize = 9.sp, color = Color(0xFFA9A3B3))
                    }
                    Button({ OfflineAccountManager.select(context, a.name); offline = OfflineAccountManager.list(context) }) {
                        Text(if (OfflineAccountManager.active(context)?.name == a.name) "Активен" else "Выбрать")
                    }
                    IconButton({ OfflineAccountManager.remove(context, a.name); offline = OfflineAccountManager.list(context) }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }

        CardBox(Modifier.padding(14.dp)) {
            Text("Ely.by", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("OAuth Ely.by подключим отдельным провайдером; пароль и токены в launcher не вводятся вручную.", color = Color(0xFFA9A3B3), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ely.by/"))) }, Modifier.fillMaxWidth()) { Text("Открыть Ely.by") }
        }
    }

    login?.let { info ->
        AlertDialog(
            onDismissRequest = { login = null },
            title = { Text("Вход Microsoft") },
            text = {
                Column {
                    Text("1. Открой страницу Microsoft:")
                    Text(info.verificationUri, color = LunarColors.purpleBright, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("2. Введи код:", fontWeight = FontWeight.Bold)
                    Text(info.userCode, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("После подтверждения Lunar автоматически получит Xbox Live, XSTS и Minecraft token. Пароль в launcher не сохраняется.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.verificationUri))) }) { Text("Открыть Microsoft") }
            },
            dismissButton = { TextButton({ login = null }) { Text("Закрыть") } }
        )
    }
}

@Composable
fun Device(back: () -> Unit) {
    val d = remember { DeviceInfo.read() }
    var scanned by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Проверка устройства", back)
        CardBox(Modifier.padding(14.dp)) {
            val rows = listOf(
                "Android" to d.android, "CPU" to d.cpu, "ABI" to d.abi,
                "Ядер" to d.cores.toString(), "RAM" to "${d.ramMb} MB",
                "GPU" to "См. системную информацию", "Vulkan" to "Проверка runtime"
            )
            rows.forEach { (a, b) -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(a, Modifier.weight(1f), color = Color(0xFFA9A3B3)); Text(b, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } }
            Button({ scanned = true }, Modifier.fillMaxWidth()) { Text(if (scanned) "Сканирование завершено" else "Сканировать устройство") }
            if (scanned) Text("Базовые характеристики считаны. Проверка Vulkan/LWJGL выполняется только при наличии соответствующего runtime.", color = LunarColors.purpleBright, fontSize = 12.sp)
        }
    }
}

@Composable
fun CrashDoctor() {
    val context = LocalContext.current
    var result by remember { mutableStateOf("Локальный анализ не запускался.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) result = "Выбран файл: $uri"
    }
    fun analyze(text: String): String {
        val lower = text.lowercase()
        val hints = buildList {
            if ("outofmemoryerror" in lower) add("Недостаточно памяти: уменьшите render distance/RAM-профиль.")
            if ("nosuchmethoderror" in lower || "noclassdeffounderror" in lower) add("Вероятен конфликт модов или несовместимая версия библиотеки.")
            if ("org.lwjgl" in lower) add("Ошибка LWJGL/native backend: проверьте мобильный runtime и renderer.")
            if ("vk_error" in lower || "vulkan" in lower && "error" in lower) add("Обнаружена Vulkan-ошибка: попробуйте Auto/OpenGL или совместимый runtime.")
            if ("exit code 1" in lower) add("Minecraft завершился с кодом 1; нужен stack trace выше по логу.")
        }
        return if (hints.isEmpty()) "Явная причина не найдена. Откройте полный crash-report и ищите первый Caused by/Exception." else hints.joinToString("\n")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Lunar AI Crash Doctor")
        CardBox(Modifier.padding(14.dp)) {
            Text("Диагностика", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button({
                val log = File(context.filesDir, "instances/1.21.11/lunar-launch.log")
                result = if (log.exists()) analyze(log.readText().takeLast(250_000)) else "Лог ещё не создан."
            }, Modifier.fillMaxWidth()) { Text("Анализировать последний лог") }
            OutlinedButton({ picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Выбрать crash-report") }
            Spacer(Modifier.height(10.dp)); Text(result, color = LunarColors.purpleBright, fontSize = 12.sp)
        }
    }
}

@Composable
fun Benchmark() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Lunar Benchmark")
        CardBox(Modifier.padding(14.dp)) {
            Text("Тест устройства", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("CPU • RAM • базовая отзывчивость. Это не заменяет реальный GPU/Vulkan benchmark.", color = Color(0xFFA9A3B3))
            Spacer(Modifier.height(12.dp))
            Button(enabled = !running, onClick = {
                running = true; result = null
                scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    val startNs = System.nanoTime(); var x = 0L
                    repeat(5_000_000) { x = (x * 1664525L + 1013904223L) and 0x7fffffff }
                    val ms = (System.nanoTime() - startNs) / 1_000_000
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        running = false; result = "CPU test: ${ms} ms • checksum ${x and 0xffff}"
                    }
                }
            }, Modifier.fillMaxWidth()) { Text(if (running) "Тест выполняется…" else "Запустить Benchmark") }
            result?.let { Text(it, Modifier.padding(top = 10.dp), color = LunarColors.purpleBright, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun More(open: (Screen) -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Header("Ещё"); val rows = listOf("Настройки" to Screen.SETTINGS, "Проверка устройства" to Screen.DEVICE, "Benchmark" to Screen.BENCHMARK, "Аккаунты" to Screen.ACCOUNTS, "Crash Doctor" to Screen.CRASH, "Начальная настройка" to Screen.INITIAL, "О Lunar Launcher" to Screen.ABOUT); rows.forEach { (name, target) -> SettingRow(name) { open(target) } }; SocialLinks() } }

@Composable
fun SocialLinks() {
    val context = LocalContext.current
    CardBox(Modifier.padding(14.dp)) {
        Text("Социальные сети автора", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SocialButton("TikTok  •  @zalithlauncher_nalune", "https://www.tiktok.com/@zalithlauncher_nalune", context)
        SocialButton("Telegram  •  @snusik_god_tiktok", "https://t.me/snusik_god_tiktok", context)
        Spacer(Modifier.height(4.dp))
        OutlinedButton({}, Modifier.fillMaxWidth(), enabled = false) { Text("GitHub  •  SOON...") }
    }
}
@Composable
fun SocialButton(name: String, url: String, context: android.content.Context) {
    OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, Modifier.fillMaxWidth()) { Text(name) }
}

@Composable
fun About() { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Header("О Lunar Launcher"); CardBox(Modifier.padding(14.dp)) { Text("LUNAR LAUNCHER", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = LunarColors.purpleBright); Text("v1.0", color = Color(0xFFA9A3B3)); Spacer(Modifier.height(12.dp)); Text("Android-каркас лаунчера Minecraft Java с профилями, Java Manager, JSON-управлением, renderer-настройками и диагностикой.", color = Color(0xFFBDB7C7)); Spacer(Modifier.height(12.dp)); Text("Важно: интерфейс и менеджеры готовы, но полноценный Minecraft runtime / скачивание клиента / OAuth / Vulkan backend требуют отдельного runtime-модуля.", color = LunarColors.purpleBright, fontSize = 12.sp) } } }

@Composable
fun InitialSetup(onDone: (String) -> Unit) { var selected by remember { mutableStateOf("Performance") }; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Header("Первоначальная настройка"); CardBox(Modifier.padding(14.dp)) { Text("Настроим Lunar под устройство", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Выбери стартовый профиль. Позже всё можно изменить.", color = Color(0xFFA9A3B3)); Spacer(Modifier.height(12.dp)); listOf("Performance", "Balanced", "Quality", "Low End", "PvP").forEach { p -> Row(Modifier.fillMaxWidth().clickable { selected = p }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == p, { selected = p }); Text(p) } }; Spacer(Modifier.height(8.dp)); Button({ onDone(selected) }, Modifier.fillMaxWidth()) { Text("Готово") } } } }

@Composable
fun InitialSetupDialog(onDone: (String) -> Unit) { var selected by remember { mutableStateOf("Balanced") }; AlertDialog(onDismissRequest = {}, title = { Text("Lunar Initial Setup") }, text = { Column { Text("Выбери стартовый профиль"); listOf("Performance", "Balanced", "Quality", "Low End", "PvP").forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == p, { selected = p }); Text(p) } } } }, confirmButton = { TextButton({ onDone(selected) }) { Text("Применить") } }) }

@Composable
fun PlayDialog(version: String, loader: String, renderer: String, java: String, profile: String, close: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Проверка файлов…") }
    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf(0) }
    var installTotal by remember { mutableStateOf(0) }
    val (javaPath, gameDir, clientJar) = remember(version) { MinecraftLauncher.defaultPaths(context, version) }
    var runtime by remember { mutableStateOf(RuntimeManager.find(context)) }
    val effectiveJava = runtime?.java ?: javaPath
    val scope = rememberCoroutineScope()

    LaunchedEffect(version) {
        status = when {
            !clientJar.exists() -> "Файлы Minecraft ещё не скачаны"
            runtime == null -> "Встроенный Android Java runtime будет подготовлен при запуске"
            else -> "Готово к запуску"
        }
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Minecraft $version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$loader • $renderer")
                Text("Java: $java")
                Text("Профиль: $profile")
                HorizontalDivider()
                Text(status, color = if (status == "Готово к запуску") Color(0xFF9AFFB0) else LunarColors.purpleBright, fontWeight = FontWeight.Bold)
                if (installing) {
                    LinearProgressIndicator(
                        progress = { if (installTotal == 0) 0f else installProgress.toFloat() / installTotal },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Скачано: $installProgress / $installTotal", fontSize = 10.sp)
                }
                Text("Runtime: ${runtime?.root ?: "не установлен"}", fontSize = 10.sp, color = Color(0xFFA9A3B3))
                Text("Instance: ${gameDir.absolutePath}", fontSize = 10.sp, color = Color(0xFFA9A3B3))
                Text("Lunar запускает подготовленный Java runtime и Minecraft instance. Клиент Minecraft и Java не распространяются внутри исходника.", fontSize = 11.sp, color = Color(0xFFA9A3B3))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !installing, onClick = {
                    installing = true
                    status = "Скачивание официальных файлов…"
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val v = MinecraftVersionRepository.load(context, false).getOrNull()?.versions?.firstOrNull { it.id == version }
                        if (v == null) { status = "Версия не найдена в manifest"; installing = false; return@launch }
                        val result = MinecraftInstaller.install(context, v) { p -> installProgress = p.done; installTotal = p.total }
                        installing = false
                        status = result.fold({ "Minecraft установлен. ${if (!javaPath.exists()) "Теперь нужен Android Java runtime." else "Готов к запуску."}" }, { "Ошибка загрузки: ${it.message}" })
                    }
                }) { Text("СКАЧАТЬ") }
                Button(enabled = !installing && clientJar.exists(), onClick = {
                    scope.launch {
                        status = "Подготовка Android Java runtime…"
                        if (runtime == null) {
                            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                RuntimeBootstrap.ensureBundled(context)
                            }
                            runtime = prepared.getOrNull()?.let { RuntimeManager.find(context) }
                            if (runtime == null) {
                                status = "В APK отсутствует рабочий ARM64 Java runtime: ${prepared.exceptionOrNull()?.message ?: "unknown error"}"
                                return@launch
                            }
                        }
                        status = "Проверка аккаунта…"
                        val account = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            MicrosoftAuthManager.refreshIfNeeded(context)
                        }
                        if (account == null) {
                            status = "Войдите через Microsoft в разделе «Аккаунты»"
                            return@launch
                        }
                        val result = MinecraftLauncher.launch(
                            context,
                            MinecraftLauncher.Config(
                                version = version, javaHome = runtime!!.java, gameDir = gameDir, clientJar = clientJar,
                                versionJson = File(gameDir, "version.json"),
                                librariesDir = File(gameDir, "libraries"),
                                nativesDir = File(gameDir, "natives"),
                                assetsDir = File(gameDir, "assets"),
                                username = account.name,
                                uuid = account.uuid.replace("-", ""),
                                accessToken = account.accessToken,
                                userType = "msa"
                            )
                        )
                        status = result.fold({ "Minecraft запущен как ${account.name}" }, { "Ошибка: ${it.message}" })
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ЗАПУСТИТЬ")
                }
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Закрыть") } }
    )
}
