package com.aravindh.expenselogger.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.core.widget.addTextChangedListener
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
        private const val SCRIPT_URL = "https://script.google.com/macros/s/XXXXXXXXXXXXXXX/exec"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val btnYesterday = view.findViewById<Button>(R.id.btnYesterday)
        val btnToday = view.findViewById<Button>(R.id.btnToday)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val rgPaymentType = view.findViewById<RadioGroup>(R.id.rgPaymentType)
        val spExpenseType = view.findViewById<Spinner>(R.id.spExpenseType)
        val etExpenseTypeOther = view.findViewById<EditText>(R.id.etExpenseTypeOther)
        val spOwner = view.findViewById<Spinner>(R.id.spOwner)
        val etOwnerOther = view.findViewById<EditText>(R.id.etOwnerOther)
        val rgLoggedBy = view.findViewById<RadioGroup>(R.id.rgLoggedBy)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)

        // Date
        etDate.setOnClickListener { showDatePicker(etDate) }
        btnToday.setOnClickListener { etDate.setText(dateFormat.format(Calendar.getInstance().time)) }
        btnYesterday.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            etDate.setText(dateFormat.format(cal.time))
        }

        // Spinners
        val expenseTypes = resources.getStringArray(R.array.expense_type_array)
        spExpenseType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expenseTypes)
        spExpenseType.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val value = parent.getItemAtPosition(pos).toString()
                etExpenseTypeOther.visibility = if (value.equals("Other", true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val owners = resources.getStringArray(R.array.owner_array)
        spOwner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, owners)
        spOwner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val value = parent.getItemAtPosition(pos).toString()
                etOwnerOther.visibility = if (value.equals("Other", true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Clear errors on typing
        etName.addTextChangedListener { etName.error = null }
        etAmount.addTextChangedListener { etAmount.error = null }

        btnSubmit.setOnClickListener {
            val date = etDate.text.toString().trim()
            val name = etName.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (date.isEmpty()) { etDate.error = "Select date"; return@setOnClickListener }
            if (name.isEmpty()) { etName.error = "Enter description"; return@setOnClickListener }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }

            val paymentId = rgPaymentType.checkedRadioButtonId
            if (paymentId == -1) { toast("Select payment type"); return@setOnClickListener }
            val paymentType = view.findViewById<RadioButton>(paymentId).text.toString()

            val expTypeSel = spExpenseType.selectedItem.toString()
            val expenseType = if (expTypeSel.equals("Other", true)) {
                val other = etExpenseTypeOther.text.toString().trim()
                if (other.isEmpty()) { etExpenseTypeOther.error = "Enter type"; return@setOnClickListener }
                other
            } else expTypeSel

            val ownerSel = spOwner.selectedItem.toString()
            val expenseOwner = if (ownerSel.equals("Other", true)) {
                val other = etOwnerOther.text.toString().trim()
                if (other.isEmpty()) { etOwnerOther.error = "Enter owner"; return@setOnClickListener }
                other
            } else ownerSel

            val loggedId = rgLoggedBy.checkedRadioButtonId
            if (loggedId == -1) { toast("Select Logged By"); return@setOnClickListener }
            val loggedBy = view.findViewById<RadioButton>(loggedId).text.toString()

            sendToSheet(date, name, amount, paymentType, expenseType, expenseOwner, loggedBy) {
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
                val json = JSONObject().apply {
                    put("date", date)
                    put("name", name)
                    put("amount", amount)
                    put("paymentType", paymentType)
                    put("expenseType", expenseType)
                    put("expenseOwner", expenseOwner)
                    put("loggedBy", loggedBy)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url(SCRIPT_URL).post(body).build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) onSuccess() else requireActivity().runOnUiThread { toast("Error: ${res.code}") }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { toast("Failed: ${e.message}") }
            }
        }.start()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
