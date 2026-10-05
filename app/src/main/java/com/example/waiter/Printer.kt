package com.example.waiter

import android.content.Context
import android.net.ConnectivityManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object PrinterScanner {

    /** "192.168.1" for a tablet on 192.168.1.x, or null when not on a network. */
    fun subnetPrefix(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        val ip = lp.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: return null
        val p = ip.split(".")
        return if (p.size == 4) p.take(3).joinToString(".") else null
    }

    private fun isOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (e: Exception) {
        false
    }

    /**
     * Tries TCP port 9100 (raw ESC/POS) on x.x.x.1 - x.x.x.254.
     * Callbacks are delivered on the main thread.
     */
    suspend fun scan(prefix: String, onProgress: (Int) -> Unit, onFound: (String) -> Unit) {
        coroutineScope {
            val gate = Semaphore(48)
            val done = AtomicInteger(0)
            (1..254).map { n ->
                async(Dispatchers.IO) {
                    val ip = "$prefix.$n"
                    val open = gate.withPermit { isOpen(ip, 9100, 600) }
                    withContext(Dispatchers.Main) {
                        if (open) onFound(ip)
                        onProgress(done.incrementAndGet())
                    }
                }
            }.awaitAll()
        }
    }
}

suspend fun printTestSlip(ip: String) = withContext(Dispatchers.IO) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    val printer = EscPosPrinter(TcpConnection(ip, 9100, 3000), 203, 80f, 42)
    printer.printFormattedTextAndCut(
        "[C]<b><font size='big'>TEST</font></b>\n" +
        "[C]Waiter Orders\n[C]$ip\n[C]$time\n\n"
    )
    printer.disconnectPrinter()
}
