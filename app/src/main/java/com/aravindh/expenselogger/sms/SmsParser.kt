package com.aravindh.expenselogger.sms

import java.text.SimpleDateFormat
import java.util.*

object SmsParser {

    data class ParsedSms(
        val amount: Double,
        val merchant: String,
        val date: String,       // yyyy-MM-dd
        val bank: String,
        val paymentType: String,
        val txNature: String,   // Expense / Income / Settlement / Saving
        val ref: String = ""
    )

    // Known bank sender keywords — used to pre-filter SMS
    private val BANK_KEYWORDS = listOf(
        "kotak", "indus", "icici", "sbi", "axis", "hsbc", "yesbank", "yes bank",
        "canara", "hdfc", "kotakb", "indusb"
    )

    fun isFromBank(address: String): Boolean {
        val lower = address.lowercase(Locale.getDefault())
        return BANK_KEYWORDS.any { lower.contains(it) }
    }

    fun parse(sms: String): ParsedSms? {
        return parseKotakNeftCredit(sms)
            ?: parseKotakUpiReceived(sms)
            ?: parseKotakUpiSent(sms)
            ?: parseKotakToAccount(sms)
            ?: parseKotakNach(sms)
            ?: parseIndusInd(sms)
    }

    // ── Kotak: NEFT Credit ──────────────────────────────────────────────────
    // "Rs. 1411 credited to your Kotak Bank a/c XX9632 via NEFT from beneficiary ALTIMETRIK INDIA..."
    private fun parseKotakNeftCredit(sms: String): ParsedSms? {
        val r = Regex(
            """Rs\.?\s*([\d,]+\.?\d*)\s+credited to your Kotak Bank a/c \w+ via (\w+) from beneficiary ([^.]+)""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        val utr = Regex("""UTR Ref\.?\s*(\w+)""", RegexOption.IGNORE_CASE).find(sms)?.groupValues?.get(1) ?: ""
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = m.groupValues[3].trim(),
            date = todayDate(),
            bank = "Kotak",
            paymentType = m.groupValues[2].uppercase(),
            txNature = "Income",
            ref = utr
        )
    }

    // ── Kotak: UPI Received ─────────────────────────────────────────────────
    // "Received Rs.4.00 in your Kotak Bank AC X9632 from canaracashback@upi on 28-03-26."
    private fun parseKotakUpiReceived(sms: String): ParsedSms? {
        val r = Regex(
            """Received Rs\.?\s*([\d,]+\.?\d*) in your Kotak Bank AC \w+ from (\S+@\S+) on (\d{2}-\d{2}-\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        val ref = Regex("""UPI Ref[: ]*(\d+)""", RegexOption.IGNORE_CASE).find(sms)?.groupValues?.get(1) ?: ""
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = vpaToName(m.groupValues[2]),
            date = normalizeDate(m.groupValues[3]),
            bank = "Kotak",
            paymentType = "UPI",
            txNature = "Income",
            ref = ref
        )
    }

    // ── Kotak: UPI Sent ─────────────────────────────────────────────────────
    // "Sent Rs.20000.00 from Kotak Bank AC X9632 to smcglobal.rzp3.brk@validhdfc on 05-03-26."
    private fun parseKotakUpiSent(sms: String): ParsedSms? {
        val r = Regex(
            """Sent Rs\.?\s*([\d,]+\.?\d*) from Kotak Bank AC \w+ to (\S+@\S+) on (\d{2}-\d{2}-\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        val ref = Regex("""UPI Ref[: ]*(\d+)""", RegexOption.IGNORE_CASE).find(sms)?.groupValues?.get(1) ?: ""
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = vpaToName(m.groupValues[2]),
            date = normalizeDate(m.groupValues[3]),
            bank = "Kotak",
            paymentType = "UPI",
            txNature = "Expense",
            ref = ref
        )
    }

    // ── Kotak: Sent to Account (IMPS) ───────────────────────────────────────
    // "Sent Rs.55000.00 from Kotak Bank AC X9632 to X8806 on 02-02-26.UTR:603313214656."
    private fun parseKotakToAccount(sms: String): ParsedSms? {
        val r = Regex(
            """Sent Rs\.?\s*([\d,]+\.?\d*) from Kotak Bank AC \w+ to (X\w+) on (\d{2}-\d{2}-\d{2,4})\.UTR[: ]*(\w+)""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = "A/C ${m.groupValues[2]}",
            date = normalizeDate(m.groupValues[3]),
            bank = "Kotak",
            paymentType = "IMPS",
            txNature = "Settlement",
            ref = m.groupValues[4]
        )
    }

    // ── Kotak: NACH Debit ───────────────────────────────────────────────────
    // "INR 3,500.00 is debited to your Account XXXXXX9632 on 02/02/2026 towards NACH-MUT-NSEClearingLimited Kotak Bank"
    private fun parseKotakNach(sms: String): ParsedSms? {
        val r = Regex(
            """INR\s*([\d,]+\.?\d*) is debited to your Account \w+ on (\d{2}/\d{2}/\d{4}) towards NACH-\w+-(\w+)""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = m.groupValues[3].trim(),
            date = normalizeDate(m.groupValues[2]),
            bank = "Kotak",
            paymentType = "NACH",
            txNature = "Expense"
        )
    }

    // ── IndusInd: Card / UPI spend ──────────────────────────────────────────
    // "INR 118.00 spent on IndusInd Card XX5071 on 30-03-2026 10:10:40 am at UPI SRI VINAYAKA DAILY."
    private fun parseIndusInd(sms: String): ParsedSms? {
        val r = Regex(
            """INR\s*([\d,]+\.?\d*) spent on IndusInd Card \w+ on (\d{2}-\d{2}-\d{4}) [\d:]+\s*(?:am|pm) at (?:UPI )?([^.]+)""",
            RegexOption.IGNORE_CASE
        )
        val m = r.find(sms) ?: return null
        return ParsedSms(
            amount = parseAmount(m.groupValues[1]),
            merchant = m.groupValues[3].trim(),
            date = normalizeDate(m.groupValues[2]),
            bank = "IndusInd",
            paymentType = "Card",
            txNature = "Expense"
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseAmount(raw: String): Double =
        raw.replace(",", "").toDoubleOrNull() ?: 0.0

    // "smcglobal.rzp3.brk@validhdfc" → "Smcglobal"
    // "tangedco@indianbk"            → "Tangedco"
    // "deepavikrish-1@okicici"       → "Deepavikrish"
    fun vpaToName(vpa: String): String {
        val namePart = vpa.substringBefore("@")
        val cleaned = namePart
            .replace(Regex("""\.(?:rzp\w*|brk|cf\w*|validhdfc|okaxis|okicici|okhdfcbank|okbizaxis|ybl|indianbk|upi).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[-.]"""), " ")
            .replace(Regex("""\d+"""), "")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return cleaned.ifBlank { namePart }
    }

    fun normalizeDate(raw: String): String {
        return when {
            raw.matches(Regex("""\d{2}-\d{2}-\d{2}$""")) -> {
                val p = raw.split("-"); "20${p[2]}-${p[1]}-${p[0]}"
            }
            raw.matches(Regex("""\d{2}-\d{2}-\d{4}""")) -> {
                val p = raw.split("-"); "${p[2]}-${p[1]}-${p[0]}"
            }
            raw.matches(Regex("""\d{2}/\d{2}/\d{4}""")) -> {
                val p = raw.split("/"); "${p[2]}-${p[1]}-${p[0]}"
            }
            else -> raw
        }
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
