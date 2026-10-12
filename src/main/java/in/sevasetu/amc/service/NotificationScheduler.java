package in.sevasetu.amc.service;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class NotificationScheduler{private final JdbcClient db;public NotificationScheduler(JdbcClient db){this.db=db;}
 @Scheduled(cron="${app.notification-cron}") @Transactional public void buildOutbox(){db.sql("""
 insert into amcservice.notification_jobs(id,tenant_id,customer_id,kind,scheduled_for,message,idempotency_key)
 select gen_random_uuid(),c.tenant_id,c.id,'BIRTHDAY',now(),concat('A birthday is coming up for ',c.name),concat(c.tenant_id,':birthday:',c.id,':',extract(year from now()))
 from amcservice.customers c where c.dob is not null and to_char(c.dob,'MM-DD')=to_char(current_date+interval '1 month','MM-DD')
 on conflict(idempotency_key) do nothing
 """).update();db.sql("""
 insert into amcservice.notification_jobs(id,tenant_id,customer_id,order_item_id,kind,scheduled_for,message,idempotency_key)
 select gen_random_uuid(),o.tenant_id,o.customer_id,oi.id,'EXPIRY',now(),concat(oi.description,' expires on ',oi.service_expires_on),concat(o.tenant_id,':expiry:',oi.id)
 from amcservice.order_items oi join amcservice.orders o on o.id=oi.order_id where oi.service_expires_on between current_date and current_date+interval '30 days'
 on conflict(idempotency_key) do nothing
 """).update();}}
