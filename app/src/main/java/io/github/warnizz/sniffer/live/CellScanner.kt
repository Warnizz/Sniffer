package io.github.warnizz.sniffer.live

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import io.github.warnizz.sniffer.towers.Radio
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Polls the default data SIM's modem for cell info. Needs ACCESS_FINE_LOCATION. */
class CellScanner(private val context: Context) {

    @Volatile private var nrOverride = false

    private fun manager(): TelephonyManager {
        val base = context.getSystemService(TelephonyManager::class.java)
        val sub = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(sub) else base
    }

    @SuppressLint("MissingPermission")
    fun snapshots(intervalMs: Long): Flow<LiveSnapshot> = callbackFlow {
        val tm = manager()
        val executor = Executors.newSingleThreadExecutor()
        val displayCallback = registerDisplayInfo(tm, executor)
        val poller = launch {
            while (true) {
                try {
                    tm.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) { trySend(toSnapshot(cellInfo)) }
                        override fun onError(errorCode: Int, detail: Throwable?) { trySend(fallback(tm)) }
                    })
                } catch (e: SecurityException) {
                    trySend(LiveSnapshot.EMPTY.copy(timeMs = System.currentTimeMillis()))
                }
                delay(intervalMs)
            }
        }
        awaitClose {
            poller.cancel()
            if (displayCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.unregisterTelephonyCallback(displayCallback as TelephonyCallback)
            }
            executor.shutdown()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallback(tm: TelephonyManager): LiveSnapshot =
        try { toSnapshot(tm.allCellInfo) } catch (e: SecurityException) { LiveSnapshot.EMPTY.copy(timeMs = System.currentTimeMillis()) }

    /** 5G NSA shows up as LTE in CellInfo; the display-info override reveals it (API 31+, best effort). */
    private fun registerDisplayInfo(tm: TelephonyManager, executor: java.util.concurrent.Executor): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    nrOverride = info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                        info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                }
            }
            tm.registerTelephonyCallback(executor, cb)
            cb
        } catch (e: SecurityException) {
            Log.i(TAG, "No permission for display info; 5G NSA will show as 4G")
            null
        }
    }

    private fun toSnapshot(infos: List<CellInfo>?): LiveSnapshot {
        val now = System.currentTimeMillis()
        val cells = infos.orEmpty().mapNotNull { toLiveCell(it) }
        val serving = cells.firstOrNull { it.serving }
        val neighbours = cells.filter { !it.serving }.map {
            if (it.mcc == null && serving != null) it.copy(mcc = serving.mcc, mnc = serving.mnc) else it
        }
        var netType = netTypeOf(serving?.radio)
        if (netType == 4 && nrOverride) netType = 5
        return LiveSnapshot(serving, neighbours, netType, now)
    }

    private fun Int.known(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE }
    private fun Long.known(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE }
    private fun String?.int(): Int? = this?.toIntOrNull()

    private fun toLiveCell(info: CellInfo): LiveCell? = when (info) {
        is CellInfoLte -> info.cellIdentity.let { id ->
            LiveCell(Radio.LTE, id.mccString.int(), id.mncString.int(), id.tac.known(), id.ci.known()?.toLong(),
                id.pci.known(), info.cellSignalStrength.rsrp.known() ?: info.cellSignalStrength.dbm.known(), info.isRegistered)
        }
        is CellInfoNr -> (info.cellIdentity as CellIdentityNr).let { id ->
            val ss = info.cellSignalStrength as CellSignalStrengthNr
            LiveCell(Radio.NR, id.mccString.int(), id.mncString.int(), id.tac.known(), id.nci.known(),
                id.pci.known(), ss.ssRsrp.known() ?: ss.dbm.known(), info.isRegistered)
        }
        is CellInfoWcdma -> info.cellIdentity.let { id ->
            LiveCell(Radio.UMTS, id.mccString.int(), id.mncString.int(), id.lac.known(), id.cid.known()?.toLong(),
                id.psc.known(), info.cellSignalStrength.dbm.known(), info.isRegistered)
        }
        is CellInfoGsm -> info.cellIdentity.let { id ->
            LiveCell(Radio.GSM, id.mccString.int(), id.mncString.int(), id.lac.known(), id.cid.known()?.toLong(),
                null, info.cellSignalStrength.dbm.known(), info.isRegistered)
        }
        is CellInfoCdma -> info.cellIdentity.let { id ->
            LiveCell(Radio.CDMA, null, id.systemId.known(), id.networkId.known(), id.basestationId.known()?.toLong(),
                null, info.cellSignalStrength.dbm.known(), info.isRegistered)
        }
        else -> null
    }

    private companion object { const val TAG = "CellScanner" }
}
