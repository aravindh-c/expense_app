package com.aravindh.expenselogger.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.fragment.app.Fragment
import com.aravindh.expenselogger.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class CashflowFragment : Fragment(R.layout.fragment_cashflow) {

    // ✅ PUT YOUR DEPLOYED APPS SCRIPT WEB APP URL HERE
    // Example: https://script.google.com/macros/s/XXXX/exec
    private val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"

    private lateinit var spMonth: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var tvMonthLabel: TextView

    private lateinit var tvFamily: TextView
    private lateinit var tvLast2: TextView

    private lateinit var graph: ConstraintLayout
    private lateinit var guideZero: Guideline

    private lateinit var barA: View
    private lateinit var barD: View
    private lateinit var barF: View

    private lateinit var tvNetA: TextView
    private lateinit var tvNetD: TextView
    private lateinit var tvNetF: TextView

    private lateinit var tvBreakA: TextView
    private lateinit var tvBreakD: TextView
    private lateinit var tvBreakF: TextView

    private val client = OkHttpClient()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spMonth = view.findViewById(R.id.spMonthCashflow)
        btnRefresh = view.findViewById(R.id.btnRefreshCashflow)
        tvMonthLabel = view.findViewById(R.id.tvCashflowMonthLabel)

        tvFamily = view.findViewById(R.id.tvCashflowFamily)
        tvLast2 = view.findViewById(R.id.tvLast2Cashflow)

        graph = view.findViewById(R.id.cashGraphContainer)
        guideZero = view.findViewById(R.id.guideZero)

        barA = view.findViewById(R.id.barCashAravindh)
        barD = view.findViewById(R.id.barCashDeepa)
        barF = view.findViewById(R.id.barCashFamily)

        tvNetA = view.findViewById(R.id.tvNetAravindh)
        tvNetD = view.findViewById(R.id.tvNetDeepa)
        tvNetF = view.findViewById(R.id.tvNetFamily)

        tvBreakA = view.findViewById(R.id.tvBreakAravindh)
        tvBreakD = view.findViewById(R.id.tvBreakDeepa)
        tvBreakF = view.findViewById(R.id.tvBreakFamily)

        btnRefresh.setOnClickListener { refreshAll() }

        fetchMonths { months ->
            if (months.isNotEmpty()) {
                setMonthSpinner(months)
                refreshAll()
            } else {
                Toast.makeText(requireContext(), "No months found in sheet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Called by MainActivity when user swipes to this page.
     */
    fun refreshCashflow() {
        refreshAll()
    }

    private fun refreshAll() {
        val month = getSelectedMonthValue()
        if (month.isEmpty()) return
        setMonthLabel(month)
        fetchCashflow(month) { ok ->
            if (ok) fetchLast2()
        }
    }

    private fun setMonthSpinner(months: List<String>) {
        // months are yyyy-MM
        val display = months.map { yyyyMm ->
            try {
                val dfIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val dfOut = SimpleDateFormat("MMM-yy", Locale.getDefault())
                dfOut.format(dfIn.parse(yyyyMm)!!)
            } catch (_: Exception) {
                yyyyMm
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, display)
        spMonth.adapter = adapter
        spMonth.tag = months

        spMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                refreshAll()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun getSelectedMonthValue(): String {
        val months = spMonth.tag as? List<*> ?: return ""
        val idx = spMonth.selectedItemPosition
        return months.getOrNull(idx)?.toString() ?: ""
    }

    private fun setMonthLabel(yyyyMm: String) {
        val label = try {
            val dfIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val dfOut = SimpleDateFormat("MMM-yy", Locale.getDefault())
            "Month: " + dfOut.format(dfIn.parse(yyyyMm)!!)
        } catch (_: Exception) {
            "Month: $yyyyMm"
        }
        tvMonthLabel.text = label
    }

    private fun fetchMonths(onDone: (List<String>) -> Unit) {
        Thread {
            try {
                val req = Request.Builder().url("$SCRIPT_URL?mode=months").get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    requireActivity().runOnUiThread { onDone(emptyList()) }
                    return@Thread
                }

                val json = JSONObject(body)
                val arr = json.optJSONArray("months")
                val list = mutableListOf<String>()
                if (arr != null) for (i in 0 until arr.length()) list.add(arr.getString(i))

                requireActivity().runOnUiThread { onDone(list) }
            } catch (_: Exception) {
                requireActivity().runOnUiThread { onDone(emptyList()) }
            }
        }.start()
    }

    private fun fetchCashflow(month: String, onDone: (Boolean) -> Unit) {
        Thread {
            try {
                val url = "$SCRIPT_URL?mode=cashflow&month=$month"
                val req = Request.Builder().url(url).get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Cashflow error: ${res.code}", Toast.LENGTH_SHORT).show()
                        onDone(false)
                    }
                    return@Thread
                }

                val json = JSONObject(body)
                val users = json.getJSONObject("users")
                val family = json.getJSONObject("family")

                fun readUser(obj: JSONObject): Triple<Float, Float, Float> {
                    val income = obj.optDouble("income", 0.0).toFloat()
                    val spend = obj.optDouble("spend", 0.0).toFloat()
                    val settlement = obj.optDouble("settlement", 0.0).toFloat()
                    val saving = obj.optDouble("saving", 0.0).toFloat()
                    val out = spend + settlement + saving
                    val net = obj.optDouble("net", (income - out).toDouble()).toFloat()
                    return Triple(income, out, net)
                }

                val (aIn, aOut, aNet) = readUser(users.getJSONObject("Aravindh"))
                val (dIn, dOut, dNet) = readUser(users.getJSONObject("Deepa"))
                val (fIn, fOut, fNet) = readUser(family)

                requireActivity().runOnUiThread {
                    tvBreakA.text = "In ₹%.0f / Out ₹%.0f".format(aIn, aOut)
                    tvBreakD.text = "In ₹%.0f / Out ₹%.0f".format(dIn, dOut)
                    tvBreakF.text = "In ₹%.0f / Out ₹%.0f".format(fIn, fOut)

                    tvNetA.text = "₹%.0f".format(aNet)
                    tvNetD.text = "₹%.0f".format(dNet)
                    tvNetF.text = "₹%.0f".format(fNet)

                    tvFamily.text = "Family: Income ₹%.0f | Out ₹%.0f | Net ₹%.0f".format(fIn, fOut, fNet)

                    renderNetBars(aNet, dNet, fNet)
                    onDone(true)
                }

            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Cashflow parse error", Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }.start()
    }

    /**
     * Renders bars around a 0-line in the middle.
     * Positive net grows upwards from the center line.
     * Negative net grows downwards from the center line.
     */
    private fun renderNetBars(netA: Float, netD: Float, netF: Float) {
        graph.post {
            val containerH = graph.height
            if (containerH <= 0) return@post

            // Zero line stays exactly mid
            val guideParams = guideZero.layoutParams as ConstraintLayout.LayoutParams
            guideParams.guidePercent = 0.5f
            guideZero.layoutParams = guideParams

            // scale based on largest absolute net
            val maxAbs = max(max(abs(netA), abs(netD)), abs(netF)).coerceAtLeast(1f)
            val pad = (maxAbs * 0.20f).coerceAtLeast(1000f) // headroom
            val axis = maxAbs + pad

            val halfH = (containerH * 0.45f).toInt() // keep margins for labels
            val minPx = (containerH * 0.05f).toInt().coerceAtLeast(8)

            fun applyBar(bar: View, net: Float) {
                val ratio = (abs(net) / axis).coerceIn(0f, 1f)
                val h = max((halfH * ratio).toInt(), minPx)

                val lp = bar.layoutParams
                lp.height = h
                bar.layoutParams = lp

                // bar view lives inside FrameLayout in XML -> use FrameLayout.LayoutParams
                val flp = bar.layoutParams as FrameLayout.LayoutParams
                flp.gravity =
                    if (net >= 0f) (android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
                    else (android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
                bar.layoutParams = flp

                bar.requestLayout()
            }

            applyBar(barA, netA)
            applyBar(barD, netD)
            applyBar(barF, netF)
        }
    }

    private fun fetchLast2() {
        Thread {
            try {
                val req = Request.Builder().url("$SCRIPT_URL?mode=last2").get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@Thread

                val json = JSONObject(body)
                val arr = json.optJSONArray("records")

                val lines = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val ts = r.optString("ts")
                        val name = r.optString("name")
                        val by = r.optString("loggedBy")
                        lines.add("$ts | $name | $by")
                    }
                }

                requireActivity().runOnUiThread {
                    tvLast2.text =
                        if (lines.isEmpty()) "Last 2: -"
                        else "Last 2:\n" + lines.joinToString("\n")
                }
            } catch (_: Exception) {
                // ignore
            }
        }.start()
    }
}
