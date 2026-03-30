package com.aravindh.expenselogger

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aravindh.expenselogger.sms.AlarmScheduler
import com.aravindh.expenselogger.ui.CashflowFragment
import com.aravindh.expenselogger.ui.PagerAdapter
import com.aravindh.expenselogger.ui.PendingFragment
import com.aravindh.expenselogger.ui.SummaryFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = PagerAdapter(this)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val tag = "f$position"
                val frag = supportFragmentManager.findFragmentByTag(tag)
                when (position) {
                    1 -> (frag as? SummaryFragment)?.refreshSummary()
                    2 -> (frag as? CashflowFragment)?.refreshCashflow()
                    3 -> (frag as? PendingFragment)?.refreshPending()
                }
            }
        })

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getString("logged_by", null) == null) {
            showIdentityDialog()
        } else {
            AlarmScheduler.schedule(this)
        }
    }

    private fun showIdentityDialog() {
        val names = arrayOf("Aravindh", "Deepa")
        AlertDialog.Builder(this)
            .setTitle("Who is using this app?")
            .setItems(names) { _, which ->
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("logged_by", names[which])
                    .apply()
                AlarmScheduler.schedule(this)
            }
            .setCancelable(false)
            .show()
    }
}
