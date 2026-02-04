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
                Toast.makeText(requireContext(), "No months found in sheet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Call this from MainActivity when user lands on page 3 */
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

    // ---------------- Months dropdown ----------------

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

    // ---------------- Cashflow fetch + parse (supports BOTH JSON formats) ----------------

    private data class Flow(val income: Float, val out: Float, val net: Float)

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

                // ---- Format A (your current Apps Script):
                // aravindh:{in,out,net}, deepa:{in,out,net}, family:{in,out,net}
                fun parseFormatA(key: String): Flow? {
                    if (!json.has(key)) return null
                    val obj = json.getJSONObject(key)
                    val income = obj.optDouble("in", 0.0).toFloat()
                    val out = obj.optDouble("out", 0.0).toFloat()
                    val net = obj.optDouble("net", (income - out).toDouble()).toFloat()
                    return Flow(income, out, net)
                }

                // ---- Format B (older idea):
                // users:{Aravindh:{income,spend,settlement,saving,net}, ...}, family:{...}
                fun parseFormatBUser(name: String): Flow? {
                    if (!json.has("users")) return null
                    val users = json.getJSONObject("users")
                    if (!users.has(name)) return null
                    val obj = users.getJSONObject(name)
                    val income = obj.optDouble("income", 0.0).toFloat()
                    val spend = obj.optDouble("spend", 0.0).toFloat()
                    val settlement = obj.optDouble("settlement", 0.0).toFloat()
                    val saving = obj.optDouble("saving", 0.0).toFloat()
                    val out = spend + settlement + saving
                    val net = obj.optDouble("net", (income - out).toDouble()).toFloat()
                    return Flow(income, out, net)
                }

                fun parseFormatBFamily(): Flow? {
                    if (!json.has("family")) return null
                    val obj = json.getJSONObject("family")
                    // could be either {in,out,net} OR {income,spend...}
                    val in1 = obj.optDouble("in", Double.NaN)
                    if (!in1.isNaN()) {
                        val income = in1.toFloat()
                        val out = obj.optDouble("out", 0.0).toFloat()
                        val net = obj.optDouble("net", (income - out).toDouble()).toFloat()
                        return Flow(income, out, net)
                    }
                    val income = obj.optDouble("income", 0.0).toFloat()
                    val spend = obj.optDouble("spend", 0.0).toFloat()
                    val settlement = obj.optDouble("settlement", 0.0).toFloat()
                    val saving = obj.optDouble("saving", 0.0).toFloat()
                    val out = spend + settlement + saving
                    val net = obj.optDouble("net", (income - out).toDouble()).toFloat()
                    return Flow(income, out, net)
                }

                val a = parseFormatA("aravindh") ?: parseFormatBUser("Aravindh") ?: Flow(0f, 0f, 0f)
                val d = parseFormatA("deepa") ?: parseFormatBUser("Deepa") ?: Flow(0f, 0f, 0f)
                val f = parseFormatA("family") ?: parseFormatBFamily() ?: Flow(a.income + d.income, a.out + d.out, (a.income + d.income) - (a.out + d.out))

                requireActivity().runOnUiThread {
                    tvBreakA.text = "In ₹%.0f / Out ₹%.0f".format(a.income, a.out)
                    tvBreakD.text = "In ₹%.0f / Out ₹%.0f".format(d.income, d.out)
                    tvBreakF.text = "In ₹%.0f / Out ₹%.0f".format(f.income, f.out)

                    tvNetA.text = "₹%.0f".format(a.net)
                    tvNetD.text = "₹%.0f".format(d.net)
                    tvNetF.text = "₹%.0f".format(f.net)

                    tvFamily.text = "Family: Income ₹%.0f | Out ₹%.0f | Net ₹%.0f".format(f.income, f.out, f.net)

                    renderNetBars(a.net, d.net, f.net)
                    onDone(true)
                }

            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Cashflow parse error", Toast.LENGTH_SHORT).show()
                    onDone(false)
                }
            }
        }.start()
    }

    // ---------------- Bars (safe layoutParams handling) ----------------

    private fun renderNetBars(netA: Float, netD: Float, netF: Float) {
        graph.post {
            val containerH = graph.height
            if (containerH <= 0) return@post

            // keep 0-line mid
            val gp = guideZero.layoutParams as ConstraintLayout.LayoutParams
            gp.guidePercent = 0.5f
            guideZero.layoutParams = gp

            val maxAbs = max(max(abs(netA), abs(netD)), abs(netF)).coerceAtLeast(1f)
            val axis = maxAbs * 1.25f // 25% headroom

            val halfH = (containerH * 0.42f).toInt() // space for top/bottom labels
            val minPx = (containerH * 0.05f).toInt().coerceAtLeast(10)

            fun applyBar(bar: View, net: Float) {
                val ratio = (abs(net) / axis).coerceIn(0f, 1f)
                val h = max((halfH * ratio).toInt(), minPx)

                // height
                val lp = bar.layoutParams
                lp.height = h
                bar.layoutParams = lp

                // If bar is inside FrameLayout, align to TOP/BOTTOM safely.
                val flp = bar.layoutParams
                if (flp is FrameLayout.LayoutParams) {
                    flp.gravity =
                        if (net >= 0f) (android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
                        else (android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
                    bar.layoutParams = flp
                }

                bar.requestLayout()
            }

            applyBar(barA, netA)
            applyBar(barD, netD)
            applyBar(barF, netF)
        }
    }

    // ---------------- Last2 ----------------

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
