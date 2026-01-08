package com.aravindh.expenselogger

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    companion object {
        // TODO: put your real Apps Script URL here:
        private const val SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycby89w6UX6milK8W3FlS_wwQrctg3a6-j1LnJlAca8hSy1i1tj17f0hcPru4FVZwwjTS/exec"
        private const val REF_THRESHOLD = 15000.0
    }

    // Page containers
    private lateinit var pageForm: View
    private lateinit var pageSummary: View
    private var currentPage = 0  // 0 = form, 1 = summary

    // Gesture detector
    
    private lateinit var gestureDetector: GestureDetector


    // Form views
    private lateinit var etDate: EditText
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

    // Summary views
    private lateinit var tvSummaryMonth: TextView
    private lateinit var btnRefreshSummary: Button
    private lateinit var barRef: View
    private lateinit var tvRefAmount: TextView
    private lateinit var barAravindh: View
    private lateinit var barDeepa: View
    private lateinit var tvAravindhAmount: TextView
    private lateinit var tvDeepaAmount: TextView

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupGestureDetector()
        setupForm()
        setupSummary()

        // Start on form page
        showPage(0)
    }

    // region Binding & setup

    private fun bindViews() {
        pageForm = findViewById(R.id.pageForm)
        pageSummary = findViewById(R.id.pageSummary)

        // Form
        etDate = findViewById(R.id.etDate)
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

        // Summary
        tvSummaryMonth = findViewById(R.id.tvSummaryMonth)
        btnRefreshSummary = findViewById(R.id.btnRefreshSummary)
        barRef = findViewById(R.id.barRef)
        tvRefAmount = findViewById(R.id.tvRefAmount)
        barAravindh = findViewById(R.id.barAravindh)
        barDeepa = findViewById(R.id.barDeepa)
        tvAravindhAmount = findViewById(R.id.tvAravindhAmount)
        tvDeepaAmount = findViewById(R.id.tvDeepaAmount)
    }

    private fun setupGestureDetector() {
    gestureDetector = GestureDetector(this, object : GestureDetector.OnGestureListener {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || e2 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                if (kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                    kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX < 0) showPage(1) else showPage(0)
                    return true
                }
            }
            return false
        }

        override fun onDown(e: MotionEvent?): Boolean = true
        override fun onShowPress(e: MotionEvent?) {}
        override fun onSingleTapUp(e: MotionEvent?): Boolean = false
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean = false

        override fun onLongPress(e: MotionEvent?) {}
    })
}


    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun setupForm() {
        // Date picker
        etDate.setOnClickListener { showDatePicker() }

        btnToday.setOnClickListener {
            etDate.setText(dateFormat.format(Calendar.getInstance().time))
        }

        btnYesterday.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            etDate.setText(dateFormat.format(cal.time))
        }

        // Spinners
        val expenseTypes = resources.getStringArray(R.array.expense_type_array)
        spExpenseType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, expenseTypes)

        spExpenseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val value = parent.getItemAtPosition(position).toString()
                etExpenseTypeOther.visibility =
                    if (value.equals("Other", ignoreCase = true)) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val owners = resources.getStringArray(R.array.owner_array)
        spOwner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, owners)

        spOwner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val value = parent.getItemAtPosition(position).toString()
                etOwnerOther.visibility =
                    if (value.equals("Other", ignoreCase = true)) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Clear errors on typing
        etName.addTextChangedListener { etName.error = null }
        etAmount.addTextChangedListener { etAmount.error = null }
        etExpenseTypeOther.addTextChangedListener { etExpenseTypeOther.error = null }
        etOwnerOther.addTextChangedListener { etOwnerOther.error = null }

        // Submit
        btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun setupSummary() {
        tvRefAmount.text = "₹15,000"

        btnRefreshSummary.setOnClickListener {
            loadMonthlySummary()
        }
    }

    // endregion

    // region Page switching

    private fun showPage(page: Int) {
        currentPage = page
        if (page == 0) {
            pageForm.visibility = View.VISIBLE
            pageSummary.visibility = View.GONE
        } else {
            pageForm.visibility = View.GONE
            pageSummary.visibility = View.VISIBLE
            // Fetch summary when we enter summary page
            loadMonthlySummary()
        }
    }

    // endregion

    // region Form logic

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val dlg = DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = Calendar.getInstance()
                c.set(y, m, d)
                etDate.setText(dateFormat.format(c.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dlg.show()
    }

    private fun validateAndSubmit() {
        val date = etDate.text.toString().trim()
        val name = etName.text.toString().trim()
        val amountStr = etAmount.text.toString().trim()

        if (date.isEmpty()) {
            etDate.error = "Select date"
            return
        }
        if (name.isEmpty()) {
            etName.error = "Enter description"
            return
        }
        if (amountStr.isEmpty()) {
            etAmount.error = "Enter amount"
            return
        }
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            etAmount.error = "Enter valid amount"
            return
        }

        val paymentTypeId = rgPaymentType.checkedRadioButtonId
        if (paymentTypeId == -1) {
            Toast.makeText(this, "Select payment type", Toast.LENGTH_SHORT).show()
            return
        }
        val paymentType = findViewById<RadioButton>(paymentTypeId).text.toString()

        val expenseTypeSpinnerValue = spExpenseType.selectedItem.toString()
        val expenseType =
            if (expenseTypeSpinnerValue.equals("Other", ignoreCase = true)) {
                val other = etExpenseTypeOther.text.toString().trim()
                if (other.isEmpty()) {
                    etExpenseTypeOther.error = "Enter expense type"
                    return
                }
                other
            } else expenseTypeSpinnerValue

        val ownerSpinnerValue = spOwner.selectedItem.toString()
        val expenseOwner =
            if (ownerSpinnerValue.equals("Other", ignoreCase = true)) {
                val other = etOwnerOther.text.toString().trim()
                if (other.isEmpty()) {
                    etOwnerOther.error = "Enter owner"
                    return
                }
                other
            } else ownerSpinnerValue

        val loggedById = rgLoggedBy.checkedRadioButtonId
        if (loggedById == -1) {
            Toast.makeText(this, "Select Logged By", Toast.LENGTH_SHORT).show()
            return
        }
        val loggedBy = findViewById<RadioButton>(loggedById).text.toString()

        sendToSheet(date, name, amount, paymentType, expenseType, expenseOwner, loggedBy)
    }

    private fun clearForm() {
        etName.setText("")
        etAmount.setText("")
        rgPaymentType.clearCheck()
        spExpenseType.setSelection(0)
        etExpenseTypeOther.setText("")
        etExpenseTypeOther.visibility = View.GONE
        spOwner.setSelection(0)
        etOwnerOther.setText("")
        etOwnerOther.visibility = View.GONE
        rgLoggedBy.clearCheck()
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
                        // After saving, if user swipes to summary, they see updated data
                    } else {
                        Toast.makeText(
                            this,
                            "Error: ${response.code}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // endregion

    // region Summary logic

    private fun loadMonthlySummary() {
        Thread {
            try {
                val client = OkHttpClient()
                val url = "$SCRIPT_URL?mode=summary"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    return@Thread
                }

                val json = JSONObject(body)
                if (json.optString("status") != "OK") {
                    return@Thread
                }

                val month = json.optString("month", "")
                val ownerTotals = json.getJSONObject("ownerTotals")
                val aTotal = ownerTotals.optDouble("Aravindh", 0.0)
                val dTotal = ownerTotals.optDouble("Deepa", 0.0)

                runOnUiThread {
                    tvSummaryMonth.text = "Month: $month"
                    updateOwnerGraph(aTotal, dTotal)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun updateOwnerGraph(aTotal: Double, dTotal: Double) {
        tvAravindhAmount.text = "Aravindh: ₹%.0f".format(aTotal)
        tvDeepaAmount.text = "Deepa: ₹%.0f".format(dTotal)

        // Compute max among threshold + both owners
        val maxVal = maxOf(REF_THRESHOLD, aTotal, dTotal, 1.0)

        val refWeight = (REF_THRESHOLD / maxVal * 100).toFloat()
        val aWeight = (aTotal / maxVal * 100).toFloat()
        val dWeight = (dTotal / maxVal * 100).toFloat()

        (barRef.layoutParams as LinearLayout.LayoutParams).apply {
            weight = refWeight
            barRef.layoutParams = this
        }

        (barAravindh.layoutParams as LinearLayout.LayoutParams).apply {
            weight = aWeight
            barAravindh.layoutParams = this
        }

        (barDeepa.layoutParams as LinearLayout.LayoutParams).apply {
            weight = dWeight
            barDeepa.layoutParams = this
        }
    }

    // endregion
}
