package com.aravindh.expenselogger.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.aravindh.expenselogger.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FormFragment : Fragment() {

    companion object {
        // TODO: replace with your /exec URL
        private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_form, container, false)
    }

    override fun onViewCreated(root: View, savedInstanceState: Bundle?) {
        // --- Bind views from fragment_form.xml ---
        val etDate = root.findViewById<EditText>(R.id.etDate)
        val btnYesterday = root.findViewById<Button>(R.id.btnYesterday)
        val btnToday = root.findViewById<Button>(R.id.btnToday)

        val etName = root.findViewById<EditText>(R.id.etName)
        val etAmount = root.findViewById<EditText>(R.id.etAmount)

        val rgPaymentType = root.findViewById<RadioGroup>(R.id.rgPaymentType)
        val spExpenseType = root.findViewById<Spinner>(R.id.spExpenseType)
        val etExpenseTypeOther = root.findViewById<EditText>(R.id.etExpenseTypeOther)

        val spOwner = root.findViewById<Spinner>(R.id.spOwner)
        val etOwnerOther = root.findViewById<EditText>(R.id.etOwnerOther)

        val rgLoggedBy = root.findViewById<RadioGroup>(R.id.rgLoggedBy)
        val btnSubmit = root.findViewById<Button>(R.id.btnSubmit)

        // --- Date picker + quick buttons ---
        etDate.setOnClickListener { showDatePicker(etDate) }

        btnToday.setOnClickListener {
            etDate.setText(dateFormat.format(Calendar.getInstance().time))
        }

        btnYesterday.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            etDate.setText(dateFormat.format(cal.time))
        }

        // --- Expense Type spinner ---
        val expenseTypes = resources.getStringArray(R.array.expense_type_array)
        spExpenseType.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expenseTypes)

        spExpenseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = parent.getItemAtPosition(pos).toString()
                etExpenseTypeOther.visibility =
                    if (selected.equals("Other", ignoreCase = true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // --- Owner spinner ---
        val owners = resources.getStringArray(R.array.owner_array)
        spOwner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, owners)

        spOwner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = parent.getItemAtPosition(pos).toString()
                etOwnerOther.visibility =
                    if (selected.equals("Other", ignoreCase = true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // clear errors when typing
        etName.addTextChangedListener { etName.error = null }
        etAmount.addTextChangedListener { etAmount.error = null }
        etExpenseTypeOther.addTextChangedListener { etExpenseTypeOther.error = null }
        etOwnerOther.addTextChangedListener { etOwnerOther.error = null }

        // --- Submit ---
        btnSubmit.setOnClickListener {
            val date = etDate.text.toString().trim()
            val name = etName.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (date.isEmpty()) { etDate.error = "Select date"; return@setOnClickListener }
            if (name.isEmpty()) { etName.error = "Enter description"; return@setOnClickListener }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }

            val payId = rgPaymentType.checkedRadioButtonId
            if (payId == -1) { toast("Select payment type"); return@setOnClickListener }
            val paymentType = root.findViewById<RadioButton>(payId).text.toString()

            val expTypeSel = spExpenseType.selectedItem.toString()
            val expenseType =
                if (expTypeSel.equals("Other", true)) {
                    val other = etExpenseTypeOther.text.toString().trim()
                    if (other.isEmpty()) { etExpenseTypeOther.error = "Enter expense type"; return@setOnClickListener }
                    other
                } else expTypeSel

            val ownerSel = spOwner.selectedItem.toString()
            val expenseOwner =
                if (ownerSel.equals("Other", true)) {
                    val other = etOwnerOther.text.toString().trim()
                    if (other.isEmpty()) { etOwnerOther.error = "Enter owner"; return@setOnClickListener }
                    other
                } else ownerSel

            val loggedId = rgLoggedBy.checkedRadioButtonId
            if (loggedId == -1) { toast("Select Logged By"); return@setOnClickListener }
            val loggedBy = root.findViewById<RadioButton>(loggedId).text.toString()

            sendToSheet(
                date = date,
                name = name,
                amount = amount,
                paymentType = paymentType,
                expenseType = expenseType,
                expenseOwner = expenseOwner,
                loggedBy = loggedBy,
                onSuccess = {
                    requireActivity().runOnUiThread {
                        toast("Saved!")
                        etName.setText("")
                        etAmount.setText("")
                        rgPaymentType.clearCheck()
                        rgLoggedBy.clearCheck()
                        spExpenseType.setSelection(0)
                        spOwner.setSelection(0)
                        etExpenseTypeOther.setText("")
                        etOwnerOther.setText("")
                        etExpenseTypeOther.visibility = View.GONE
                        etOwnerOther.visibility = View.GONE
                    }
                }
            )
        }
    }

    private fun showDatePicker(etDate: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val c = Calendar.getInstance()
                c.set(y, m, d)
                etDate.setText(dateFormat.format(c.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun sendToSheet(
        date: String,
        name: String,
        amount: Double,
        paymentType: String,
        expenseType: String,
        expenseOwner: String,
        loggedBy: String,
        onSuccess: () -> Unit
    ) {
        toast("Sending...")

        Thread {
            try {
                val client = OkHttpClient()
                val payload = JSONObject().apply {
                    put("date", date)
                    put("name", name)
                    put("amount", amount)
                    put("paymentType", paymentType)
                    put("expenseType", expenseType)
                    put("expenseOwner", expenseOwner)
                    put("loggedBy", loggedBy)
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(SCRIPT_URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    requireActivity().runOnUiThread {
                        toast("Error ${response.code}: $responseBody")
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    toast("Failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
