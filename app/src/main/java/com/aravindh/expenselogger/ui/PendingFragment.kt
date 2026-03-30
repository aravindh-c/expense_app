package com.aravindh.expenselogger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aravindh.expenselogger.R
import com.aravindh.expenselogger.db.AppDatabase
import com.aravindh.expenselogger.db.PendingSms
import com.aravindh.expenselogger.sms.SmsScanJob
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PendingFragment : Fragment(R.layout.fragment_pending) {

    companion object {
        private const val SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
        private const val SMS_PERMISSION_CODE = 201
    }

    private lateinit var tvPendingTitle: TextView
    private lateinit var tvLastScanned: TextView
    private lateinit var tvPermissionHint: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvPending: RecyclerView

    private val client = OkHttpClient()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvPendingTitle = view.findViewById(R.id.tvPendingTitle)
        tvLastScanned = view.findViewById(R.id.tvLastScanned)
        tvPermissionHint = view.findViewById(R.id.tvPermissionHint)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        rvPending = view.findViewById(R.id.rvPending)

        rvPending.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<Button>(R.id.btnScanNow).setOnClickListener { scanNow() }

        tvPermissionHint.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), SMS_PERMISSION_CODE)
        }

        checkPermission()
        updateLastScannedLabel()
        refreshList()
    }

    /** Called by MainActivity when user swipes to this tab */
    fun refreshPending() {
        checkPermission()
        updateLastScannedLabel()
        refreshList()
    }

    private fun checkPermission() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        tvPermissionHint.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun scanNow() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), SMS_PERMISSION_CODE)
            return
        }
        Toast.makeText(requireContext(), "Scanning...", Toast.LENGTH_SHORT).show()
        Thread {
            SmsScanJob.scan(requireContext())
            requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putLong("last_scanned", System.currentTimeMillis()).apply()
            requireActivity().runOnUiThread {
                updateLastScannedLabel()
                refreshList()
            }
        }.start()
    }

    private fun refreshList() {
        Thread {
            val items = AppDatabase.get(requireContext()).smsDao().getPending()
            requireActivity().runOnUiThread {
                tvPendingTitle.text =
                    if (items.isEmpty()) "Pending SMS" else "Pending SMS (${items.size})"
                tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                rvPending.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                rvPending.adapter = PendingSmsAdapter(items) { showProcessDialog(it) }
            }
        }.start()
    }

    private fun updateLastScannedLabel() {
        val ts = requireContext()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getLong("last_scanned", 0L)
        tvLastScanned.text = if (ts == 0L) {
            "Last scanned: never"
        } else {
            "Last scanned: ${SimpleDateFormat("dd-MMM hh:mm a", Locale.getDefault()).format(Date(ts))}"
        }
    }

    private fun showProcessDialog(item: PendingSms) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_process_sms, null)

        val tvInfo = dialogView.findViewById<TextView>(R.id.tvDialogInfo)
        val spCategory = dialogView.findViewById<Spinner>(R.id.spCategory)
        val spOwner = dialogView.findViewById<Spinner>(R.id.spOwner)

        tvInfo.text = "₹%.0f  %s  %s\n%s · %s".format(
            item.amount, item.merchant, item.date, item.bank, item.paymentType
        )

        // Category list based on txNature
        val categories = categoriesFor(item.txNature)
        spCategory.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, categories
        )

        // Owner list — pre-select logged-in user
        val owners = resources.getStringArray(R.array.owner_array).toList()
        spOwner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, owners
        )
        val loggedBy = requireContext()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("logged_by", "") ?: ""
        val ownerIdx = owners.indexOfFirst { it.equals(loggedBy, ignoreCase = true) }
        if (ownerIdx >= 0) spOwner.setSelection(ownerIdx)

        AlertDialog.Builder(requireContext())
            .setTitle("Process Transaction")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                submitEntry(
                    item,
                    spCategory.selectedItem?.toString() ?: "",
                    spOwner.selectedItem?.toString() ?: "",
                    loggedBy
                )
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun submitEntry(item: PendingSms, category: String, owner: String, loggedBy: String) {
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("date", item.date)
                    put("name", item.merchant)
                    put("amount", item.amount)
                    put("paymentType", item.paymentType)
                    put("expenseType", category)
                    put("expenseOwner", owner)
                    put("loggedBy", loggedBy)
                    put("txNature", item.txNature)
                    put("bank", item.bank)
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(SCRIPT_URL).post(body).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    AppDatabase.get(requireContext()).smsDao().markProcessed(item.id)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun categoriesFor(txNature: String): List<String> {
        return when (txNature.trim().lowercase()) {
            "income" -> listOf("Salary", "Refund", "Insurance Payout", "Bonus", "Other Income")
            "settlement" -> listOf("Credit Card Bill Payment", "Loan EMI", "Other Settlement")
            "saving" -> listOf("SIP", "RD", "Mutual Fund", "Gold", "Stocks", "Other Saving")
            else -> listOf(
                "Food", "Apparel", "Grocery", "Entertainment", "Travel",
                "Medical", "Health", "Insurance", "School", "Others"
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == SMS_PERMISSION_CODE
            && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            checkPermission()
            scanNow()
        }
    }
}
