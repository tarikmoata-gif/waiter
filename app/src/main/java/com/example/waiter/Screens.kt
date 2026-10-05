@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.waiter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

fun money(v: Double) = "%.2f".format(v)

// =====================================================================
// App shell
// =====================================================================
@Composable
fun App(vm: WaiterViewModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        vm.status?.let { snack.showSnackbar(it, duration = SnackbarDuration.Long); vm.status = null }
    }
    BackHandler(vm.canGoBack()) { vm.back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.screen) {
                            Screen.TABLES -> "Tables"
                            Screen.ORDER -> "Table ${vm.table?.name}"
                            Screen.SETTINGS -> "Settings"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (vm.canGoBack()) IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (vm.lastOrder != null && vm.screen != Screen.SETTINGS) {
                        TextButton(
                            onClick = { vm.reprint() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) { Text("Reprint last") }
                    }
                    if (vm.screen == Screen.TABLES) IconButton(onClick = { vm.loadMenu() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    if (vm.screen != Screen.SETTINGS) IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.TABLES -> TablesScreen(vm)
                Screen.ORDER -> OrderScreen(vm)
            }
        }
    }
}

@Composable
fun StatusDot(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).background(
                if (ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error, CircleShape
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =====================================================================
// Tables
// =====================================================================
@Composable
fun TablesScreen(vm: WaiterViewModel) {
    val menu = vm.menu
    if (menu == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            Arrangement.Center, Alignment.CenterHorizontally
        ) {
            if (vm.busy) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Connecting to Odoo...")
            } else {
                Text("Not connected", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Check Settings, and make sure a restaurant POS session is open in Odoo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.loadMenu() }) { Text("Retry") }
            }
        }
        return
    }

    val floors = menu.floors.orEmpty()
    var idx by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatusDot(true, "Odoo connected")
            StatusDot(vm.settings.printerIp.isNotBlank(),
                if (vm.settings.printerIp.isBlank()) "No printer set" else "Printer ${vm.settings.printerIp}")
        }
        if (floors.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(floors) { i, f ->
                    FilterChip(selected = i == idx, onClick = { idx = i }, label = { Text(f.name) })
                }
            }
        }
        val tables = floors.getOrNull(idx.coerceIn(0, (floors.size - 1).coerceAtLeast(0)))?.tables.orEmpty()
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tables) { t ->
                val sent = vm.sentTables[t.id] ?: 0
                ElevatedCard(
                    onClick = { vm.openTable(t) },
                    modifier = Modifier.fillMaxWidth().height(104.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (sent > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        Text("TABLE", Modifier.align(Alignment.TopStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t.name, Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (sent > 0) Text("sent: $sent", Modifier.align(Alignment.BottomEnd),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Order
// =====================================================================
@Composable
fun OrderScreen(vm: WaiterViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                Catalog(vm, Modifier.weight(3f).fillMaxHeight())
                CartPanel(vm, Modifier.weight(2f).fillMaxHeight().padding(8.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Catalog(vm, Modifier.weight(1f).fillMaxWidth())
                CartPanel(vm, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
            }
        }
    }
}

@Composable
fun Catalog(vm: WaiterViewModel, modifier: Modifier) {
    val products = vm.menu?.products.orEmpty()
    val cats = remember(products) {
        listOf("All") + products.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    var cat by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    val shown = products.filter {
        (cat == "All" || it.category == cat) && it.name.contains(query, ignoreCase = true)
    }

    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search products") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cats) { c -> FilterChip(selected = c == cat, onClick = { cat = c }, label = { Text(c) }) }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shown) { p ->
                val inCart = vm.cart.filter { it.product.id == p.id }.sumOf { it.qty }
                ElevatedCard(
                    onClick = { vm.add(p) },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (inCart > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        Text(p.name,
                            Modifier.align(Alignment.TopStart).padding(end = if (inCart > 0) 30.dp else 0.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text(money(p.price), Modifier.align(Alignment.BottomStart),
                            color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        if (inCart > 0) Box(
                            Modifier.align(Alignment.TopEnd).size(26.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$inCart", color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartPanel(vm: WaiterViewModel, modifier: Modifier) {
    val count = vm.cart.sumOf { it.qty }
    val total = vm.cart.sumOf { it.product.price * it.qty }
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Table ${vm.table?.name}", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("$count item(s)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.cart.isNotEmpty()) TextButton(onClick = { vm.cart.clear() }) { Text("Clear") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (vm.cart.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("Tap products to add them", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(vm.cart, key = { _, c -> c.uid }) { i, item -> CartRow(vm, i, item) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(money(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { vm.send() },
                enabled = vm.cart.isNotEmpty() && !vm.busy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(vm.busyText)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SEND TO KITCHEN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CartRow(vm: WaiterViewModel, i: Int, item: CartItem) {
    var noteOpen by remember { mutableStateOf(item.note.isNotBlank()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, maxLines = 2, fontWeight = FontWeight.Medium)
                Text(money(item.product.price * item.qty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QtyButton("-") { vm.dec(i) }
            Text("${item.qty}", Modifier.widthIn(min = 34.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            QtyButton("+") { vm.inc(i) }
        }
        if (noteOpen) {
            OutlinedTextField(
                value = item.note, onValueChange = { vm.setNote(i, it) },
                placeholder = { Text("e.g. no onions") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        } else {
            TextButton(onClick = { noteOpen = true }, contentPadding = PaddingValues(0.dp)) {
                Text("+ Add note")
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
fun QtyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

// =====================================================================
// Settings
// =====================================================================
@Composable
fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun SettingsScreen(vm: WaiterViewModel) {
    var s by remember { mutableStateOf(vm.settings) }

    // exactly one printer found -> fill it in automatically
    LaunchedEffect(vm.scanning) {
        if (!vm.scanning && vm.scanDone && vm.found.size == 1) s = s.copy(printerIp = vm.found[0])
    }

    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("Odoo server", "Used to create orders on POS tables") {
                OutlinedTextField(s.url, { s = s.copy(url = it) }, label = { Text("Odoo URL (https://your-odoo.com)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.db, { s = s.copy(db = it) }, label = { Text("Database name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.login, { s = s.copy(login = it) }, label = { Text("Login (e.g. tablet1)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.apiKey, { s = s.copy(apiKey = it) }, label = { Text("API key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
            }

            SectionCard("Kitchen printer", "Epson TM-T20 on your Wi-Fi / LAN (raw port 9100)") {
                OutlinedTextField(s.printerIp, { s = s.copy(printerIp = it) }, label = { Text("Printer IP address") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.scanPrinters() }, enabled = !vm.scanning) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.scanning) "Scanning..." else "Scan network")
                    }
                    OutlinedButton(onClick = { vm.testPrint(s.printerIp) }) { Text("Test print") }
                }

                if (vm.scanning) {
                    LinearProgressIndicator(progress = { vm.scanProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Checking ${vm.scanSubnet}.1 - ${vm.scanSubnet}.254 ...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (vm.scanDone) {
                    if (vm.found.isEmpty()) {
                        Text("No printer found. Check that it is powered on, connected to this same network, " +
                            "and that its network interface is enabled.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Found ${vm.found.size} device(s) with port 9100 open. Select your printer " +
                            "(use Test print if unsure which one it is):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        vm.found.forEach { ip ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                    .clickable { s = s.copy(printerIp = ip) }.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = s.printerIp == ip, onClick = { s = s.copy(printerIp = ip) })
                                Text(ip, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                TextButton(onClick = { vm.testPrint(ip) }) { Text("Test print") }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-find printer", style = MaterialTheme.typography.titleSmall)
                        Text("If printing fails (e.g. the printer got a new IP), scan the network and " +
                            "reconnect automatically when exactly one printer is found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.autoFind, onCheckedChange = { s = s.copy(autoFind = it) })
                }
            }

            Button(
                onClick = {
                    vm.saveSettings(s.copy(url = s.url.trim(), db = s.db.trim(), login = s.login.trim(),
                        apiKey = s.apiKey.trim(), printerIp = s.printerIp.trim()))
                },
                enabled = s.url.isNotBlank() && s.db.isNotBlank() && s.login.isNotBlank() && s.apiKey.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Save & connect", fontWeight = FontWeight.Bold) }
        }
    }
}
