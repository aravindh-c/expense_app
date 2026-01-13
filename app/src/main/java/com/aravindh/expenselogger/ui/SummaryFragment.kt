package com.aravindh.expenselogger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.Guideline
import androidx.fragment.app.Fragment
import com.aravindh.expenselogger.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class SummaryFragment : Fragment() {

    companion object {
        private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
        private const val REF_THRESHOLD = 15000.0
    }

    private lateinit var spMonth: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var tvMonthLabel: TextView

    private lateinit var graphContainer: View
    private lateinit var guideRef: Guideline
    private lateinit var barA: View
    private lateinit var barD: View
    private lateinit var tvA: TextView
    private lateinit var tvD: TextView
    private lateinit var tvRef: TextView

    // monthValues = ["2026-01", "2025-12", ...]
    private var monthValues: List<String> = emptyList()
    private var selectedMonth: String? = null

    private val fmtIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val fmtOut = SimpleDateFormat("MMM-yy", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    override fun onViewCreated(root: View, savedInstanceState: Bundle?) {
        spMonth = root.findViewById(R.id.spMonth)
        btnRefresh = root.findViewById(R.id.btnRefreshSummary)
        tvMonthLabel = root.findViewById(R.id.tvMonthLabel)

        graphContainer = root.findViewById(R.id.graphContainer)
        guideRef = root.findViewById(R.id.guideRef)
        barA = root.findViewById(R.id.barAravindh)
        barD = root.findViewById(R.id.barDeepa)
        tvA = root.findViewById(R.id.tvAravindhAmount)
        tvD = root.findViewById(R.id.tvDeepaAmount)
        tvRef = root.findViewById(R.id.tvRefAmount)

        tvRef.text = "Rs.15000"

        btnRefresh.setOnClickListener {
            selectedMonth?.let { fetchSummary(it) }
        }

        fetchMonths()
    }

    fun refreshSummary() {
        // called when user swipes to this page
        fetchMonths()
    }

    private fun fetchMonths() {
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$SCRIPT_URL?mode=months").get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    toast("Months error ${res.code}: $body"); return@Thread
                }

                val json = JSONObject(body)
                if (json.optString("status") != "OK") {
                    toast("Months failed: $body"); return@Thread
                }

                val arr = json.getJSONArray("months")
                val months = mutableListOf<String>()
                for (i in 0 until arr.length()) months.add(arr.getString(i))

                monthValues = months
                val display = months.map { m ->
                    try {
                        val d = fmtIn.parse(m)
                        if (d != null) fmtOut.format(d) else m
                    } catch (e: Exception) { m }
                }

                requireActivity().runOnUiThread {
                    spMonth.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, display)

                    spMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                            selectedMonth = monthValues[pos]
                            tvMonthLabel.text = "Month: ${display[pos]}"
                            fetchSummary(selectedMonth!!)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }

                    // default select first (latest)
                    if (monthValues.isNotEmpty()) {
                        spMonth.setSelection(0)
                    }
                }

            } catch (e: Exception) {
                toast("Months failed: ${e.message}")
            }
        }.start()
    }

    private fun fetchSummary(month: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$SCRIPT_URL?mode=summary&month=$month").get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string().orEmpty()

                if (!res.isSuccessful) {
                    toast("Summary error ${res.code}: $body"); return@Thread
                }

                val json = JSONObject(body)
                if (json.optString("status") != "OK") {
                    toast("Summary failed: $body"); return@Thread
                }

                val totals = json.getJSONObject("ownerTotals")
                val aTotal = totals.optDouble("Aravindh", 0.0)
                val dTotal = totals.optDouble("Deepa", 0.0)

                requireActivity().runOnUiThread {
                    tvA.text = "₹%.0f".format(aTotal)
                    tvD.text = "₹%.0f".format(dTotal)
                    updateVerticalBars(aTotal, dTotal)
                }

            } catch (e: Exception) {
                toast("Summary failed: ${e.message}")
            }
        }.start()
    }

    private fun updateVerticalBars(aTotal: Double, dTotal: Double) {
        val maxVal = maxOf(REF_THRESHOLD, aTotal, dTotal, 1.0)

        // Set reference line position using guideline percent
        // percent is from TOP. If ref is big, line should be higher.
        val refPercentFromTop = (1.0 - (REF_THRESHOLD / maxVal)).toFloat().coerceIn(0f, 1f)
        guideRef.setGuidelinePercent(refPercentFromTop)

        // Update bar heights after layout is measured
        graphContainer.post {
            val h = graphContainer.height
            val aH = (h * (aTotal / maxVal)).toInt().coerceAtLeast(6)
            val dH = (h * (dTotal / maxVal)).toInt().coerceAtLeast(6)

            barA.layoutParams = barA.layoutParams.apply { height = aH }
            barD.layoutParams = barD.layoutParams.apply { height = dH }

            barA.requestLayout()
            barD.requestLayout()
        }
    }

    private fun toast(msg: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }
}
