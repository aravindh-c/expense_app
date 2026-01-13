package com.aravindh.expenselogger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aravindh.expenselogger.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SummaryFragment : Fragment() {

    companion object {
        private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
        private const val REF_THRESHOLD = 15000.0
    }

    private lateinit var tvSummaryMonth: TextView
    private lateinit var btnRefresh: Button
    private lateinit var barRef: View
    private lateinit var tvRefAmount: TextView
    private lateinit var barA: View
    private lateinit var barD: View
    private lateinit var tvA: TextView
    private lateinit var tvD: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvSummaryMonth = view.findViewById(R.id.tvSummaryMonth)
        btnRefresh = view.findViewById(R.id.btnRefreshSummary)
        barRef = view.findViewById(R.id.barRef)
        tvRefAmount = view.findViewById(R.id.tvRefAmount)
        barA = view.findViewById(R.id.barAravindh)
        barD = view.findViewById(R.id.barDeepa)
        tvA = view.findViewById(R.id.tvAravindhAmount)
        tvD = view.findViewById(R.id.tvDeepaAmount)

        tvRefAmount.text = "₹15,000"

        btnRefresh.setOnClickListener { refreshSummary() }
    }

    fun refreshSummary() {
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$SCRIPT_URL?mode=summary").get().build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()

                if (!res.isSuccessful || body.isNullOrEmpty()) return@Thread

                val json = JSONObject(body)
                if (json.optString("status") != "OK") return@Thread

                val month = json.optString("month", "")
                val totals = json.getJSONObject("ownerTotals")
                val aTotal = totals.optDouble("Aravindh", 0.0)
                val dTotal = totals.optDouble("Deepa", 0.0)

                requireActivity().runOnUiThread {
                    tvSummaryMonth.text = "Month: $month"
                    tvA.text = "Aravindh: ₹%.0f".format(aTotal)
                    tvD.text = "Deepa: ₹%.0f".format(dTotal)
                    updateBars(aTotal, dTotal)
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateBars(aTotal: Double, dTotal: Double) {
        val maxVal = maxOf(REF_THRESHOLD, aTotal, dTotal, 1.0)
        val refW = (REF_THRESHOLD / maxVal * 100).toFloat()
        val aW = (aTotal / maxVal * 100).toFloat()
        val dW = (dTotal / maxVal * 100).toFloat()

        (barRef.layoutParams as ViewGroup.LayoutParams).also {
            // barRef is inside a LinearLayout with weight in your layout;
            // ensure it's LinearLayout.LayoutParams in XML.
        }

        val refLp = barRef.layoutParams as android.widget.LinearLayout.LayoutParams
        refLp.weight = refW
        barRef.layoutParams = refLp

        val aLp = barA.layoutParams as android.widget.LinearLayout.LayoutParams
        aLp.weight = aW
        barA.layoutParams = aLp

        val dLp = barD.layoutParams as android.widget.LinearLayout.LayoutParams
        dLp.weight = dW
        barD.layoutParams = dLp
    }
}
