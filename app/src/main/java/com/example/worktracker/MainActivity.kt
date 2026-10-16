package com.example.worktracker

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        title = "Worker Time Tracker"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        root.addView(Button(this).apply { text = "+ Add worker"; setOnClickListener { workerDialog(this@MainActivity, null) { refresh() } } })
        summary = TextView(this).apply { textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(8)) }
        root.addView(summary)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        list.removeAllViews()
        var totalPending = 0.0
        if (Store.workers.isEmpty()) list.addView(TextView(this).apply { text = "No workers yet. Tap \"+ Add worker\"."; setPadding(0, dp(16), 0, 0) })
        for (w in Store.workers.sortedBy { it.name.lowercase() }) {
            val pending = Store.pending(w)
            totalPending += pending
            var t = w.name
            if (Store.openShift(w.id) != null) t += "\nEntry added, exit pending"
            if (Store.hasCompletedShift(w.id)) t += "\nWorked: ${Fmt.duration(Store.workedMs(w.id))}   Pending pay: ${Fmt.money(pending)}"
            val row = TextView(this).apply {
                text = t
                textSize = 15f; setTextColor(Color.BLACK); setBackgroundColor(Color.parseColor("#EEEEEE"))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, dp(10)) }
                setOnClickListener { startActivity(Intent(this@MainActivity, WorkerActivity::class.java).putExtra("id", w.id)) }
            }
            list.addView(row)
        }
        summary.text = "Total pending pay: ${Fmt.money(totalPending)}"
    }
}
