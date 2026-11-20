package pe.unsa.inscripcion

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import pe.unsa.inscripcion.data.Module
import pe.unsa.inscripcion.ui.*

/** Optional real-screen demonstration. Run separately from the acceptance suite. */
@RunWith(AndroidJUnit4::class)
class DemoTest {
 private val i=InstrumentationRegistry.getInstrumentation()
 private fun main(f:()->Unit){i.runOnMainSync(f);i.waitForIdleSync();Thread.sleep(900)}
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun description(a:Activity,value:String){main{all(a.window.decorView).first{it.contentDescription?.toString()?.startsWith(value)==true}.performClick()}}
 private fun button(a:Activity,id:Int){main{a.findViewById<View>(id).performClick()}}
 private fun dialog(value:String){
  val automation=i.uiAutomation
  automation.takeScreenshot()?.recycle()
  var selected:AccessibilityNodeInfo?=null
  for(attempt in 0..49){
   selected=automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(value)?.firstOrNull{it.text?.toString()?.equals(value,true)==true}
   if(selected!=null)break
   Thread.sleep(100)
  }
  check(selected!=null){"La confirmación no apareció: $value"}
  val bounds=android.graphics.Rect()
  selected!!.getBoundsInScreen(bounds)
  val now=android.os.SystemClock.uptimeMillis()
  for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
   val tap=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),
    action,bounds.exactCenterX(),bounds.exactCenterY(),0)
   tap.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
   try { check(automation.injectInputEvent(tap,true)) } finally { tap.recycle() }
  }
  i.waitForIdleSync();Thread.sleep(900)
 }
 @Test fun recorrido_completo(){
  val c=i.targetContext
  val home=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1800)
  val demoCodes=mapOf(Module.TIPO to "97",Module.SOCIO to "S097",Module.DEPORTE to "D097",Module.FICHA to "F0997")
  for(m in listOf(Module.TIPO,Module.SOCIO,Module.DEPORTE,Module.FICHA)){
   val a=i.startActivitySync(Intent(c,MaintenanceActivity::class.java).putExtra("module",m.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as MaintenanceActivity
   Thread.sleep(1100);button(a,R.id.add)
   main{a.findViewById<EditText>(R.id.code).setText(demoCodes.getValue(m));if(m!=Module.FICHA)a.findViewById<EditText>(R.id.name).setText(when(m){Module.TIPO->"Socio visitante";Module.SOCIO->"Carlos Medina";else->"Atletismo"})}
   if(m==Module.FICHA){
    for(ref in listOf(Module.SOCIO,Module.TIPO,Module.DEPORTE)){
     val monitor=i.addMonitor(ReferenceActivity::class.java.name,null,false)
     description(a,"Consultar ${ref.singular}")
     val s=i.waitForMonitorWithTimeout(monitor,4000)as ReferenceActivity
     main{s.findViewById<EditText>(R.id.search).setText(demoCodes.getValue(ref))}
     main{
      val rows=s.findViewById<ListView>(R.id.records)
      check(rows.count==1)
      rows.performItemClick(rows.getChildAt(0),0,rows.adapter.getItemId(0))
     }
     check(s.isFinishing)
     i.removeMonitor(monitor)
    }
    main{a.findViewById<EditText>(R.id.date).setText("2026-11-19");a.findViewById<EditText>(R.id.amount).setText("80,00")}
   }
   main{a.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN)}
   button(a,R.id.save);button(a,R.id.edit)
   main{a.findViewById<EditText>(if(m==Module.FICHA)R.id.amount else R.id.name).setText(if(m==Module.FICHA)"85,00" else when(m){Module.TIPO->"Visitante deportivo";Module.SOCIO->"Carlos Medina Rojas";else->"Atletismo libre"})}
   main{a.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN)};button(a,R.id.save)
   button(a,R.id.more);dialog("Inactivar");button(a,R.id.reactivate);button(a,R.id.more);dialog("Eliminar…");dialog("Eliminar");button(a,R.id.reactivate)
   main{a.onBackPressed();a.findViewById<EditText>(R.id.search).setText(demoCodes.getValue(m))}
   main{val rows=a.findViewById<ListView>(R.id.records);check(rows.count==1);rows.performItemClick(rows.getChildAt(0),0,rows.adapter.getItemId(0))}
   main{a.onBackPressed()}
   button(a,R.id.add);main{a.findViewById<EditText>(R.id.code).setText("DESCARTAR")};main{a.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN)};button(a,R.id.cancel);dialog("Descartar");button(a,R.id.back)
   Thread.sleep(900)
  }
  i.runOnMainSync{home.finish()}
 }
}
