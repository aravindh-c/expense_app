package com.aravindh.expenselogger.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.fragment.app.Fragment
import com.aravindh.expenselogger.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class CashflowFragment : Fragment(R.layout.fragment_cashflow) {

    // ✅ Your deployed Apps Script Web App URL (/exec)
    private val SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"

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
                toast("No months found in sheet")
            }
        }
    }

    /** Call this from MainActivity when user lands on page-3 */
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
        val display = months.map { yyyyMm ->
            try {
                val dfIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val dfOut = SimpleDateFormat("MMM-yy", Locale.getDefault())
                dfOut.format(dfIn.parse(yyyyMm)!!)
            } catch (_: Exception) {
                yyyyMm
            }
        }

        spMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            display
        )

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

    /**
     * Expects JSON shape:
     * {
     *  status:"OK",
     *  month:"yyyy-MM",
     *  aravindh:{in,out,net},
     *  deepa:{in,out,net},
     *  family:{in,out,net}
     * }
     */
    private fun fetchCashflow(month: String, onDone: (Boolean) -> Unit) {
        Thread {
            try {
                val monthEnc = URLEncoder.encode(month, "UTF-8")
                val url = "$SCRIPT_URL?mode=cashflow&month=$monthEnc"
                val req = Request.Builder().url(url).get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    requireActivity().runOnUiThread {
                        toast("Cashflow error: ${res.code}")
                        onDone(false)
                    }
                    return@Thread
                }

                // If script accidentally returns "OK" or HTML, this will throw -> parse error
                val json = JSONObject(body)

                val aObj = json.getJSONObject("aravindh")
                val dObj = json.getJSONObject("deepa")
                val fObj = json.getJSONObject("family")

                val aIn = aObj.optDouble("in", 0.0).toFloat()
                val aOut = aObj.optDouble("out", 0.0).toFloat()
                val aNet = aObj.optDouble("net", (aIn - aOut).toDouble()).toFloat()

                val dIn = dObj.optDouble("in", 0.0).toFloat()
                val dOut = dObj.optDouble("out", 0.0).toFloat()
                val dNet = dObj.optDouble("net", (dIn - dOut).toDouble()).toFloat()

                val fIn = fObj.optDouble("in", 0.0).toFloat()
                val fOut = fObj.optDouble("out", 0.0).toFloat()
                val fNet = fObj.optDouble("net", (fIn - fOut).toDouble()).toFloat()

                requireActivity().runOnUiThread {
                    tvBreakA.text = "In ₹%.0f / Out ₹%.0f".format(aIn, aOut)
                    tvBreakD.text = "In ₹%.0f / Out ₹%.0f".format(dIn, dOut)
                    tvBreakF.text = "In ₹%.0f / Out ₹%.0f".format(fIn, fOut)

                    tvNetA.text = "₹%.0f".format(aNet)
                    tvNetD.text = "₹%.0f".format(dNet)
                    tvNetF.text = "₹%.0f".format(fNet)

                    tvFamily.text = "Family: In ₹%.0f | Out ₹%.0f | Net ₹%.0f".format(fIn, fOut, fNet)

                    renderNetBars(aNet, dNet, fNet)
                    onDone(true)
                }

            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    // show first chars so you can see if it's "OK" or HTML
                    toast("Cashflow parse error: ${e.message}")
                    onDone(false)
                }
            }
        }.start()
    }

    /**
     * Bars around a zero-line in the middle.
     * +ve grows UP from center, -ve grows DOWN from center.
     */
    private fun renderNetBars(netA: Float, netD: Float, netF: Float) {
        graph.post {
            val containerH = graph.height
            if (containerH <= 0) return@post

            // Zero line at center
            val gParams = guideZero.layoutParams as ConstraintLayout.LayoutParams
            gParams.guidePercent = 0.5f
            guideZero.layoutParams = gParams

            val maxAbs = max(max(abs(netA), abs(netD)), abs(netF)).coerceAtLeast(1f)
            val headroom = maxAbs * 0.20f
            val axis = maxAbs + headroom

            val usableHalf = (containerH * 0.42f).toInt() // space for labels
            val minPx = (containerH * 0.05f).toInt().coerceAtLeast(8)

            fun applyBar(bar: View, net: Float) {
                val ratio = (abs(net) / axis).coerceIn(0f, 1f)
                val h = max((usableHalf * ratio).toInt(), minPx)

                val flp = bar.layoutParams as? FrameLayout.LayoutParams
                if (flp != null) {
                    flp.height = h
                    flp.gravity = if (net >= 0f)
                        (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                    else
                        (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    bar.layoutParams = flp
                } else {
                    // fallback: at least set height
                    val lp = bar.layoutParams
                    lp.height = h
                    bar.layoutParams = lp
                }
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

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
