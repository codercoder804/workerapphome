package com.example.worktracker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Worker(val id: Long, var name: String, var wage: Double)
/** logout == null means the worker is still clocked in. Times are epoch millis. */
data class Shift(val id: Long, val workerId: Long, var login: Long, var logout: Long?)
data class Payment(val id: Long, val workerId: Long, val amount: Double, val time: Long)

object Store {
    private lateinit var prefs: SharedPreferences
    private var seq = 1L
    val workers = mutableListOf<Worker>()
    val shifts = mutableListOf<Shift>()
    val payments = mutableListOf<Payment>()
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        prefs = ctx.applicationContext.getSharedPreferences("worktracker", Context.MODE_PRIVATE)
        load(); loaded = true
    }

    fun nextId() = seq++

    // ---------- calculations ----------
    fun openShift(wid: Long) = shifts.firstOrNull { it.workerId == wid && it.logout == null }

    /** Total worked time. Only shifts with BOTH entry and exit are counted. */
    fun workedMs(wid: Long): Long =
        shifts.filter { it.workerId == wid && it.logout != null }.sumOf { it.logout!! - it.login }

    fun hasCompletedShift(wid: Long) = shifts.any { it.workerId == wid && it.logout != null }

    fun earned(w: Worker) = workedMs(w.id) / 3_600_000.0 * w.wage

    fun paid(wid: Long) = payments.filter { it.workerId == wid }.sumOf { it.amount }

    fun pending(w: Worker) = earned(w) - paid(w.id)

    fun deleteWorker(wid: Long) {
        workers.removeAll { it.id == wid }
        shifts.removeAll { it.workerId == wid }
        payments.removeAll { it.workerId == wid }
        save()
    }

    // ---------- persistence ----------
    fun save() {
        val o = JSONObject().put("seq", seq)
        o.put("workers", JSONArray().also { a -> workers.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("wage", it.wage)) } })
        o.put("shifts", JSONArray().also { a -> shifts.forEach {
            a.put(JSONObject().put("id", it.id).put("wid", it.workerId).put("in", it.login)
                .put("out", it.logout ?: JSONObject.NULL)) } })
        o.put("payments", JSONArray().also { a -> payments.forEach {
            a.put(JSONObject().put("id", it.id).put("wid", it.workerId)
                .put("amt", it.amount).put("t", it.time)) } })
        prefs.edit().putString("data", o.toString()).apply()
    }

    private fun load() {
        val o = JSONObject(prefs.getString("data", "{}")!!)
        seq = o.optLong("seq", 1L)
        o.optJSONArray("workers")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
            workers.add(Worker(it.getLong("id"), it.getString("name"), it.getDouble("wage"))) } }
        o.optJSONArray("shifts")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
            shifts.add(Shift(it.getLong("id"), it.getLong("wid"), it.getLong("in"),
                if (it.isNull("out")) null else it.getLong("out"))) } }
        o.optJSONArray("payments")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
            payments.add(Payment(it.getLong("id"), it.getLong("wid"), it.getDouble("amt"), it.getLong("t"))) } }
    }
}
