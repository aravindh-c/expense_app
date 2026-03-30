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
        // ✅ Your deployed /exec URL
        private const val SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ✅ Make these class-level so sendToSheet() can access them
    private lateinit var spTxNature: Spinner
    private lateinit var spExpenseType: Spinner
    private lateinit var spOwner: Spinner

    private lateinit var etExpenseTypeOther: EditText
    private lateinit var etOwnerOther: EditText

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_form, container, false)

    override fun onViewCreated(root: View, savedInstanceState: Bundle?) {
        super.onViewCreated(root, savedInstanceState)

        // --- Bind views from fragment_form.xml ---
        val etDate = root.findViewById<EditText>(R.id.etDate)
        val btnYesterday = root.findViewById<Button>(R.id.btnYesterday)
        val btnToday = root.findViewById<Button>(R.id.btnToday)

        val etName = root.findViewById<EditText>(R.id.etName)
        val etAmount = root.findViewById<EditText>(R.id.etAmount)

        val rgPaymentType = root.findViewById<RadioGroup>(R.id.rgPaymentType)
        val rgLoggedBy = root.findViewById<RadioGroup>(R.id.rgLoggedBy)

        spTxNature = root.findViewById(R.id.spTxNature)
        spExpenseType = root.findViewById(R.id.spExpenseType)
        etExpenseTypeOther = root.findViewById(R.id.etExpenseTypeOther)

        spOwner = root.findViewById(R.id.spOwner)
        etOwnerOther = root.findViewById(R.id.etOwnerOther)

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

        // --- Tx Nature spinner ---
        // IMPORTANT: you MUST have <string-array name="txn_nature_array"> in strings.xml
        val txNatureList = resources.getStringArray(R.array.txn_nature_array)
        spTxNature.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            txNatureList
        )

        // --- Category spinner (dynamic based on tx nature, without extra XML arrays) ---
        fun getCategoriesForNature(nature: String): List<String> {
            val n = nature.trim().lowercase(Locale.getDefault())
            return when (n) {
                "income" -> listOf("Salary", "Investment", "Insurance", "Other")
                "settlement" -> listOf("Credit Card Settlement", "Loan EMI", "Other")
                "saving" -> listOf("Savings", "Investment", "Other")
                else -> listOf(
                    "Food", "Apparel", "Grocery", "Entertainment", "Travel", "Medical",
                    "Health", "Insurance", "School", "Others"
                )
            }
        }

        fun setCategoryAdapter(values: List<String>) {
            spExpenseType.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                values
            )
        }

        // initial category load (based on initial txNature selection)
        setCategoryAdapter(getCategoriesForNature(spTxNature.selectedItem?.toString() ?: "Expense"))

        spTxNature.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val nature = parent.getItemAtPosition(pos).toString()
                setCategoryAdapter(getCategoriesForNature(nature))
                etExpenseTypeOther.visibility = View.GONE
                spExpenseType.setSelection(0)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // show Other field when needed
        spExpenseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = parent.getItemAtPosition(pos).toString().trim()
                etExpenseTypeOther.visibility =
                    if (selected.equals("Other", true) || selected.equals("Others", true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // --- Owner spinner ---
        // IMPORTANT: you MUST have <string-array name="owner_array"> in strings.xml
        val owners = resources.getStringArray(R.array.owner_array)
        spOwner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            owners
        )

        spOwner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val selected = parent.getItemAtPosition(pos).toString()
                etOwnerOther.visibility =
                    if (selected.equals("Other", ignoreCase = true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Pre-select LoggedBy based on stored identity
        val identity = requireContext()
            .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .getString("logged_by", null)
        if (identity != null) {
            for (i in 0 until rgLoggedBy.childCount) {
                val child = rgLoggedBy.getChildAt(i)
                if (child is android.widget.RadioButton && child.text.toString().equals(identity, ignoreCase = true)) {
                    child.isChecked = true
                    break
                }
            }
        }

        // clear errors when typing
        etName.addTextChangedListener { etName.error = null }
        etAmount.addTextChangedListener { etAmount.error = null }
        etExpenseTypeOther.addTextChangedListener { etExpenseTypeOther.error = null }
        etOwnerOther.addTextChangedListener { etOwnerOther.error = null }

        // Helper: works even if RadioButtons don't have IDs
        fun getCheckedRadioText(group: RadioGroup): String? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is RadioButton && child.isChecked) return child.text.toString()
            }
            return null
        }

        // --- Submit ---
        btnSubmit.setOnClickListener {
            val date = etDate.text.toString().trim()
            val name = etName.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (date.isEmpty()) { etDate.error = "Select date"; return@setOnClickListener }
            if (name.isEmpty()) { etName.error = "Enter description"; return@setOnClickListener }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }

            val paymentType = getCheckedRadioText(rgPaymentType)
            if (paymentType.isNullOrBlank()) { toast("Select payment type"); return@setOnClickListener }

            val txNature = spTxNature.selectedItem?.toString()?.trim().orEmpty()

            val catSel = spExpenseType.selectedItem?.toString()?.trim().orEmpty()
            val category =
                if (catSel.equals("Other", true) || catSel.equals("Others", true)) {
                    val other = etExpenseTypeOther.text.toString().trim()
                    if (other.isEmpty()) { etExpenseTypeOther.error = "Enter category"; return@setOnClickListener }
                    other
                } else catSel

            val ownerSel = spOwner.selectedItem?.toString()?.trim().orEmpty()
            val expenseOwner =
                if (ownerSel.equals("Other", true)) {
                    val other = etOwnerOther.text.toString().trim()
                    if (other.isEmpty()) { etOwnerOther.error = "Enter owner"; return@setOnClickListener }
                    other
                } else ownerSel

            val loggedBy = getCheckedRadioText(rgLoggedBy)
            if (loggedBy.isNullOrBlank()) { toast("Select Logged By"); return@setOnClickListener }

            sendToSheet(
                date = date,
                name = name,
                amount = amount,
                paymentType = paymentType,
                category = category,
                ownerPaid = expenseOwner,
                loggedBy = loggedBy,
                txNature = txNature,
                onSuccess = {
                    requireActivity().runOnUiThread {
                        toast("Saved!")
                        etName.setText("")
                        etAmount.setText("")
                        rgPaymentType.clearCheck()
                        rgLoggedBy.clearCheck()
                        spTxNature.setSelection(0)
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
        category: String,
        ownerPaid: String,
        loggedBy: String,
        txNature: String,
        onSuccess: () -> Unit
    ) {
        toast("Sending...")

        Thread {
            try {
                val payload = JSONObject().apply {
                    put("date", date)
                    put("name", name)
                    put("amount", amount)
                    put("paymentType", paymentType)
                    put("expenseType", category)      // keep same key your Apps Script expects
                    put("expenseOwner", ownerPaid)    // "who paid"
                    put("loggedBy", loggedBy)
                    put("txNature", txNature)
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
