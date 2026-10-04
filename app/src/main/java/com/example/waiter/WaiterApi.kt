package com.example.waiter

import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- JSON-RPC envelope ----------
data class RpcRequest<T>(val jsonrpc: String = "2.0", val method: String = "call", val params: T)
data class RpcError(val message: String?, val data: Map<String, Any>?)
data class RpcResponse<T>(val result: T?, val error: RpcError?)

// ---------- DTOs ----------
data class AuthParams(val db: String, val login: String, val password: String)
data class Empty(val config_id: Int? = null)
data class Table(val id: Int, val name: String)
data class Floor(val id: Int, val name: String, val tables: List<Table>)
data class Product(val id: Int, val name: String, val price: Double, val category: String)
data class MenuResult(val ok: Boolean, val error: String?, val config_id: Int?,
                      val floors: List<Floor>?, val products: List<Product>?)
data class OrderLine(val product_id: Int, val qty: Double, val note: String = "")
data class OrderParams(val table_id: Int, val lines: List<OrderLine>, val config_id: Int? = null)
data class PrintedLine(val name: String, val qty: Double, val note: String)
data class OrderResult(val ok: Boolean, val error: String?, val order_ref: String?,
                       val table: String?, val new_lines: List<PrintedLine>?)

data class SendOutcome(val order: OrderResult, val printError: String?)

interface OdooService {
    @POST("web/session/authenticate")
    suspend fun authenticate(@Body b: RpcRequest<AuthParams>): RpcResponse<Map<String, Any>>

    @POST("kitchen/api/menu")
    suspend fun menu(@Body b: RpcRequest<Empty>): RpcResponse<MenuResult>

    @POST("kitchen/api/order")
    suspend fun order(@Body b: RpcRequest<OrderParams>): RpcResponse<OrderResult>
}

// ---------- Session cookie kept in memory ----------
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store[it.name] = it }
    }
    override fun loadForRequest(url: HttpUrl) = store.values.toList()
}

class WaiterRepository(
    baseUrl: String,                 // "https://your-odoo.com/"  (trailing slash!)
    private val db: String,
    private val login: String,
    private val apiKey: String,
    private val printerIp: String,   // "192.168.1.50"
) {
    private val service: OdooService = Retrofit.Builder()
        .baseUrl(baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" })
        .client(OkHttpClient.Builder().cookieJar(MemoryCookieJar()).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(OdooService::class.java)

    private var loggedIn = false

    private suspend fun ensureLogin() {
        if (loggedIn) return
        val r = service.authenticate(RpcRequest(params = AuthParams(db, login, apiKey)))
        if (r.error != null) error(r.error.message ?: "Login failed")
        loggedIn = true
    }

    /** Runs a call, re-logging in once if the Odoo session expired. */
    private suspend fun <T> call(block: suspend () -> RpcResponse<T>): T {
        ensureLogin()
        var r = block()
        if (r.error != null && r.error!!.data?.get("name")?.toString()?.contains("SessionExpired") == true) {
            loggedIn = false; ensureLogin(); r = block()
        }
        r.error?.let { error(it.message ?: "Odoo error") }
        return r.result ?: error("Empty response")
    }

    suspend fun loadMenu(): MenuResult = call { service.menu(RpcRequest(params = Empty())) }

    /** Sends the order to Odoo, then prints ONLY the newly added lines in the kitchen. */
    suspend fun sendOrder(tableId: Int, lines: List<OrderLine>): SendOutcome {
        val res = call { service.order(RpcRequest(params = OrderParams(tableId, lines))) }
        if (!res.ok) error(res.error ?: "Order rejected")
        // The order is already in Odoo here; a print failure must NOT trigger a resend.
        val printError = try { printKitchenTicket(res); null } catch (e: Exception) { e.message ?: "Printer error" }
        return SendOutcome(res, printError)
    }

    suspend fun printKitchenTicket(o: OrderResult) = withContext(Dispatchers.IO) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("[C]<b><font size='big'>TABLE ${o.table}</font></b>\n")
        sb.append("[C]$time\n[C]--------------------------------\n")
        o.new_lines.orEmpty().forEach {
            sb.append("[L]<b>${it.qty.toInt()} x ${it.name}</b>\n")
            if (it.note.isNotBlank()) sb.append("[L]   <i>** ${it.note}</i>\n")
        }
        sb.append("\n")
        // 203 dpi, 80 mm paper, 42 chars per line
        val printer = EscPosPrinter(TcpConnection(printerIp, 9100, 5000), 203, 80f, 42)
        printer.printFormattedTextAndCut(sb.toString())
        printer.disconnectPrinter()
    }
}
