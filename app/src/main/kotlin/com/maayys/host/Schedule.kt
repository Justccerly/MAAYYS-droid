package com.maayys.host
import android.app.*
import android.content.*
import com.maayys.app.MaaAgentForegroundService
import java.util.Calendar
data class ScheduleEntry(val task:String,val weekdays:Set<Int>,val minutes:Set<Int>,val enabled:Boolean)
object ScheduleStore {
 private const val PREF="schedules"
 fun load(c:Context):MutableList<ScheduleEntry>{val raw=c.getSharedPreferences(PREF,0).getString("entries","")?:"";return raw.split(";").filter{it.count{q->q=='|'}==3}.mapNotNull{p->val x=p.split('|');ScheduleEntry(x[0],x[1].split(',').mapNotNull(String::toIntOrNull).toSet(),x[2].split(',').mapNotNull(String::toIntOrNull).toSet(),x[3]=="1")}.toMutableList()}
 fun save(c:Context,e:List<ScheduleEntry>){c.getSharedPreferences(PREF,0).edit().putString("entries",e.joinToString(";"){ "${it.task}|${it.weekdays.joinToString(",")}|${it.minutes.joinToString(",")}|${if(it.enabled)1 else 0}"}).apply();reschedule(c,e)}
 fun reschedule(c:Context,e:List<ScheduleEntry>){val a=c.getSystemService(AlarmManager::class.java);e.forEachIndexed{idx,x->if(x.enabled)x.weekdays.forEach{d->x.minutes.forEach{m->val i=Intent(c,ScheduleReceiver::class.java).putExtra("task",x.task);val p=PendingIntent.getBroadcast(c,idx*100+d*10+m%10,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=Calendar.getInstance();n.set(Calendar.DAY_OF_WEEK,d);n.set(Calendar.HOUR_OF_DAY,m/60);n.set(Calendar.MINUTE,m%60);n.set(Calendar.SECOND,0);n.set(Calendar.MILLISECOND,0);if(n.timeInMillis<=System.currentTimeMillis())n.add(Calendar.WEEK_OF_YEAR,1);a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,n.timeInMillis,p)}}}}
}
class ScheduleReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val t=i.getStringExtra("task")?:return;val p=c.getSharedPreferences("project",0);if(p.getBoolean("enabled:$t",true))c.startForegroundService(Intent(c,MaaAgentForegroundService::class.java).setAction(MaaAgentForegroundService.ACTION_RUN_TASK).putExtra(MaaAgentForegroundService.EXTRA_TASK,t).putExtra(MaaAgentForegroundService.EXTRA_PARAMS,android.os.Bundle().apply{putString("options",p.getString("options:$t","{}"))}));ScheduleStore.reschedule(c,ScheduleStore.load(c))}}
