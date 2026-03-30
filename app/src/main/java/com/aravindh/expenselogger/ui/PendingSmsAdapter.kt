package com.aravindh.expenselogger.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aravindh.expenselogger.R
import com.aravindh.expenselogger.db.PendingSms

class PendingSmsAdapter(
    private val items: List<PendingSms>,
    private val onItemClick: (PendingSms) -> Unit
) : RecyclerView.Adapter<PendingSmsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMerchant: TextView = view.findViewById(R.id.tvMerchant)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvBankType: TextView = view.findViewById(R.id.tvBankType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_sms, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvMerchant.text = item.merchant
        holder.tvAmount.text = "₹%.0f".format(item.amount)
        holder.tvDate.text = item.date
        holder.tvBankType.text = "${item.bank} · ${item.paymentType}"
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
