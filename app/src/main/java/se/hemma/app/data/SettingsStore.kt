package se.hemma.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sparar inställningar och maskiner lokalt i telefonen. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("hemma", Context.MODE_PRIVATE)

    fun loadSettings(): PriceSettings {
        val d = PriceSettings()
        return PriceSettings(
            area = prefs.getString(K_AREA, d.area) ?: d.area,
            supplierMarkupOre = dbl(K_MARKUP, d.supplierMarkupOre),
            energyTaxOre = dbl(K_TAX, d.energyTaxOre),
            gridFeeOre = dbl(K_GRID, d.gridFeeOre),
            vatPercent = dbl(K_VAT, d.vatPercent),
            includeFees = prefs.getBoolean(K_FEES, d.includeFees),
            cheapHoursCount = prefs.getInt(K_CHEAP, d.cheapHoursCount),
        )
    }

    fun saveSettings(s: PriceSettings) {
        prefs.edit()
            .putString(K_AREA, s.area)
            .putString(K_MARKUP, s.supplierMarkupOre.toString())
            .putString(K_TAX, s.energyTaxOre.toString())
            .putString(K_GRID, s.gridFeeOre.toString())
            .putString(K_VAT, s.vatPercent.toString())
            .putBoolean(K_FEES, s.includeFees)
            .putInt(K_CHEAP, s.cheapHoursCount)
            .apply()
    }

    fun loadMachines(): List<Machine> {
        val json = prefs.getString(K_MACHINES, null) ?: return Defaults.machines()
        return runCatching { machinesFromJson(json) }.getOrElse { Defaults.machines() }
    }

    fun saveMachines(list: List<Machine>) {
        prefs.edit().putString(K_MACHINES, machinesToJson(list)).apply()
    }

    private fun dbl(key: String, def: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull() ?: def

    companion object {
        private const val K_AREA = "area"
        private const val K_MARKUP = "markupOre"
        private const val K_TAX = "energyTaxOre"
        private const val K_GRID = "gridFeeOre"
        private const val K_VAT = "vatPercent"
        private const val K_FEES = "includeFees"
        private const val K_CHEAP = "cheapHours"
        private const val K_MACHINES = "machines"

        fun machinesToJson(list: List<Machine>): String {
            val arr = JSONArray()
            for (m in list) {
                val o = JSONObject()
                o.put("id", m.id)
                o.put("name", m.name)
                o.put("type", m.type.name)
                o.put("model", m.model)
                o.put("energyKwh", m.energyKwh)
                o.put("durationMinutes", m.durationMinutes)
                m.powerKw?.let { o.put("powerKw", it) }
                m.readyByHour?.let { o.put("readyByHour", it) }
                o.put("showOnWidget", m.showOnWidget)
                arr.put(o)
            }
            return arr.toString()
        }

        fun machinesFromJson(json: String): List<Machine> {
            val arr = JSONArray(json)
            val out = ArrayList<Machine>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Machine(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = runCatching { MachineType.valueOf(o.getString("type")) }.getOrDefault(MachineType.OTHER),
                        model = o.optString("model", ""),
                        energyKwh = o.getDouble("energyKwh"),
                        durationMinutes = o.optInt("durationMinutes", 60),
                        powerKw = if (o.has("powerKw")) o.getDouble("powerKw") else null,
                        readyByHour = if (o.has("readyByHour")) o.getInt("readyByHour") else null,
                        showOnWidget = o.optBoolean("showOnWidget", true),
                    ),
                )
            }
            return out
        }
    }
}
