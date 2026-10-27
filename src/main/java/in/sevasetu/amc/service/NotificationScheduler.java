package in.sevasetu.amc.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationScheduler {
 private final JdbcClient db;
 public NotificationScheduler(JdbcClient db){this.db=db;}

 @Scheduled(cron="${app.notification-cron}")
 @Transactional
 public void buildOutbox(){
  db.sql("""
   insert into amcservice.notification_jobs(id,tenant_id,customer_id,kind,scheduled_for,message,idempotency_key)
   select gen_random_uuid(),c.tenant_id,c.id,'BIRTHDAY',now(),
          concat('Birthday reminder for ',c.name,' on ',to_char(d.event_date,'DD Mon')),
          concat(c.tenant_id,':birthday:',c.id,':',extract(year from d.event_date))
   from amcservice.customers c
   join amcservice.tenants t on t.id=c.tenant_id and t.domain='BAKERY'
   join amcservice.tenant_automation_settings s on s.tenant_id=c.tenant_id and s.automatic_notifications
   cross join lateral (
     select min(day)::date event_date
     from generate_series(current_date,current_date+s.purchase_reminder_days,interval '1 day') day
     where to_char(day,'MM-DD')=to_char(c.dob,'MM-DD')
   ) d
   where c.dob is not null and d.event_date is not null
   on conflict(idempotency_key) do nothing
   """).update();

  db.sql("""
   insert into amcservice.notification_jobs(id,tenant_id,customer_id,kind,scheduled_for,message,idempotency_key)
   select gen_random_uuid(),c.tenant_id,c.id,'ANNIVERSARY',now(),
          concat('Anniversary reminder for ',c.name,' on ',to_char(d.event_date,'DD Mon')),
          concat(c.tenant_id,':anniversary:',c.id,':',extract(year from d.event_date))
   from amcservice.customers c
   join amcservice.tenants t on t.id=c.tenant_id and t.domain='BAKERY'
   join amcservice.tenant_automation_settings s on s.tenant_id=c.tenant_id and s.automatic_notifications
   cross join lateral (
     select min(day)::date event_date
     from generate_series(current_date,current_date+s.purchase_reminder_days,interval '1 day') day
     where to_char(day,'MM-DD')=to_char(c.anniversary,'MM-DD')
   ) d
   where c.anniversary is not null and d.event_date is not null
   on conflict(idempotency_key) do nothing
   """).update();

  db.sql("""
   insert into amcservice.notification_jobs(id,tenant_id,customer_id,order_item_id,kind,scheduled_for,message,idempotency_key)
   select gen_random_uuid(),o.tenant_id,o.customer_id,oi.id,'EXPIRY',now(),
          concat(oi.description,' expires on ',oi.service_expires_on),
          concat(o.tenant_id,':expiry:',oi.id)
   from amcservice.order_items oi
   join amcservice.orders o on o.id=oi.order_id
   join amcservice.tenant_automation_settings s on s.tenant_id=o.tenant_id and s.automatic_notifications
   where oi.service_expires_on between current_date and current_date+s.purchase_reminder_days
   on conflict(idempotency_key) do nothing
   """).update();
 }
}
