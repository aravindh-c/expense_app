package com.aravindh.expenselogger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aravindh.expenselogger.ui.PagerAdapter
import com.aravindh.expenselogger.ui.SummaryFragment

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        val adapter = PagerAdapter(this)
        viewPager.adapter = adapter

        // Refresh summary whenever user lands on page-2
        viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1) {
                    val frag = supportFragmentManager.findFragmentByTag("f1")
                    if (frag is SummaryFragment) {
                        frag.refreshSummary()
                    }
                }
            }
        })
    }
}
