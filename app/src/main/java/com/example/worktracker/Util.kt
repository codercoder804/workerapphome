package com.example.worktracker

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

object Fmt {
    // HH = 24-hour clock (23:11, never 11:11 PM)
    private val dt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    fun dateTime(ms: Long): String = dt.format(Date(ms))
    fun duration(ms: Long): String { val m = ms / 60000; return "%dh %02dm".format(m / 60, m % 60) }
    fun money(v: Double): String = "%.2f".format(v)
}

/** Date picker followed by a 24-hour time picker. */
fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, mo, d ->
        TimePickerDialog(ctx, { _, h, mi ->
            val r = Calendar.getInstance().apply {
                set(y, mo, d, h, mi, 0); set(Calendar.MILLISECOND, 0)
            }
            onPicked(r.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show() // true = 24-hour
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

private fun field(ctx: Context, hint: String, type: Int, text: String = "") = EditText(ctx).apply {
    this.hint = hint; inputType = type; setText(text)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

/** Add (existing == null) or edit a worker. */
fun workerDialog(ctx: Context, existing: Worker?, onDone: () -> Unit) {
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0) }
    val name = field(ctx, "Worker name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, existing?.name ?: "")
    val wage = field(ctx, "Hourly wage", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        existing?.wage?.toString() ?: "")
    box.addView(name); box.addView(wage)
    AlertDialog.Builder(ctx).setTitle(if (existing == null) "Add worker" else "Edit worker").setView(box)
        .setPositiveButton("Save") { _, _ ->
            val n = name.text.toString().trim()
            val w = wage.text.toString().toDoubleOrNull()
            if (n.isEmpty() || w == null || w < 0) { ctx.toast("Enter a valid name and wage"); return@setPositiveButton }
            if (existing == null) Store.workers.add(Worker(Store.nextId(), n, w))
            else { existing.name = n; existing.wage = w }
            Store.save(); onDone()
        }.setNegativeButton("Cancel", null).show()
}
