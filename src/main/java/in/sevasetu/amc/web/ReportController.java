package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ReportController {
 private final JdbcClient db;
 public ReportController(JdbcClient db){this.db=db;}
 private record Range(LocalDate start,LocalDate end){}

 @GetMapping
 Map<String,Object> report(@AuthenticationPrincipal CurrentUser u,@RequestParam(defaultValue="MONTH") String period){
  CustomerController.tenant(u);
  var selected=range(period);
  var monthStart=LocalDate.now().withDayOfMonth(1);
  var currentMonth=new Range(monthStart,monthStart.plusMonths(1));
  var result=new LinkedHashMap<String,Object>();
  result.put("period",period);
  result.put("rangeStart",selected.start());
  result.put("rangeEnd",selected.end().minusDays(1));
  result.put("currentMonth",summary(u,currentMonth));
  result.put("selected",summary(u,selected));
  result.put("salesByType",db.sql("""
   select coalesce(ci.type,case when oi.item_kind='PACKAGE' then 'PACKAGE' else 'PRODUCT' end) sale_type,
          sum(oi.quantity) quantity,sum((oi.unit_price-oi.discount)*oi.quantity) revenue,count(distinct o.id) orders
   from amcservice.order_items oi
   join amcservice.orders o on o.id=oi.order_id
   left join amcservice.catalog_items ci on ci.id=oi.item_id and oi.item_kind='ITEM'
   where o.tenant_id=:t and o.created_at>=:start and o.created_at<:end
   group by sale_type order by revenue desc
   """).param("t",u.tenantId()).param("start",selected.start()).param("end",selected.end()).query().listOfRows());
  result.put("items",db.sql("""
   select oi.description,coalesce(ci.type,case when oi.item_kind='PACKAGE' then 'PACKAGE' else 'PRODUCT' end) sale_type,
          sum(oi.quantity) quantity,sum((oi.unit_price-oi.discount)*oi.quantity) revenue,count(distinct o.id) orders
   from amcservice.order_items oi
   join amcservice.orders o on o.id=oi.order_id
   left join amcservice.catalog_items ci on ci.id=oi.item_id and oi.item_kind='ITEM'
   where o.tenant_id=:t and o.created_at>=:start and o.created_at<:end
   group by oi.description,sale_type order by revenue desc,quantity desc
   """).param("t",u.tenantId()).param("start",selected.start()).param("end",selected.end()).query().listOfRows());
  result.put("chefs",db.sql("""
   select e.id,e.name,b.name branch_name,count(distinct j.id) jobs,
          count(distinct case when j.status='COMPLETED' then j.id end) completed
   from amcservice.employees e
   join amcservice.branches b on b.id=e.branch_id
   left join amcservice.job_card_chefs jc on jc.employee_id=e.id
   left join amcservice.job_cards j on j.id=jc.job_card_id and j.created_at>=:start and j.created_at<:end
   where e.tenant_id=:t and e.job_role='CHEF'
   group by e.id,b.name order by jobs desc,e.name
   """).param("t",u.tenantId()).param("start",selected.start()).param("end",selected.end()).query().listOfRows());
  return result;
 }

 private Range range(String period){
  var today=LocalDate.now();
  var monthStart=today.withDayOfMonth(1);
  var quarterStart=LocalDate.of(today.getYear(),((today.getMonthValue()-1)/3)*3+1,1);
  return switch(period){
   case "WEEK"->new Range(today.minusDays(6),today.plusDays(1));
   case "TWO_WEEKS"->new Range(today.minusDays(13),today.plusDays(1));
   case "QUARTER"->new Range(quarterStart.minusMonths(3),quarterStart);
   case "TWO_QUARTERS"->new Range(quarterStart.minusMonths(6),quarterStart);
   case "YEAR"->new Range(LocalDate.of(today.getYear()-1,1,1),LocalDate.of(today.getYear(),1,1));
   default->new Range(monthStart.minusMonths(1),monthStart);
  };
 }

 private Map<String,Object> summary(CurrentUser u,Range range){
  return db.sql("select coalesce(sum(total),0) sales,count(*) orders,coalesce(sum(amount_paid),0) collected,coalesce(sum(total-amount_paid),0) outstanding from amcservice.orders where tenant_id=:t and created_at>=:start and created_at<:end")
   .param("t",u.tenantId()).param("start",range.start()).param("end",range.end()).query().listOfRows().getFirst();
 }
}
