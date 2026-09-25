package com.semhas.app.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val oneDecimalFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    private val twoDecimalFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val threeDecimalFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 3
        maximumFractionDigits = 3
    }

    private val rateDecimalFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 3
        maximumFractionDigits = 4
    }

    fun formatCurrency(amount: Double): String {
        return "₹${inrFormat.format(amount)}"
    }

    fun formatRate(rate: Double): String {
        return "₹${rateDecimalFormat.format(rate)}/Wh"
    }

    fun formatPower(watts: Double): String {
        return if (watts >= 1000.0) {
            "${twoDecimalFormat.format(watts / 1000.0)} kW"
        } else {
            "${oneDecimalFormat.format(watts)} W"
        }
    }

    fun formatVoltage(volts: Double): String {
        return "${oneDecimalFormat.format(volts)} V"
    }

    fun formatCurrent(amperes: Double): String {
        return "${twoDecimalFormat.format(amperes)} A"
    }

    fun formatEnergy(wh: Double): String {
        return if (Math.abs(wh) < 0.005) {
            "0 Wh"
        } else {
            "${twoDecimalFormat.format(wh)} Wh"
        }
    }

    fun formatRuntime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
