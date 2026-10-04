package com.example.waiter

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

enum class Screen { SETTINGS, TABLES, ORDER }

data class AppSettings(
    val url: String = "", val db: String = "", val login: String = "",
    val apiKey: String = "", val printerIp: String = "",
)

data class CartItem(val product: Product, val qty: Int = 1, val note: String = "")

class WaiterViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("waiter", Context.MODE_PRIVATE)

    var settings by mutableStateOf(
        AppSettings(
            prefs.getString("url", "") ?: "",
            prefs.getString("db", "") ?: "",
            prefs.getString("login", "") ?: "",
            prefs.getString("apiKey", "") ?: "",
            prefs.getString("printerIp", "") ?: "",
        )
    )
        private set
    var screen by mutableStateOf(if (settings.url.isBlank()) Screen.SETTINGS else Screen.TABLES)
    var menu by mutableStateOf<MenuResult?>(null)
    var table by mutableStateOf<Table?>(null)
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var lastOrder by mutableStateOf<OrderResult?>(null)
    val cart = mutableStateListOf<CartItem>()
    private var repo: WaiterRepository? = null

    init { if (settings.url.isNotBlank()) loadMenu() }

    private fun repo(): WaiterRepository = repo ?: WaiterRepository(
        settings.url, settings.db, settings.login, settings.apiKey, settings.printerIp
    ).also { repo = it }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("url", s.url).putString("db", s.db).putString("login", s.login)
            .putString("apiKey", s.apiKey).putString("printerIp", s.printerIp).apply()
        settings = s
        repo = null
        menu = null
        screen = Screen.TABLES
        loadMenu()
    }

    fun loadMenu() {
        viewModelScope.launch {
            busy = true
            try {
                val m = repo().loadMenu()
                if (m.ok) menu = m else { menu = null; status = m.error ?: "Could not load menu" }
            } catch (e: Exception) {
                status = "Load failed: ${e.message}"
            } finally { busy = false }
        }
    }

    fun openTable(t: Table) { table = t; cart.clear(); screen = Screen.ORDER }

    fun add(p: Product) {
        val i = cart.indexOfFirst { it.product.id == p.id && it.note.isBlank() }
        if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1) else cart.add(CartItem(p))
    }
    fun inc(i: Int) { cart[i] = cart[i].copy(qty = cart[i].qty + 1) }
    fun dec(i: Int) { if (cart[i].qty <= 1) cart.removeAt(i) else cart[i] = cart[i].copy(qty = cart[i].qty - 1) }
    fun setNote(i: Int, n: String) { cart[i] = cart[i].copy(note = n) }

    fun send() {
        val t = table ?: return
        if (cart.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            try {
                val out = repo().sendOrder(
                    t.id, cart.map { OrderLine(it.product.id, it.qty.toDouble(), it.note) }
                )
                lastOrder = out.order
                cart.clear()
                if (out.printError == null) {
                    status = "Sent: table ${t.name}"
                    screen = Screen.TABLES
                } else {
                    status = "Order saved in Odoo, but PRINT FAILED: ${out.printError}. Tap Reprint."
                }
            } catch (e: Exception) {
                status = "Failed (cart kept): ${e.message}"
            } finally { busy = false }
        }
    }

    fun reprint() {
        val o = lastOrder ?: return
        viewModelScope.launch {
            try { repo().printKitchenTicket(o); status = "Reprinted" }
            catch (e: Exception) { status = "Print failed: ${e.message}" }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: WaiterViewModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        vm.status?.let { snack.showSnackbar(it, duration = SnackbarDuration.Long); vm.status = null }
    }
    BackHandler(vm.screen == Screen.ORDER) { vm.screen = Screen.TABLES }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.screen == Screen.ORDER) "Table ${vm.table?.name}" else "Waiter Orders") },
                actions = {
                    if (vm.lastOrder != null) TextButton(onClick = { vm.reprint() }) { Text("Reprint") }
                    if (vm.screen == Screen.TABLES) TextButton(onClick = { vm.loadMenu() }) { Text("Refresh") }
                    if (vm.screen != Screen.SETTINGS) TextButton(onClick = { vm.screen = Screen.SETTINGS }) { Text("Settings") }
                }
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
fun SettingsScreen(vm: WaiterViewModel) {
    var s by remember { mutableStateOf(vm.settings) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(s.url, { s = s.copy(url = it) }, label = { Text("Odoo URL (https://your-odoo.com)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.db, { s = s.copy(db = it) }, label = { Text("Database name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.login, { s = s.copy(login = it) }, label = { Text("Login (e.g. tablet1)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.apiKey, { s = s.copy(apiKey = it) }, label = { Text("API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.printerIp, { s = s.copy(printerIp = it) }, label = { Text("Kitchen printer IP (port 9100)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveSettings(s.copy(url = s.url.trim(), db = s.db.trim(),
                login = s.login.trim(), apiKey = s.apiKey.trim(), printerIp = s.printerIp.trim())) },
                enabled = s.url.isNotBlank() && s.db.isNotBlank() && s.login.isNotBlank() && s.apiKey.isNotBlank()) {
                Text("Save & connect")
            }
            if (vm.settings.url.isNotBlank()) OutlinedButton(onClick = { vm.screen = Screen.TABLES }) { Text("Cancel") }
        }
    }
}

@Composable
fun TablesScreen(vm: WaiterViewModel) {
    val menu = vm.menu
    if (menu == null) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            if (vm.busy) CircularProgressIndicator()
            else Button(onClick = { vm.loadMenu() }) { Text("Load menu / retry") }
        }
        return
    }
    val floors = menu.floors.orEmpty()
    var idx by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        if (floors.size > 1) {
            ScrollableTabRow(selectedTabIndex = idx.coerceIn(0, floors.lastIndex)) {
                floors.forEachIndexed { i, f ->
                    Tab(selected = i == idx, onClick = { idx = i }, text = { Text(f.name) })
                }
            }
        }
        val tables = floors.getOrNull(idx)?.tables.orEmpty()
        LazyVerticalGrid(
            GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tables) { t ->
                Card(Modifier.fillMaxWidth().height(90.dp).clickable { vm.openTable(t) }) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(t.name, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderScreen(vm: WaiterViewModel) {
    val products = vm.menu?.products.orEmpty()
    val cats = remember(products) {
        listOf("All") + products.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    var cat by remember { mutableStateOf("All") }
    val shown = if (cat == "All") products else products.filter { it.category == cat }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(2f).fillMaxHeight()) {
            LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c -> FilterChip(selected = c == cat, onClick = { cat = c }, label = { Text(c) }) }
            }
            LazyVerticalGrid(
                GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown) { p ->
                    Card(Modifier.fillMaxWidth().height(80.dp).clickable { vm.add(p) }) {
                        Column(Modifier.padding(8.dp)) {
                            Text(p.name, maxLines = 2)
                            Text(String.format("%.2f", p.price), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(vm.cart) { i, item ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.product.name, Modifier.weight(1f), maxLines = 2)
                            OutlinedButton(onClick = { vm.dec(i) }, contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(40.dp)) { Text("-") }
                            Text("${item.qty}", Modifier.padding(horizontal = 8.dp))
                            OutlinedButton(onClick = { vm.inc(i) }, contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(40.dp)) { Text("+") }
                        }
                        OutlinedTextField(item.note, { vm.setNote(i, it) }, label = { Text("Note") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                    }
                }
            }
            val total = vm.cart.sumOf { it.product.price * it.qty }
            Text("Total: " + String.format("%.2f", total), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp))
            Button(onClick = { vm.send() }, enabled = vm.cart.isNotEmpty() && !vm.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (vm.busy) "Sending..." else "SEND TO KITCHEN")
            }
        }
    }
}
