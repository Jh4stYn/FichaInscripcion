package pe.unsa.inscripcion.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

class Repository(val helper: Database) {
    private val db get() = helper.writableDatabase
    private val codePattern = Regex("[A-Z0-9_-]{1,30}")
    private val isoDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val amountPattern = Regex("[0-9]{1,9}([.,][0-9]{1,2})?")
    private val collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }

    private fun Cursor.record(module: Module): Record {
        fun text(column: String) = getString(getColumnIndexOrThrow(column))
        return if (module == Module.FICHA) {
            Record(text("code"), text("member_name"), text("state"), text("socio"), text("tipo"),
                text("deporte"), text("date"), getLong(getColumnIndexOrThrow("cents")),
                text("sport_name"), text("type_name"))
        } else Record(text("code"), text("name"), text("state"))
    }

    private fun recordsSql(module: Module) = if (module == Module.FICHA) """
        SELECT r.*, s.name AS member_name, d.name AS sport_name, t.name AS type_name
        FROM fichas r
        JOIN socios s ON s.code = r.socio
        JOIN deportes d ON d.code = r.deporte
        JOIN tipos_socio t ON t.code = r.tipo
    """.trimIndent() else "SELECT r.* FROM ${module.table} r"

    fun get(module: Module, code: String): Record? =
        db.rawQuery("${recordsSql(module)} WHERE r.code = ?", arrayOf(code)).use {
            if (it.moveToFirst()) it.record(module) else null
        }

    fun list(module: Module, query: String = "", state: String? = null, alphabetical: Boolean = false): List<Record> {
        val clause = if (state == null) "" else "WHERE r.state = ?"
        val words = SearchText.normalize(query.trim())
        val result = db.rawQuery("${recordsSql(module)} $clause ORDER BY r.code COLLATE NOCASE",
            state?.let { arrayOf(it) }).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val record = cursor.record(module)
                    if (words.isEmpty() || SearchText.normalize(record.name).contains(words) ||
                        SearchText.normalize(record.code).contains(words)) add(record)
                }
            }
        }
        return if (alphabetical) result.sortedWith { a, b ->
            collator.compare(a.name, b.name).takeUnless { it == 0 } ?: a.code.compareTo(b.code)
        } else result
    }

    fun money(cents: Long) = String.format(Locale.ROOT, "%d,%02d", cents / 100, cents % 100)

    fun save(m: Module, code: String, name: String = "", socio: String = "", tipo: String = "",
             deporte: String = "", date: String = "", amount: String = "", editing: Boolean = false): Record {
        val identifier = code.trim().uppercase(Locale.ROOT)
        if (identifier.isEmpty()) throw DomainError("code", "El código o número es obligatorio.")
        if (!codePattern.matches(identifier)) throw DomainError("code", "Usa hasta 30 letras, números, guion o guion bajo.")
        val displayName = name.trim()
        if (m != Module.FICHA && displayName.length !in 1..100) {
            throw DomainError("name", "El nombre es obligatorio y admite hasta 100 caracteres.")
        }
        try {
            return transaction {
                val old = get(m, identifier)
                if (editing && old == null) throw DomainError("code", "El registro ya no está disponible.")
                if (!editing && old != null) {
                    throw DomainError("code", "El código ya existe. Busca el registro para modificarlo o reactivarlo.")
                }
                if (editing && old?.state != "A") throw DomainError("state", "Solo puedes modificar registros activos.")
                val values = ContentValues().apply {
                    put("code", identifier)
                    put("state", old?.state ?: "A")
                    if (m == Module.FICHA) {
                        validateReferences(socio, tipo, deporte)
                        val cents = parseCents(date, amount)
                        put("socio", socio)
                        put("tipo", tipo)
                        put("deporte", deporte)
                        put("date", date.trim())
                        put("cents", cents)
                    } else {
                        put("name", displayName)
                    }
                }
                if (editing) update(m.table, values, "code = ?", arrayOf(identifier))
                else insertOrThrow(m.table, null, values)
                checkNotNull(get(m, identifier))
            }
        } catch (_: SQLiteConstraintException) {
            throw DomainError("storage", "No se pudo guardar el registro. Revisa sus referencias e inténtalo otra vez.")
        }
    }

    private fun parseCents(date: String, amount: String): Long {
        val typedDate = date.trim()
        if (!isoDate.matches(typedDate)) throw DomainError("date", "Usa el formato AAAA-MM-DD.")
        try {
            LocalDate.parse(typedDate)
        } catch (_: DateTimeParseException) {
            throw DomainError("date", "Ingresa una fecha válida en formato AAAA-MM-DD.")
        }
        val typedAmount = amount.trim()
        if (!amountPattern.matches(typedAmount)) {
            throw DomainError("amount", "Ingresa un monto no negativo con un máximo de dos decimales.")
        }
        return BigDecimal(typedAmount.replace(',', '.')).movePointRight(2).longValueExact()
    }

    private fun validateReferences(socio: String, tipo: String, deporte: String) {
        for ((module, code) in listOf(Module.SOCIO to socio, Module.TIPO to tipo, Module.DEPORTE to deporte)) {
            val label = module.singular.lowercase()
            if (code.isBlank()) throw DomainError(module.name, "Selecciona $label.")
            if (get(module, code)?.state != "A") {
                throw DomainError(module.name, "La referencia $label ($code) debe existir y estar activa.")
            }
        }
    }

    fun changeState(m: Module, code: String, target: String): Record {
        require(target in listOf("A", "I", "*"))
        return transaction {
            val old = get(m, code) ?: throw DomainError("state", "Selecciona un registro existente.")
            if (target == old.state) throw DomainError("state", "El registro ya tiene ese estado.")
            if (target == "I" && old.state != "A") throw DomainError("state", "Solo puedes inactivar registros activos.")
            val column = when (m) {
                Module.SOCIO -> "socio"
                Module.TIPO -> "tipo"
                Module.DEPORTE -> "deporte"
                Module.FICHA -> null
            }
            if (column != null && target != "A") {
                val blockingCode = rawQuery("SELECT code FROM fichas WHERE $column = ? AND state = 'A' ORDER BY code LIMIT 1",
                    arrayOf(code)).use { if (it.moveToFirst()) it.getString(0) else null }
                if (blockingCode != null) {
                    throw DomainError("state", "La ficha activa $blockingCode usa este registro. Inactívala o cambia su referencia.")
                }
            }
            if (m == Module.FICHA && target == "A") validateReferences(old.socio, old.tipo, old.deporte)
            update(m.table, ContentValues().apply { put("state", target) }, "code = ?", arrayOf(code))
            old.copy(state = target)
        }
    }

    private fun <T> transaction(work: SQLiteDatabase.() -> T): T {
        val connection = db
        connection.beginTransaction()
        try {
            val result = connection.work()
            connection.setTransactionSuccessful()
            return result
        } finally {
            connection.endTransaction()
        }
    }

    fun counts() = Module.entries.associateWith { module ->
        db.rawQuery("SELECT count(*) FROM ${module.table} WHERE state = ?", arrayOf("A")).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }
}
