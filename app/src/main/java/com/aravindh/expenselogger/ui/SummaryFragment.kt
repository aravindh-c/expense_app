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
import kotlin.math.max

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    // ✅ SET YOUR DEPLOYED APPS SCRIPT WEB APP URL HERE
    // Example: https://script.google.com/macros/s/XXXX/exec
    private val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"

    private lateinit var spMonth: Spinner
    private lateinit var btnRefresh: Button

    private lateinit var tvFamilyTotal: TextView
    private lateinit var tvAlerts: TextView
    private lateinit var tvLast2: TextView
    private lateinit var tvMonthLabel: TextView

    private lateinit var graphContainer: ConstraintLayout
    private lateinit var guideRef: Guideline
    private lateinit var tvRefAmount: TextView

    private lateinit var barA: View
    private lateinit var barD: View
    private lateinit var tvAAmount: TextView
    private lateinit var tvDAmount: TextView

    private val client = OkHttpClient()
        fun refreshSummary() {
        refreshAll()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spMonth = view.findViewById(R.id.spMonth)
        btnRefresh = view.findViewById(R.id.btnRefreshSummary)

        tvFamilyTotal = view.findViewById(R.id.tvFamilyTotal)
        tvAlerts = view.findViewById(R.id.tvAlerts)
        tvLast2 = view.findViewById(R.id.tvLast2)
        tvMonthLabel = view.findViewById(R.id.tvMonthLabel)

        graphContainer = view.findViewById(R.id.graphContainer)
        guideRef = view.findViewById(R.id.guideRef)
        tvRefAmount = view.findViewById(R.id.tvRefAmount)

        barA = view.findViewById(R.id.barAravindh)
        barD = view.findViewById(R.id.barDeepa)
        tvAAmount = view.findViewById(R.id.tvAravindhAmount)
        tvDAmount = view.findViewById(R.id.tvDeepaAmount)

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

    private fun refreshAll() {
        val monthValue = getSelectedMonthValue()
        if (monthValue.isEmpty()) return

        setMonthLabel(monthValue)
        fetchSummary(monthValue) { ok ->
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
                if (arr != null) {
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                }

                requireActivity().runOnUiThread { onDone(list) }
            } catch (_: Exception) {
                requireActivity().runOnUiThread { onDone(emptyList()) }
            }
        }.start()
    }

    private fun fetchSummary(month: String, onDone: (Boolean) -> Unit) {
        Thread {
            try {
                val url = "$SCRIPT_URL?mode=summary&month=$month"
                val req = Request.Builder().url(url).get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Summary error: ${res.code}", Toast.LENGTH_SHORT).show()
                        onDone(false)
                    }
                    return@Thread
                }

                val json = JSONObject(body)
                val totals = json.getJSONObject("ownerTotals")
                val a = totals.optDouble("Aravindh", 0.0).toFloat()
                val d = totals.optDouble("Deepa", 0.0).toFloat()

                val family = json.optDouble("familyTotal", (a + d).toDouble()).toFloat()
                val limit = json.optDouble("limit", 15000.0).toFloat()

                val alerts = json.optJSONObject("alerts")
                val aAlert = alerts?.optBoolean("Aravindh", false) ?: false
                val dAlert = alerts?.optBoolean("Deepa", false) ?: false

                requireActivity().runOnUiThread {
                    tvFamilyTotal.text = "Family Total: ₹%.0f".format(family)
                    tvAlerts.text = when {
                        aAlert && dAlert -> "⚠️ Both crossed ₹%.0f".format(limit)
                        aAlert -> "⚠️ Aravindh crossed ₹%.0f".format(limit)
                        dAlert -> "⚠️ Deepa crossed ₹%.0f".format(limit)
                        else -> ""
                    }

                    tvAAmount.text = "₹%.0f".format(a)
                    tvDAmount.text = "₹%.0f".format(d)

                    tvRefAmount.text = "Rs.%.0f".format(limit)

                    // update bar heights + ref line once container size is known
                    renderBarsAndRefLine(a, d, limit)

                    onDone(true)
                }

            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Summary parse error", Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }.start()
    }

    private fun renderBarsAndRefLine(a: Float, d: Float, limit: Float) {
    graphContainer.post {
        val containerH = graphContainer.height
        if (containerH <= 0) return@post

        // Keep space for top so ref label never touches edge
        val topPaddingPx = (containerH * 0.08f).toInt()
        val usableH = (containerH - topPaddingPx).coerceAtLeast(120)

        val dataMax = max(a, d)

        // Axis max with headroom (25%) and also at least limit
        var axisMax = max(limit, dataMax) * 1.25f

        // Optional: round axisMax to a nice number (nearest 1000)
        axisMax = ((axisMax + 999) / 1000).toInt() * 1000f

        fun heightFor(value: Float): Int {
            val ratio = (value / axisMax).coerceIn(0f, 1f)
            val minPx = (usableH * 0.05f).toInt().coerceAtLeast(10)
            val h = (usableH * ratio).toInt()
            return max(h, minPx)
        }

        barA.layoutParams = barA.layoutParams.apply { height = heightFor(a) }
        barD.layoutParams = barD.layoutParams.apply { height = heightFor(d) }
        barA.requestLayout()
        barD.requestLayout()

        // Guideline percent is from TOP (0 top, 1 bottom)
        val rawFromBottom = (limit / axisMax).coerceIn(0f, 1f)
        var percentFromTop = 1f - rawFromBottom

        // Clamp so reference line stays inside plot nicely
        percentFromTop = percentFromTop.coerceIn(0.10f, 0.92f)

        val params = guideRef.layoutParams as ConstraintLayout.LayoutParams
        params.guidePercent = percentFromTop
        guideRef.layoutParams = params
        guideRef.requestLayout()
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
                    tvLast2.text = if (lines.isEmpty()) "Last 2: -" else "Last 2:\n" + lines.joinToString("\n")
                }
            } catch (_: Exception) {}
        }.start()
    }
}
