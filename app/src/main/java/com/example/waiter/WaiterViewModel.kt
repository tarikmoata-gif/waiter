package com.example.waiter

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Screen { SETTINGS, TABLES, ORDER }

data class AppSettings(
    val url: String = "", val db: String = "", val login: String = "",
    val apiKey: String = "", val printerIp: String = "", val autoFind: Boolean = true,
)

data class CartItem(val uid: Long, val product: Product, val qty: Int = 1, val note: String = "")

class WaiterViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("waiter", Context.MODE_PRIVATE)

    var settings by mutableStateOf(
        AppSettings(
            prefs.getString("url", "") ?: "",
            prefs.getString("db", "") ?: "",
            prefs.getString("login", "") ?: "",
            prefs.getString("apiKey", "") ?: "",
            prefs.getString("printerIp", "") ?: "",
            prefs.getBoolean("autoFind", true),
        )
    )
        private set

    var screen by mutableStateOf(if (settings.url.isBlank()) Screen.SETTINGS else Screen.TABLES)
    var menu by mutableStateOf<MenuResult?>(null)
    var table by mutableStateOf<Table?>(null)
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var busyText by mutableStateOf("")
    var lastOrder by mutableStateOf<OrderResult?>(null)
    val cart = mutableStateListOf<CartItem>()
    val sentTables = mutableStateMapOf<Int, Int>()

    // network scan state
    var scanning by mutableStateOf(false)
    var scanDone by mutableStateOf(false)
    var scanProgress by mutableFloatStateOf(0f)
    var scanSubnet by mutableStateOf<String?>(null)
    val found = mutableStateListOf<String>()

    private var repo: WaiterRepository? = null
    private var nextUid = 1L

    init { if (settings.url.isNotBlank()) loadMenu() }

    private fun repo(): WaiterRepository = repo ?: WaiterRepository(
        settings.url, settings.db, settings.login, settings.apiKey, settings.printerIp
    ).also { repo = it }

    // ---------- navigation ----------
    fun canGoBack() = screen == Screen.ORDER || (screen == Screen.SETTINGS && settings.url.isNotBlank())
    fun back() { if (canGoBack()) screen = Screen.TABLES }
    fun openSettings() { found.clear(); scanDone = false; screen = Screen.SETTINGS }
    fun openTable(t: Table) { table = t; cart.clear(); screen = Screen.ORDER }

    // ---------- settings ----------
    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("url", s.url).putString("db", s.db).putString("login", s.login)
            .putString("apiKey", s.apiKey).putString("printerIp", s.printerIp)
            .putBoolean("autoFind", s.autoFind).apply()
        settings = s
        repo = null
        menu = null
        screen = Screen.TABLES
        loadMenu()
    }

    private fun adoptPrinterIp(ip: String) {
        prefs.edit().putString("printerIp", ip).apply()
        settings = settings.copy(printerIp = ip)
        repo?.printerIp = ip
    }

    fun loadMenu() {
        viewModelScope.launch {
            busy = true; busyText = "Connecting..."
            try {
                val m = repo().loadMenu()
                if (m.ok) menu = m else { menu = null; status = m.error ?: "Could not load menu" }
            } catch (e: Exception) {
                status = "Load failed: ${e.message}"
            } finally { busy = false }
        }
    }

    // ---------- printer scan ----------
    fun scanPrinters() {
        if (scanning) return
        val prefix = PrinterScanner.subnetPrefix(getApplication())
        if (prefix == null) { status = "Not connected to a Wi-Fi / LAN network"; return }
        viewModelScope.launch {
            scanning = true; scanDone = false; scanSubnet = prefix; scanProgress = 0f
            found.clear()
            try {
                PrinterScanner.scan(prefix, { scanProgress = it / 254f }, { found.add(it) })
                found.sortBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
            } finally { scanning = false; scanDone = true }
        }
    }

    fun testPrint(ip: String) {
        if (ip.isBlank()) { status = "Enter or scan a printer IP first"; return }
        viewModelScope.launch {
            try { printTestSlip(ip.trim()); status = "Test slip sent to $ip" }
            catch (e: Exception) { status = "Cannot print to $ip: ${e.message}" }
        }
    }

    /** Scan the LAN; only returns an IP when exactly one printer answers. */
    private suspend fun recoverPrinter(): String? {
        if (!settings.autoFind) return null
        val prefix = PrinterScanner.subnetPrefix(getApplication()) ?: return null
        val hits = mutableListOf<String>()
        busyText = "Finding printer..."
        PrinterScanner.scan(prefix, { }, { hits.add(it) })
        return hits.singleOrNull()
    }

    /** Prints; on failure optionally re-discovers the printer and retries once. Returns error or null. */
    private suspend fun printWithRecovery(o: OrderResult): String? {
        return try {
            repo().printKitchenTicket(o); null
        } catch (e: Exception) {
            val ip = recoverPrinter()
            if (ip == null) {
                e.message ?: "Printer error"
            } else {
                adoptPrinterIp(ip)
                try { repo().printKitchenTicket(o); null }
                catch (e2: Exception) { e2.message ?: "Printer error" }
            }
        }
    }

    // ---------- cart ----------
    fun add(p: Product) {
        val i = cart.indexOfFirst { it.product.id == p.id && it.note.isBlank() }
        if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1) else cart.add(CartItem(nextUid++, p))
    }
    fun inc(i: Int) { cart[i] = cart[i].copy(qty = cart[i].qty + 1) }
    fun dec(i: Int) { if (cart[i].qty <= 1) cart.removeAt(i) else cart[i] = cart[i].copy(qty = cart[i].qty - 1) }
    fun setNote(i: Int, n: String) { cart[i] = cart[i].copy(note = n) }

    fun send() {
        val t = table ?: return
        if (cart.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true; busyText = "Sending..."
            try {
                val order = repo().sendOrder(
                    t.id, cart.map { OrderLine(it.product.id, it.qty.toDouble(), it.note) }
                )
                lastOrder = order
                sentTables[t.id] = (sentTables[t.id] ?: 0) + 1
                cart.clear()
                busyText = "Printing..."
                val err = printWithRecovery(order)
                if (err == null) {
                    status = "Sent to kitchen: table ${t.name}"
                    screen = Screen.TABLES
                } else {
                    status = "Order saved in Odoo, but PRINT FAILED: $err. Fix the printer, then tap Reprint."
                }
            } catch (e: Exception) {
                status = "Failed (cart kept): ${e.message}"
            } finally { busy = false }
        }
    }

    fun reprint() {
        val o = lastOrder ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true; busyText = "Printing..."
            val err = printWithRecovery(o)
            status = if (err == null) "Reprinted" else "Print failed: $err"
            busy = false
        }
    }
}
