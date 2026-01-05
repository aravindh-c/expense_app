package com.aravindh.expenselogger

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale



class MainActivity : AppCompatActivity() {

    private lateinit var etDate: EditText
    private lateinit var btnPickDate: Button
    private lateinit var btnYesterday: Button
    private lateinit var btnToday: Button
    private lateinit var etName: EditText
    private lateinit var etAmount: EditText
    private lateinit var rgPaymentType: RadioGroup
    private lateinit var spExpenseType: Spinner
    private lateinit var etExpenseTypeOther: EditText
    private lateinit var spOwner: Spinner
    private lateinit var etOwnerOther: EditText
    private lateinit var rgLoggedBy: RadioGroup
    private lateinit var btnSubmit: Button

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // TODO: replace with your deployed Apps Script URL
    private val SCRIPT_URL = "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSpinners()
        setupDateControls()
        setupSubmit()
    }

    private fun bindViews() {
        etDate = findViewById(R.id.etDate)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnYesterday = findViewById(R.id.btnYesterday)
        btnToday = findViewById(R.id.btnToday)
        etName = findViewById(R.id.etName)
        etAmount = findViewById(R.id.etAmount)
        rgPaymentType = findViewById(R.id.rgPaymentType)
        spExpenseType = findViewById(R.id.spExpenseType)
        etExpenseTypeOther = findViewById(R.id.etExpenseTypeOther)
        spOwner = findViewById(R.id.spOwner)
        etOwnerOther = findViewById(R.id.etOwnerOther)
        rgLoggedBy = findViewById(R.id.rgLoggedBy)
        btnSubmit = findViewById(R.id.btnSubmit)
    }

    private fun setupSpinners() {
        ArrayAdapter.createFromResource(
            this,
            R.array.expense_type_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spExpenseType.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.owner_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spOwner.adapter = adapter
        }

        spExpenseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val value = parent.getItemAtPosition(position).toString()
                etExpenseTypeOther.visibility =
                    if (value.equals("Other", ignoreCase = true)) android.view.View.VISIBLE
                    else android.view.View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spOwner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val value = parent.getItemAtPosition(position).toString()
                etOwnerOther.visibility =
                    if (value.equals("Other", ignoreCase = true)) android.view.View.VISIBLE
                    else android.view.View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupDateControls() {
        val cal = Calendar.getInstance()

        fun setDateFromCalendar(year: Int, month: Int, day: Int) {
            cal.set(year, month, day)
            etDate.setText(dateFormat.format(cal.time))
        }

        btnPickDate.setOnClickListener {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                setDateFromCalendar(year, month, dayOfMonth)
            }, y, m, d).show()
        }

        etDate.setOnClickListener {
            btnPickDate.performClick()
        }

        btnToday.setOnClickListener {
            cal.timeInMillis = System.currentTimeMillis()
            etDate.setText(dateFormat.format(cal.time))
        }

        btnYesterday.setOnClickListener {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            etDate.setText(dateFormat.format(cal.time))
        }

        // default to today on launch
        btnToday.performClick()
    }

    private fun setupSubmit() {
        btnSubmit.setOnClickListener {

		val date = etDate.text.toString().trim()
		val name = etName.text.toString().trim()
		val amountStr = etAmount.text.toString().trim()

		// --- Date required ---
		if (date.isEmpty()) {
			etDate.error = "Please select date"
			return@setOnClickListener
		}

		// --- Name required ---
		if (name.isEmpty()) {
			etName.error = "Please enter description"
			return@setOnClickListener
		}

		// --- Amount required + numeric ---
		if (amountStr.isEmpty()) {
			etAmount.error = "Enter amount"
			return@setOnClickListener
		}

		val amount = amountStr.toDoubleOrNull()
		if (amount == null || amount <= 0) {
			etAmount.error = "Enter valid amount"
			return@setOnClickListener
		}

		// --- Payment Type required ---
		val paymentTypeId = rgPaymentType.checkedRadioButtonId
		if (paymentTypeId == -1) {
			Toast.makeText(this, "Select Payment Type", Toast.LENGTH_SHORT).show()
			return@setOnClickListener
		}
		val paymentType = findViewById<RadioButton>(paymentTypeId).text.toString()

		// --- Expense Type required ---
		val expenseTypeSpinnerValue = spExpenseType.selectedItem.toString()
		val expenseType =
			if (expenseTypeSpinnerValue.equals("Other", ignoreCase = true)) {
				val other = etExpenseTypeOther.text.toString().trim()
				if (other.isEmpty()) {
					etExpenseTypeOther.error = "Enter expense type"
					return@setOnClickListener
				}
				other
			} else expenseTypeSpinnerValue

		// --- Expense Owner required ---
		val ownerSpinnerValue = spOwner.selectedItem.toString()
		val expenseOwner =
			if (ownerSpinnerValue.equals("Other", ignoreCase = true)) {
				val other = etOwnerOther.text.toString().trim()
				if (other.isEmpty()) {
					etOwnerOther.error = "Enter owner name"
					return@setOnClickListener
				}
				other
			} else ownerSpinnerValue

		// --- Logged By required ---
		val loggedById = rgLoggedBy.checkedRadioButtonId
		if (loggedById == -1) {
			Toast.makeText(this, "Select Logged By", Toast.LENGTH_SHORT).show()
			return@setOnClickListener
		}
		val loggedBy = findViewById<RadioButton>(loggedById).text.toString()

		// --- All good → submit ---
		sendToSheet(
			date,
			name,
			amount,
			paymentType,
			expenseType,
			expenseOwner,
			loggedBy
		)
	}

    }

    private fun sendToSheet(
    date: String,
    name: String,
    amount: Double,
    paymentType: String,
    expenseType: String,
    expenseOwner: String,
    loggedBy: String
) {
    Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()

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

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(SCRIPT_URL)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful

            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
                    clearForm()
                } else {
                    Toast.makeText(this, "Error: ${response.code}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}


    private fun clearForm() {
        etName.setText("")
        etAmount.setText("")
        spExpenseType.setSelection(0)
        spOwner.setSelection(0)
        rgPaymentType.clearCheck()
        rgLoggedBy.clearCheck()
        etExpenseTypeOther.setText("")
        etOwnerOther.setText("")
        btnToday.performClick()
    }
}
