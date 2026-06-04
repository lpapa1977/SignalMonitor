package com.lpapa.signalmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lpapa.signalmonitor.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Monitor de señal en tiempo real. Permite elegir entre señal MÓVIL (dBm de la
 * portadora, con detalle LTE/5G y selección de SIM) o WiFi (RSSI + SSID).
 * Muestra un gráfico que se desplaza, coloreado por calidad, y permite exportar
 * las lecturas a CSV.
 */
class MainActivity : AppCompatActivity() {

    private enum class Mode { MOBILE, WIFI }

    private data class CsvRow(
        val timeMs: Long,
        val type: String,
        val value: Int,
        val level: Int,
        val extra: String
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var wifiManager: WifiManager

    private var mode = Mode.MOBILE
    private var sampleIntervalMs = 1000L

    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var activeTm: TelephonyManager? = null

    // Última lectura móvil (la fija el callback; la consume el muestreador).
    private var lastMobileDbm: Int? = null
    private var lastMobileLevel: Int = 0
    private var lastMobileExtra: String = ""

    // Multi-SIM
    private var subIds: List<Int> = emptyList()
    private var selectedSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var suppressSimEvent = false

    private val intervalValuesMs = listOf(500L, 1000L, 2000L, 5000L)

    // Registro para exportar a CSV (acotado para no crecer sin límite).
    private val log = ArrayList<CsvRow>()
    private val maxLog = 10_000

    private val handler = Handler(Looper.getMainLooper())
    private val sampler = object : Runnable {
        override fun run() {
            when (mode) {
                Mode.MOBILE -> lastMobileDbm?.let {
                    record(it, lastMobileLevel, "móvil", lastMobileExtra)
                }
                Mode.WIFI -> sampleWifi()
            }
            handler.postDelayed(this, sampleIntervalMs)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_PHONE_STATE] == true ||
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        ) {
            startListening()
        } else {
            binding.status.text = getString(R.string.permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        subscriptionManager = getSystemService(SubscriptionManager::class.java)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        setupModeToggle()
        setupIntervalSpinner()
        setupSimSpinnerListener()
        binding.btnExport.setOnClickListener { exportCsv() }

        // Arranca siempre en modo Móvil (más fiable que android:checked en el XML).
        binding.modeToggle.check(R.id.btnMobile)
        mode = Mode.MOBILE
        applyModeVisibility()
    }

    override fun onStart() {
        super.onStart()
        ensurePermissionsAndStart()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(sampler)
        stopTelephony()
    }

    // ---------------------------------------------------------------- UI setup

    private fun setupModeToggle() {
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = if (checkedId == R.id.btnWifi) Mode.WIFI else Mode.MOBILE
            onModeChanged()
        }
    }

    private fun setupIntervalSpinner() {
        val labels = listOf("0,5 s", "1 s", "2 s", "5 s")
        binding.spinnerInterval.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, labels
        )
        binding.spinnerInterval.setSelection(1) // 1 s por defecto
        binding.spinnerInterval.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    sampleIntervalMs = intervalValuesMs[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
    }

    private fun setupSimSpinnerListener() {
        binding.spinnerSim.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    if (suppressSimEvent) return
                    val newSub = subIds.getOrNull(pos) ?: return
                    if (newSub != selectedSubId) {
                        selectedSubId = newSub
                        binding.graph.clear()
                        registerTelephony()
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
    }

    private fun onModeChanged() {
        binding.graph.clear()
        lastMobileDbm = null
        applyModeVisibility()
        binding.status.text = getString(R.string.listening)
    }

    private fun applyModeVisibility() {
        val mobile = mode == Mode.MOBILE
        val showSim = mobile && subIds.size > 1
        binding.simRow.visibility = if (showSim) android.view.View.VISIBLE else android.view.View.GONE
        binding.detail.visibility = if (mobile) android.view.View.VISIBLE else android.view.View.GONE
    }

    // -------------------------------------------------------------- Permisos

    private fun ensurePermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.READ_PHONE_STATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (needed.isEmpty()) startListening()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startListening() {
        loadSimList()
        registerTelephony()
        binding.status.text = getString(R.string.listening)
        handler.removeCallbacks(sampler)
        handler.post(sampler)
    }

    // ---------------------------------------------------------------- Móvil

    private fun loadSimList() {
        subIds = try {
            subscriptionManager.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        if (selectedSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            selectedSubId = subIds.firstOrNull() ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        if (subIds.size > 1) {
            val labels = try {
                subscriptionManager.activeSubscriptionInfoList.map { info ->
                    "${info.displayName} (SIM ${info.simSlotIndex + 1})"
                }
            } catch (e: SecurityException) {
                subIds.map { "SIM $it" }
            }
            suppressSimEvent = true
            binding.spinnerSim.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, labels
            )
            val sel = subIds.indexOf(selectedSubId).coerceAtLeast(0)
            binding.spinnerSim.setSelection(sel)
            suppressSimEvent = false
        }
        applyModeVisibility()
    }

    private fun registerTelephony() {
        stopTelephony()
        val tm = if (selectedSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            telephonyManager.createForSubscriptionId(selectedSubId)
        else telephonyManager
        activeTm = tm

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    handleSignal(signalStrength)
                }
            }
            telephonyCallback = cb
            tm.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    handleSignal(signalStrength)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    private fun stopTelephony() {
        val tm = activeTm ?: telephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            phoneStateListener = null
        }
    }

    private fun handleSignal(signalStrength: SignalStrength) {
        val primary: CellSignalStrength? = signalStrength.cellSignalStrengths
            .firstOrNull { it.dbm != Int.MAX_VALUE }
        val dbm = primary?.dbm ?: return
        val level = primary.level

        val nr = signalStrength.cellSignalStrengths
            .filterIsInstance<CellSignalStrengthNr>().firstOrNull()
        val lte = signalStrength.cellSignalStrengths
            .filterIsInstance<CellSignalStrengthLte>().firstOrNull()

        val detail = when {
            nr != null && nr.ssRsrp != Int.MAX_VALUE ->
                "5G NR · RSRP ${fmt(nr.ssRsrp)} · RSRQ ${fmt(nr.ssRsrq)} · SINR ${fmt(nr.ssSinr)}"
            lte != null && lte.rsrp != Int.MAX_VALUE ->
                "LTE · RSRP ${fmt(lte.rsrp)} · RSRQ ${fmt(lte.rsrq)} · SINR ${fmt(lte.rssnr)}"
            else -> getString(R.string.no_detail)
        }

        runOnUiThread {
            lastMobileDbm = dbm
            lastMobileLevel = level
            lastMobileExtra = detail
            if (mode == Mode.MOBILE) {
                binding.dbmValue.text = getString(R.string.dbm_format, dbm)
                binding.levelValue.text = getString(R.string.level_format, level, levelLabel(level))
                binding.networkType.text = getString(R.string.network_format, networkTypeName())
                binding.detail.text = detail
            }
        }
    }

    private fun networkTypeName(): String {
        val type = try {
            (activeTm ?: telephonyManager).dataNetworkType
        } catch (e: SecurityException) {
            return "—"
        }
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "—"
        }
    }

    // ----------------------------------------------------------------- WiFi

    @Suppress("DEPRECATION")
    private fun sampleWifi() {
        val info = wifiManager.connectionInfo
        if (info == null || info.networkId == -1 || info.rssi <= -127) {
            binding.status.text = getString(R.string.no_wifi)
            binding.dbmValue.text = getString(R.string.dbm_placeholder)
            binding.levelValue.text = ""
            binding.networkType.text = ""
            return
        }
        val rssi = info.rssi
        val ssid = info.ssid?.removeSurrounding("\"") ?: "?"
        val level = wifiLevel(rssi)

        binding.status.text = getString(R.string.listening)
        binding.dbmValue.text = getString(R.string.dbm_format, rssi)
        binding.levelValue.text = getString(R.string.level_format, level, levelLabel(level))
        binding.networkType.text = getString(R.string.wifi_ssid_format, ssid)
        record(rssi, level, "wifi", ssid)
    }

    private fun wifiLevel(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    // ----------------------------------------------------------- Compartido

    private fun record(value: Int, level: Int, type: String, extra: String) {
        binding.graph.addValue(value, level)
        log.add(CsvRow(System.currentTimeMillis(), type, value, level, extra))
        if (log.size > maxLog) log.removeAt(0)
    }

    private fun fmt(v: Int): String = if (v == Int.MAX_VALUE) "—" else v.toString()

    private fun levelLabel(level: Int): String = when (level) {
        0 -> getString(R.string.level_none)
        1 -> getString(R.string.level_poor)
        2 -> getString(R.string.level_moderate)
        3 -> getString(R.string.level_good)
        else -> getString(R.string.level_great)
    }

    // ------------------------------------------------------------ Exportar

    private fun exportCsv() {
        if (log.isEmpty()) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("timestamp_ms,fecha_hora,tipo,valor_dbm,nivel,detalle\n")
        for (r in log) {
            val safeExtra = r.extra.replace(",", ";").replace("\n", " ")
            sb.append("${r.timeMs},${df.format(Date(r.timeMs))},${r.type},${r.value},${r.level},$safeExtra\n")
        }

        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "senal_${log.last().timeMs}.csv")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.export_share)))
        Toast.makeText(this, getString(R.string.export_done, log.size), Toast.LENGTH_SHORT).show()
    }
}
