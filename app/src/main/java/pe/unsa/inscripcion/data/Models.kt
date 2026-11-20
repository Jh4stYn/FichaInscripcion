package pe.unsa.inscripcion.data

import java.text.Normalizer
import java.util.Locale

enum class Module(val table: String, val title: String, val singular: String) {
 FICHA("fichas", "Inscripciones", "Ficha de Inscripción"),
 SOCIO("socios", "Socios", "Socio"),
 TIPO("tipos_socio", "Tipos de socio", "Tipo de socio"),
 DEPORTE("deportes", "Deportes", "Tipo de deporte");
 companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: FICHA }
}
data class Record(val code: String, val name: String, val state: String = "A",
 val socio: String = "", val tipo: String = "", val deporte: String = "", val date: String = "", val cents: Long = 0L, val sportName: String = "", val typeName: String = "")
class DomainError(val field: String, override val message: String): IllegalArgumentException(message)
enum class EditMode { VIEW, ADD, EDIT }

internal object SearchText {
    private val accents = Regex("\\p{M}+")

    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(accents, "").lowercase(Locale.ROOT)
}
