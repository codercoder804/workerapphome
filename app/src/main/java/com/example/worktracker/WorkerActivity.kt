package com.example.worktracker

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WorkerActivity : AppCompatActivity() {
    private var wid = 0L
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        wid = intent.getLongExtra("id", 0L)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun tv(text: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.BLACK)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun row(vararg b: Button) = LinearLayout(this).apply { b.forEach { addView(it) } }

    private fun refresh() {
        val w = Store.workers.firstOrNull { it.id == wid } ?: run { finish(); return }
        title = w.name
        box.removeAllViews()
        val open = Store.openShift(wid)

        box.addView(tv(w.name, 22f, true))
        box.addView(tv("Hourly wage: ${Fmt.money(w.wage)}"))

        // Total working time is shown only once at least one shift has BOTH entry and exit.
        if (Store.hasCompletedShift(wid)) {
            val earned = Store.earned(w)
            val paid = Store.paid(wid)
            box.addView(tv("Total worked: ${Fmt.duration(Store.workedMs(wid))}", 17f, true))
            box.addView(tv("Total earned: ${Fmt.money(earned)}"))
            box.addView(tv("Paid: ${Fmt.money(paid)}"))
            box.addView(tv("PENDING PAY: ${Fmt.money(earned - paid)}", 20f, true))
        }

        // Pick any time (past or now) for entry/exit.
        if (open == null) box.addView(row(btn("Add ENTRY time") { addEntry() }))
        else {
            box.addView(tv("Entry: ${Fmt.dateTime(open.login)}  (exit not added yet)"))
            box.addView(row(btn("Add EXIT time") { addExit(open) }))
        }
        box.addView(row(btn("Record payment") { payDialog(w) }))
        box.addView(row(btn("Edit worker") { workerDialog(this, w) { refresh() } },
            btn("Delete worker") { confirmDeleteWorker(w) }))

        box.addView(tv("Entries (tap to edit)", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        val mine = Store.shifts.filter { it.workerId == wid }.sortedByDescending { it.login }
        if (mine.isEmpty()) box.addView(tv("None yet"))
        for (s in mine) {
            val out = s.logout
            val line = if (out == null) "${Fmt.dateTime(s.login)}  →  exit not added"
            else "${Fmt.dateTime(s.login)}  →  ${Fmt.dateTime(out)}  (${Fmt.duration(out - s.login)})"
            box.addView(tv(line).apply { setOnClickListener { editShift(s) } })
        }

        val pays = Store.payments.filter { it.workerId == wid }.sortedByDescending { it.time }
        if (pays.isNotEmpty()) {
            box.addView(tv("Payments (tap to delete)", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
            for (p in pays) box.addView(tv("${Fmt.dateTime(p.time)}   ${Fmt.money(p.amount)}").apply {
                setOnClickListener {
                    AlertDialog.Builder(this@WorkerActivity).setTitle("Delete this payment?")
                        .setPositiveButton("Delete") { _, _ -> Store.payments.remove(p); Store.save(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            })
        }
    }

    private fun addEntry() {
        pickDateTime(this, System.currentTimeMillis()) { t ->
            Store.shifts.add(Shift(Store.nextId(), wid, t, null)); Store.save(); refresh()
        }
    }

    private fun addExit(s: Shift) {
        pickDateTime(this, System.currentTimeMillis()) { t ->
            if (t <= s.login) toast("Exit must be after entry (${Fmt.dateTime(s.login)})")
            else { s.logout = t; Store.save(); refresh() }
        }
    }

    private fun editShift(s: Shift) {
        val items = arrayOf("Change entry time", if (s.logout == null) "Add exit time" else "Change exit time", "Delete entry")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> pickDateTime(this, s.login) { t ->
                    if (s.logout != null && t >= s.logout!!) toast("Entry must be before exit")
                    else { s.login = t; Store.save(); refresh() } }
                1 -> pickDateTime(this, s.logout ?: System.currentTimeMillis()) { t ->
                    if (t <= s.login) toast("Exit must be after entry")
                    else { s.logout = t; Store.save(); refresh() } }
                2 -> { Store.shifts.remove(s); Store.save(); refresh() }
            }
        }.show()
    }

    private fun payDialog(w: Worker) {
        val pending = Store.pending(w)
        val input = EditText(this).apply {
            hint = "Amount paid"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (pending > 0) setText("%.2f".format(pending))
        }
        val holder = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        AlertDialog.Builder(this).setTitle("Record payment").setView(holder)
            .setPositiveButton("Save") { _, _ ->
                val a = input.text.toString().toDoubleOrNull()
                if (a == null || a <= 0) toast("Enter a valid amount")
                else { Store.payments.add(Payment(Store.nextId(), wid, a, System.currentTimeMillis())); Store.save(); refresh() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteWorker(w: Worker) {
        AlertDialog.Builder(this).setTitle("Delete ${w.name}?").setMessage("All entries and payments will be removed.")
            .setPositiveButton("Delete") { _, _ -> Store.deleteWorker(w.id); finish() }
            .setNegativeButton("Cancel", null).show()
    }
}
