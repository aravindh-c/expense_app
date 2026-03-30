package com.aravindh.expenselogger.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FormFragment()
            1 -> SummaryFragment()
            2 -> CashflowFragment()
            else -> PendingFragment()
        }
    }
}
