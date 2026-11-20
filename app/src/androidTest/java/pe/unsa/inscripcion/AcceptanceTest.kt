package pe.unsa.inscripcion

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pe.unsa.inscripcion.data.*
import pe.unsa.inscripcion.ui.*
import java.io.File
import java.text.Collator
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AcceptanceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private lateinit var repo:Repository
 private val launched=mutableListOf<Activity>()
 @Before fun setup(){ context.deleteDatabase("inscripciones.db");repo=Repository(Database(context));repo.counts() }
 @After fun cleanup(){ main{launched.reversed().forEach{if(!it.isFinishing)it.finish()}};instrumentation.waitForIdleSync();repo.helper.close() }
 private fun main(f:()->Unit){instrumentation.runOnMainSync(f);instrumentation.waitForIdleSync()}
 private fun launch(m:Module, openFirst:Boolean=true):MaintenanceActivity {
  val a=instrumentation.startActivitySync(Intent(context,MaintenanceActivity::class.java).putExtra("module",m.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MaintenanceActivity
  launched.add(a);instrumentation.waitForIdleSync();if(openFirst) tapRow(a, repo.list(m).first().code);return a
 }
 private fun add(m:Module,code:String="T001")=if(m==Module.FICHA)repo.save(m,code,socio="S001",tipo="2",deporte="D001",date="2026-11-16",amount="80,00") else repo.save(m,code,"Registro de prueba")
 private fun bad(field:String?=null,f:()->Unit){try{f();fail("Debió rechazar la operación")}catch(e:DomainError){if(field!=null)assertEquals(field,e.field)}}
 private fun shot(name:String){
  if (InstrumentationRegistry.getArguments().getString("capturas")=="false") return
  instrumentation.waitForIdleSync();Thread.sleep(400)
  val img=instrumentation.uiAutomation.takeScreenshot()?:error("No se obtuvo captura")
  val file=File(context.getExternalFilesDir(null),"evidencias/$name.png");file.parentFile!!.mkdirs()
  file.outputStream().use{img.compress(Bitmap.CompressFormat.PNG,100,it)};img.recycle()
 }
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))} else emptyList()
 private fun clickDescription(a:Activity,description:String){main{val v=all(a.window.decorView).firstOrNull{it.contentDescription?.toString()?.startsWith(description)==true}?:error("No se encontró $description");assertTrue(v.isEnabled);v.performClick()};instrumentation.waitForIdleSync()}
 private fun tapRow(activity:Activity, code:String){
  val list=activity.findViewById<ListView>(R.id.records)
  var index=-1
  main{
   assertTrue(list.isShown)
   index=(0 until list.count).first { position ->
    val row=list.adapter.getView(position,null,list)
    all(row).filterIsInstance<TextView>().any {
     val text=it.text.toString()
     text==code || text.startsWith("$code ·")
    }
   }
   list.setSelection(index)
  }
  main{
   val row=list.getChildAt(index-list.firstVisiblePosition) ?: error("Registro no visible: $code")
   assertTrue(list.performItemClick(row,index,list.adapter.getItemId(index)))
  }
 }
 private fun press(activity:Activity,id:Int){main{
  val button=activity.findViewById<View>(id)
  assertTrue(button.isShown)
  assertTrue(button.isEnabled)
  assertTrue(button.performClick())
 }}
 private fun clickText(a:Activity,text:String){main{val b=all(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()==text};b.performClick()};instrumentation.waitForIdleSync()}
 private fun dialogClick(text:String){
  repeat(35) {
   val node=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
    ?.firstOrNull{it.text?.toString()?.equals(text,true)==true}
   if(node?.isVisibleToUser==true){
    val bounds=android.graphics.Rect()
    node.getBoundsInScreen(bounds)
    val now=android.os.SystemClock.uptimeMillis()
    for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
     val tap=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),
      action,bounds.exactCenterX(),bounds.exactCenterY(),0)
     tap.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
     try { assertTrue(instrumentation.uiAutomation.injectInputEvent(tap,true)) } finally { tap.recycle() }
    }
    instrumentation.waitForIdleSync()
    return
   }
   Thread.sleep(80)
  }
  error("No apareció el control: $text")
 }
 @Test fun CP01_adicionar_y_guardar_en_cuatro_tablas(){for(m in Module.entries){val r=add(m);assertEquals("A",r.state);assertEquals(r,repo.get(m,"T001"))}}
 @Test fun CP02_identificadores_duplicados(){for(m in Module.entries){add(m);bad("code"){add(m)};assertEquals(1,repo.list(m).count{it.code=="T001"})}}
 @Test fun CP03_modificar_conserva_identificador(){for(m in Module.entries){add(m);val r=if(m==Module.FICHA)repo.save(m,"T001",socio="S002",tipo="1",deporte="D002",date="2026-11-17",amount="95,50",editing=true) else repo.save(m,"T001","Nombre corregido",editing=true);assertEquals("T001",r.code);assertEquals("A",r.state);if(m==Module.FICHA)assertEquals(9550L,r.cents)else assertEquals("Nombre corregido",r.name)}}
 @Test fun CP04_inactivar_en_cuatro_tablas(){for(m in Module.entries){add(m);assertEquals("I",repo.changeState(m,"T001","I").state);assertTrue(repo.list(m).any{it.code=="T001"})}}
 @Test fun CP05_eliminacion_logica_conserva_fila(){for(m in Module.entries){add(m);assertEquals("*",repo.changeState(m,"T001","*").state);assertNotNull(repo.get(m,"T001"))}}
 @Test fun CP06_reactivar_desde_inactivo_y_eliminado(){for(m in Module.entries){add(m);for(s in listOf("I","*")){repo.changeState(m,"T001",s);assertEquals("A",repo.changeState(m,"T001","A").state)}}}
 @Test fun CP07_busqueda_por_codigo_y_nombre(){for(m in Module.entries){add(m);assertEquals("T001",repo.list(m,"t001").single().code);assertTrue(repo.list(m,if(m==Module.FICHA)"ana" else "prueba").any{it.code=="T001"})}}
 @Test fun CP08_cancelar_adicion_y_modificacion() {
  for (m in Module.entries) {
   val a=launch(m,false)
   val original=repo.list(m).first()
   val count=repo.list(m).size
   press(a,R.id.add)
   main{a.findViewById<EditText>(R.id.code).setText("NUEVO")}
   press(a,R.id.cancel)
   dialogClick("Descartar")
   assertEquals(count,repo.list(m).size)
   tapRow(a,original.code)
   press(a,R.id.edit)
   main{a.findViewById<EditText>(if(m==Module.FICHA)R.id.amount else R.id.name).setText(if(m==Module.FICHA)"150" else "Descartar")}
   press(a,R.id.cancel)
   dialogClick("Descartar")
   assertEquals(original,repo.get(m,original.code))
   main{a.finish()}
  }
 }
 @Test fun CP09_salir_solicita_confirmacion_solo_con_cambios(){
  val clean=launch(Module.SOCIO,false);main{clean.onBackPressed()};assertTrue(clean.isFinishing)
  val a=launch(Module.SOCIO,false);main{a.beginAdd();a.findViewById<EditText>(R.id.code).setText("S099");a.onBackPressed()};assertFalse(a.isFinishing);shot("17_confirmacion_cambios");main{a.discardDialog!!.getButton(-2).performClick()};assertFalse(a.isFinishing);main{a.onBackPressed();a.discardDialog!!.getButton(-1).performClick()};assertFalse(a.isFinishing);assertTrue(a.findViewById<View>(R.id.records).isShown);main{a.onBackPressed()};assertTrue(a.isFinishing);assertNull(repo.get(Module.SOCIO,"S099"))
 }
 @Test fun CP10_tres_selectores_solo_activos_ordenados(){
  val collator=Collator.getInstance(Locale.forLanguageTag("es")).apply{strength=Collator.PRIMARY}
  for(m in listOf(Module.SOCIO,Module.TIPO,Module.DEPORTE)){val records=repo.list(m,state="A",alphabetical=true);assertTrue(records.all{it.state=="A"});assertTrue(records.zipWithNext().all{collator.compare(it.first.name,it.second.name)<=0})}
  val a=launch(Module.FICHA);main{a.beginEdit()}
  for((i,m)in listOf(Module.SOCIO,Module.TIPO,Module.DEPORTE).withIndex()){
   val monitor=instrumentation.addMonitor(ReferenceActivity::class.java.name,null,false);clickDescription(a,"Consultar ${m.singular}");val selector=instrumentation.waitForMonitorWithTimeout(monitor,4000) as? ReferenceActivity?:error("No abrió la consulta")
   launched.add(selector);shot("0${7+i}_selector_${m.name.lowercase()}");val labels=all(selector.window.decorView).filterIsInstance<TextView>().map{it.text.toString()};assertFalse(labels.any{it=="S004"||it=="S005"});clickText(selector,"Cancelar");instrumentation.removeMonitor(monitor)
  }
 }
 @Test fun CP11_seleccionar_y_cancelar_consulta_conserva_valor() {
  val ficha=launch(Module.FICHA)
  press(ficha,R.id.edit)

  val selection=instrumentation.addMonitor(ReferenceActivity::class.java.name,null,false)
  clickDescription(ficha,"Consultar Socio")
  val selector=instrumentation.waitForMonitorWithTimeout(selection,5000) as ReferenceActivity
  launched.add(selector)
  tapRow(selector,"S002")
  assertTrue(selector.isFinishing)
  assertEquals("S001",repo.get(Module.FICHA,"F0001")!!.socio)
  press(ficha,R.id.save)
  assertEquals("S002",repo.get(Module.FICHA,"F0001")!!.socio)
  instrumentation.removeMonitor(selection)

  press(ficha,R.id.edit)
  val cancellation=instrumentation.addMonitor(ReferenceActivity::class.java.name,null,false)
  clickDescription(ficha,"Consultar Socio")
  val cancelled=instrumentation.waitForMonitorWithTimeout(cancellation,5000) as ReferenceActivity
  launched.add(cancelled)
  main {
   val search=cancelled.findViewById<EditText>(R.id.search)
   val list=cancelled.findViewById<ListView>(R.id.records)
   search.setText("S002")
   assertEquals(1,list.count)
   val currentRow=list.adapter.getView(0,null,list)
   assertTrue(all(currentRow).filterIsInstance<TextView>().any {
    it.text.toString()=="S002 · Selección actual"
   })
   search.setText("S003")
   assertEquals(1,list.count)
  }
  clickText(cancelled,"Cancelar")
  press(ficha,R.id.save)
  assertEquals("S002",repo.get(Module.FICHA,"F0001")!!.socio)
  instrumentation.removeMonitor(cancellation)
 }

 @Test fun CP12_referencias_obligatorias(){for(field in listOf("SOCIO","TIPO","DEPORTE")){bad(field){repo.save(Module.FICHA,"F0099",socio=if(field=="SOCIO")""else "S001",tipo=if(field=="TIPO")""else "2",deporte=if(field=="DEPORTE")""else "D001",date="2026-11-16",amount="80")}};assertNull(repo.get(Module.FICHA,"F0099"))}
 @Test fun CP13_bloquear_estado_de_referencia_con_ficha_activa(){for((m,c)in listOf(Module.SOCIO to "S001",Module.TIPO to "2",Module.DEPORTE to "D001")){for(state in listOf("I","*")){bad("state"){repo.changeState(m,c,state)};assertEquals("A",repo.get(m,c)!!.state)}};val a=launch(Module.SOCIO);main{a.selectCode("S001");a.changeState("I")};assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any{it.isShown && it.text.toString().contains("ficha activa F0001")});shot("13_integridad_referencial")}
 @Test fun CP14_fecha_y_montos_invalidos(){
  bad("date"){repo.save(Module.FICHA,"F0099",socio="S001",tipo="2",deporte="D001",date="2026-02-31",amount="80")}
  for(amount in listOf("-1","80,001","abc")){bad("amount"){repo.save(Module.FICHA,"F0099",socio="S001",tipo="2",deporte="D001",date="2026-11-16",amount=amount)}}
  assertNull(repo.get(Module.FICHA,"F0099"));val a=launch(Module.FICHA);main{a.beginEdit();a.findViewById<EditText>(R.id.amount).setText("80,001");a.save()}
  val message=a.findViewById<TextView>(R.id.amount_error)
  assertTrue(message.isShown)
  assertTrue(message.text.toString().contains("dos decimales"))
  shot("14_validacion_monto")
  main { a.findViewById<EditText>(R.id.amount).setText("80,00") }
  assertFalse(message.isShown)
 }
 @Test fun CP15_persistencia_despues_de_reabrir_base(){for(m in Module.entries){add(m);repo.changeState(m,"T001","I")};repo.helper.close();repo=Repository(Database(context));for(m in Module.entries)assertEquals("I",repo.get(m,"T001")!!.state)}
 @Test fun CP16_reactivacion_ficha_exige_referencias_activas(){repo.changeState(Module.FICHA,"F0001","I");repo.changeState(Module.SOCIO,"S001","I");bad("SOCIO"){repo.changeState(Module.FICHA,"F0001","A")};assertEquals("I",repo.get(Module.FICHA,"F0001")!!.state)}
 @Test fun CP17_datos_iniciales_del_emulador() {
  assertEquals(listOf("F0001","F0015"),repo.list(Module.FICHA).map { it.code })
  val first=repo.get(Module.FICHA,"F0001")!!
  assertEquals(listOf("S001","2","D001","2026-11-16","A"),
   listOf(first.socio,first.tipo,first.deporte,first.date,first.state))
  assertEquals(8000L,first.cents)
  val second=repo.get(Module.FICHA,"F0015")!!
  assertEquals(listOf("S015","5","D015","2026-11-19","A"),
   listOf(second.socio,second.tipo,second.deporte,second.date,second.state))
  assertEquals(8500L,second.cents)
  assertEquals("I",repo.get(Module.SOCIO,"S004")!!.state)
  assertEquals("*",repo.get(Module.SOCIO,"S005")!!.state)

  val activity=launch(Module.FICHA,false)
  tapRow(activity,"F0015")
  assertEquals("F0015",activity.selected?.code)
  assertEquals("Carlos Medina Rojas",activity.selected?.name)
  main {
   val labels=all(activity.window.decorView).filterIsInstance<TextView>()
    .filter { it.isShown }.map { it.text.toString() }
   assertTrue(labels.contains("5 · Visitante deportivo"))
   assertTrue(labels.contains("D015 · Atletismo libre"))
   assertTrue(labels.contains("S/ 85,00"))
  }
 }
 @Test fun CP18_datos_obligatorios_vacios(){for(m in Module.entries){for(c in listOf("","   "))bad("code"){add(m,c)};if(m!=Module.FICHA)for(n in listOf("","   "))bad("name"){repo.save(m,"T002",n)}}}
 @Test fun CP19_consulta_sin_activos(){repo.list(Module.FICHA,state="A").forEach{repo.changeState(Module.FICHA,it.code,"I")};repo.list(Module.SOCIO,state="A").forEach{repo.changeState(Module.SOCIO,it.code,"I")};assertTrue(repo.list(Module.SOCIO,state="A").isEmpty());val a=instrumentation.startActivitySync(Intent(context,ReferenceActivity::class.java).putExtra("module","SOCIO").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ReferenceActivity;launched.add(a);shot("16_selector_vacio");assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any{it.text.toString()=="No hay registros activos disponibles."});clickText(a,"Cancelar")}
 @Test fun CP20_cancelar_confirmacion_eliminar(){val a=launch(Module.SOCIO);main{a.onBackPressed()};tapRow(a,"S002");press(a,R.id.more);dialogClick("Eliminar…");shot("15_confirmacion_eliminar");dialogClick("Cancelar");assertEquals("A",repo.get(Module.SOCIO,"S002")!!.state)}
 @Test fun CP21_orden_y_seleccion_registros_inactivos_eliminados(){for(m in Module.entries){val codes=repo.list(m).map{it.code};assertEquals(codes.sorted(),codes)};val a=launch(Module.SOCIO);main{a.selectCode("S004");assertTrue(a.findViewById<View>(R.id.reactivate).isEnabled);a.changeState("A")};assertEquals("A",repo.get(Module.SOCIO,"S004")!!.state);main{a.selectCode("S005");a.changeState("A")};assertEquals("A",repo.get(Module.SOCIO,"S005")!!.state)}
 @Test fun EVIDENCIAS_recorrido_integrado_y_estados(){
  val home=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));launched.add(home);shot("01_inicio")
  for((i,m)in listOf(Module.SOCIO,Module.TIPO,Module.DEPORTE).withIndex()){
   val a=launch(m,false)
   shot("0${2+i}_catalogo_${m.name.lowercase()}")
   main{a.finish()}
  }
  val ficha=launch(Module.FICHA,false)
  shot("05_inscripciones")
  press(ficha,R.id.add)
  shot("06_nueva_inscripcion")
  press(ficha,R.id.cancel)
  tapRow(ficha,"F0001")
  shot("06b_ficha_detalle")
  press(ficha,R.id.edit)
  main{ficha.findViewById<EditText>(R.id.amount).setText("95,50")}
  press(ficha,R.id.save)
  assertEquals(9550L,ficha.selected!!.cents)
  shot("10_guardado")
  press(ficha,R.id.more);dialogClick("Inactivar");shot("11_estado_inactivo")
  press(ficha,R.id.more);dialogClick("Eliminar…");dialogClick("Eliminar");shot("12_estado_eliminado")
  press(ficha,R.id.reactivate);shot("12b_reactivado")
  main{ficha.finish()}
  val reopened=launch(Module.FICHA)
  assertEquals(9550L,reopened.selected!!.cents)
  shot("18_reapertura")
 }

 @Test fun UX01_listado_filtra_y_abre_detalle_por_toque() {
  val a=launch(Module.SOCIO,false)
  val add=a.findViewById<View>(R.id.add)
  val bounds=android.graphics.Rect()
  assertTrue(add.getGlobalVisibleRect(bounds))
  assertEquals(add.height,bounds.height())
  main{
   a.findViewById<EditText>(R.id.search).setText("s004")
   a.findViewById<Spinner>(R.id.filter).setSelection(1)
  }
  assertEquals(0,a.findViewById<ListView>(R.id.records).count)
  main{a.findViewById<Spinner>(R.id.filter).setSelection(2)}
  tapRow(a,"S004")
  assertEquals("I",a.selected!!.state)
  assertTrue(a.findViewById<View>(R.id.reactivate).isShown)
  main{a.onBackPressed()}
  assertEquals("s004",a.findViewById<EditText>(R.id.search).text.toString())
  assertEquals(2,a.findViewById<Spinner>(R.id.filter).selectedItemPosition)
 }

 @Test fun UX02_recrear_formulario_conserva_borrador_y_filtro() {
  val intent=Intent(context,MaintenanceActivity::class.java).putExtra("module","SOCIO")
  androidx.test.core.app.ActivityScenario.launch<MaintenanceActivity>(intent).use { scenario ->
   scenario.onActivity { a ->
    a.findViewById<EditText>(R.id.search).setText("prueba")
    a.findViewById<Spinner>(R.id.filter).setSelection(2)
    a.findViewById<View>(R.id.add).performClick()
    a.findViewById<EditText>(R.id.code).setText("S099")
    a.findViewById<EditText>(R.id.name).setText("Borrador original")
   }
   scenario.recreate()
   scenario.onActivity { a ->
    assertEquals("S099",a.findViewById<EditText>(R.id.code).text.toString())
    assertEquals("Borrador original",a.findViewById<EditText>(R.id.name).text.toString())
    assertTrue(a.hasChanges())
    val save=a.findViewById<View>(R.id.save)
    assertEquals(R.id.footer,(save.parent as View).id)
    assertTrue(save.getGlobalVisibleRect(android.graphics.Rect()))
    a.findViewById<View>(R.id.cancel).performClick()
    a.discardDialog!!.getButton(-1).performClick()
    assertEquals("prueba",a.findViewById<EditText>(R.id.search).text.toString())
    assertEquals(2,a.findViewById<Spinner>(R.id.filter).selectedItemPosition)
   }
  }
  assertNull(repo.get(Module.SOCIO,"S099"))
 }

 @Test fun UX03_busqueda_sin_coincidencias_y_selector_conservan_estado() {
  val intent=Intent(context,ReferenceActivity::class.java).putExtra("module","SOCIO")
  androidx.test.core.app.ActivityScenario.launch<ReferenceActivity>(intent).use { scenario ->
   scenario.onActivity { a ->
    val search=a.findViewById<EditText>(R.id.search)
    search.setText("sin coincidencias xyz")
    assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any {
     it.isShown && it.text.toString()=="No hay coincidencias con la búsqueda."
    })
    search.setText("s002")
   }
   scenario.recreate()
   scenario.onActivity { a ->
    assertEquals("s002",a.findViewById<EditText>(R.id.search).text.toString())
    assertEquals(1,a.findViewById<ListView>(R.id.records).count)
   }
  }
 }

 @Test fun UX04_inicio_abre_formulario_nuevo_y_guarda_ficha_integrada() {
  val home=instrumentation.startActivitySync(Intent(context,MainActivity::class.java)
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  launched.add(home)
  val newScreen=instrumentation.addMonitor(MaintenanceActivity::class.java.name,null,false)
  clickText(home,"+ Nueva inscripción")
  val form=instrumentation.waitForMonitorWithTimeout(newScreen,5000) as MaintenanceActivity
  instrumentation.removeMonitor(newScreen)
  launched.add(form)
  main{form.findViewById<EditText>(R.id.code).setText("F0998")}
  press(form,R.id.save)
  assertTrue(all(form.window.decorView).filterIsInstance<TextView>().any {
   it.isShown && it.text.toString()=="Selecciona socio."
  })
  val choices=mapOf(Module.SOCIO to "S002",Module.TIPO to "1",Module.DEPORTE to "D002")
  for((module,record) in choices){
   val monitor=instrumentation.addMonitor(ReferenceActivity::class.java.name,null,false)
   clickDescription(form,"Consultar ${module.singular}")
   val selector=instrumentation.waitForMonitorWithTimeout(monitor,5000) as ReferenceActivity
   launched.add(selector)
   tapRow(selector,record)
   assertTrue(selector.isFinishing)
   instrumentation.removeMonitor(monitor)
  }
  main{
   form.findViewById<EditText>(R.id.date).setText("2026-11-19")
   form.findViewById<EditText>(R.id.amount).setText("125,50")
  }
  press(form,R.id.save)
  val saved=repo.get(Module.FICHA,"F0998")!!
  assertEquals("S002",saved.socio)
  assertEquals("1",saved.tipo)
  assertEquals("D002",saved.deporte)
  assertEquals("2026-11-19",saved.date)
  assertEquals(12550L,saved.cents)
  assertEquals("F0998",form.selected?.code)
 }
}
