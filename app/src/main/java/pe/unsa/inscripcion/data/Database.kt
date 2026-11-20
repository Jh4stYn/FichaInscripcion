package pe.unsa.inscripcion.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import pe.unsa.inscripcion.R

class Database(context: Context, name: String = "inscripciones.db"): SQLiteOpenHelper(context.applicationContext, name, null, 1) {
 private val resources = context.applicationContext.resources

 override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
 override fun onCreate(db: SQLiteDatabase) {
  for (m in listOf(Module.SOCIO, Module.TIPO, Module.DEPORTE)) {
   db.execSQL("""CREATE TABLE ${m.table} (
    code TEXT PRIMARY KEY NOT NULL CHECK(length(trim(code)) BETWEEN 1 AND 30),
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 100),
    state TEXT NOT NULL DEFAULT 'A' CHECK(state IN ('A','I','*')))""")
  }
  db.execSQL("""CREATE TABLE fichas (
   code TEXT PRIMARY KEY NOT NULL CHECK(length(trim(code)) BETWEEN 1 AND 30),
   socio TEXT NOT NULL REFERENCES socios(code) ON UPDATE RESTRICT ON DELETE RESTRICT,
   tipo TEXT NOT NULL REFERENCES tipos_socio(code) ON UPDATE RESTRICT ON DELETE RESTRICT,
   deporte TEXT NOT NULL REFERENCES deportes(code) ON UPDATE RESTRICT ON DELETE RESTRICT,
   date TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents >= 0),
   state TEXT NOT NULL DEFAULT 'A' CHECK(state IN ('A','I','*')))""")
  for (c in listOf("socio", "tipo", "deporte")) db.execSQL("CREATE INDEX idx_fichas_$c ON fichas($c, state)")
  insertInitialRecords(db)
 }

 private fun insertInitialRecords(db: SQLiteDatabase) {
  val initialRecords = resources.openRawResource(R.raw.datos_iniciales).bufferedReader(Charsets.UTF_8)
   .use { JSONObject(it.readText()) }
  for (module in listOf(Module.SOCIO, Module.TIPO, Module.DEPORTE, Module.FICHA)) {
   val rows = initialRecords.getJSONArray(module.table)
   for (index in 0 until rows.length()) {
    val row = rows.getJSONObject(index)
    val values = ContentValues().apply {
     put("code", row.getString("code"))
     put("state", row.getString("state"))
     if (module == Module.FICHA) {
      put("socio", row.getString("socio"))
      put("tipo", row.getString("tipo"))
      put("deporte", row.getString("deporte"))
      put("date", row.getString("date"))
      put("cents", row.getLong("cents"))
     } else {
      put("name", row.getString("name"))
     }
    }
    db.insertOrThrow(module.table, null, values)
   }
  }
 }

 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  // Never drop user data as a migration strategy. Add explicit migrations before increasing version.
  throw IllegalStateException("Se requiere una migración explícita de $oldVersion a $newVersion")
 }
}
