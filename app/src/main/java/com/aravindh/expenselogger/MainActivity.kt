package com.aravindh.expenselogger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aravindh.expenselogger.ui.CashflowFragment
import com.aravindh.expenselogger.ui.PagerAdapter
import com.aravindh.expenselogger.ui.SummaryFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = PagerAdapter(this)

        viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // ViewPager2 fragment tags follow pattern "f0", "f1", "f2"...
                val tag = "f$position"
                val frag = supportFragmentManager.findFragmentByTag(tag)

                when (position) {
                    1 -> (frag as? SummaryFragment)?.refreshSummary()
                    2 -> (frag as? CashflowFragment)?.refreshCashflow()
                }
            }
        })
    }
}
