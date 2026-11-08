package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/owner/dashboard")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','SALESPERSON')")
public class OwnerDashboardController {
 private final JdbcClient db;
 public OwnerDashboardController(JdbcClient db){this.db=db;}

 @GetMapping
 Map<String,Object> dashboard(@AuthenticationPrincipal CurrentUser u,
   @RequestParam(defaultValue="TODAY") String period){
  CustomerController.tenant(u);
  LocalDate today=LocalDate.now();
  LocalDate end="LAST_MONTH".equals(period)?today.withDayOfMonth(1):today.plusDays(1);
  LocalDate start=switch(period){
   case "7_DAYS"->end.minusDays(7);
   case "30_DAYS"->end.minusDays(30);
   case "LAST_MONTH"->end.minusMonths(1);
   default->end.minusDays(1);
  };
  Timestamp startAt=Timestamp.valueOf(start.atStartOfDay());
  Timestamp endAt=Timestamp.valueOf(end.atStartOfDay());

  var result=new LinkedHashMap<String,Object>();
  result.put("period",period);
  result.put("rangeStart",start);
  result.put("rangeEnd",end.minusDays(1));
  result.put("summary",db.sql("""
    select coalesce(sum(total),0) sales,
      coalesce(sum(amount_paid),0) collected,
      coalesce(sum(total-amount_paid),0) outstanding,
      count(*) orders,
      count(*) filter(where delivered_at is null) open_orders
    from amcservice.orders
    where tenant_id=:t and created_at>=:start and created_at<:end
      and fulfillment_status<>'CANCELLED'
    """).param("t",u.tenantId()).param("start",startAt).param("end",endAt)
    .query().singleRow());

  var salesByDay=new HashMap<LocalDate,BigDecimal>();
  db.sql("""
    select created_at::date day,coalesce(sum(total),0) sales
    from amcservice.orders
    where tenant_id=:t and created_at>=:start and created_at<:end
      and fulfillment_status<>'CANCELLED'
    group by created_at::date order by created_at::date
    """).param("t",u.tenantId()).param("start",startAt).param("end",endAt)
    .query().listOfRows().forEach(row->{
      Object raw=row.get("day");
      LocalDate day=raw instanceof LocalDate value?value:((java.sql.Date)raw).toLocalDate();
      salesByDay.put(day,(BigDecimal)row.get("sales"));
    });
  var daily=new ArrayList<Map<String,Object>>();
  for(LocalDate day=start;day.isBefore(end);day=day.plusDays(1))
   daily.add(Map.of("day",day,"sales",salesByDay.getOrDefault(day,BigDecimal.ZERO)));
  result.put("daily",daily);

  result.put("popular",db.sql("""
    select oi.description name,sum(oi.quantity) quantity,
      sum((oi.unit_price-oi.discount)*oi.quantity) revenue
    from amcservice.order_items oi join amcservice.orders o on o.id=oi.order_id
    where o.tenant_id=:t and o.created_at>=:start and o.created_at<:end
      and o.fulfillment_status<>'CANCELLED'
    group by oi.description order by quantity desc,revenue desc limit 5
    """).param("t",u.tenantId()).param("start",startAt).param("end",endAt)
    .query().listOfRows());
  result.put("recentOrders",db.sql("""
    select o.id,o.bill_number,c.name customer_name,o.total,o.amount_paid,
      o.fulfillment_status,o.due_at,o.created_at
    from amcservice.orders o join amcservice.customers c on c.id=o.customer_id
    where o.tenant_id=:t order by o.created_at desc limit 6
    """).param("t",u.tenantId()).query().listOfRows());
  return result;
 }
}
