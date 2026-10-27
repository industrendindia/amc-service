package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/job-cards")
public class JobCardController {
 private final JdbcClient db; public JobCardController(JdbcClient db){this.db=db;}
 public record Assign(@NotNull List<UUID> employeeIds){}
 public record Note(@NotBlank @Size(max=4000) String note){}
 public record PageResult(List<Map<String,Object>> content,int page,int size,long totalElements,int totalPages,Map<String,Long> statusCounts){}

 @GetMapping PageResult list(@AuthenticationPrincipal CurrentUser u,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="") String status){
  CustomerController.tenant(u);
  var filter=switch(status){case "OPEN"->" and j.status='OPEN'";case "ASSIGNED"->" and j.status='ASSIGNED'";case "IN_PROGRESS"->" and j.status in ('IN_PROGRESS','READY')";case "COMPLETED"->" and j.status='COMPLETED'";default->"";};
  var size=5;var total=db.sql("select count(*) from amcservice.job_cards j where j.tenant_id=:t"+filter).param("t",u.tenantId()).query(Long.class).single();
  var rows=db.sql("select j.id,j.job_number,j.branch_id,b.name branch_name,j.order_id,o.bill_number,j.title,j.notes,j.due_at,j.priority,j.status,j.created_at,c.name customer_name,c.phone customer_phone,(select count(*) from amcservice.order_reference_images i where i.order_id=j.order_id) reference_image_count,coalesce((select string_agg(e.name,', ' order by e.name) from amcservice.job_card_chefs jc join amcservice.employees e on e.id=jc.employee_id where jc.job_card_id=j.id),'') chef_names from amcservice.job_cards j join amcservice.branches b on b.id=j.branch_id left join amcservice.orders o on o.id=j.order_id left join amcservice.customers c on c.id=o.customer_id where j.tenant_id=:t"+filter+" order by j.due_at nulls last,j.created_at desc limit :size offset :offset")
   .param("t",u.tenantId()).param("size",size).param("offset",page*size).query().listOfRows();
  var counts=new LinkedHashMap<String,Long>();db.sql("select case when status='READY' then 'IN_PROGRESS' else status end bucket,count(*) total from amcservice.job_cards where tenant_id=:t and status in ('OPEN','ASSIGNED','IN_PROGRESS','READY','COMPLETED') group by bucket").param("t",u.tenantId()).query().listOfRows().forEach(r->counts.put(String.valueOf(r.get("bucket")),((Number)r.get("total")).longValue()));
  return new PageResult(rows,page,size,total,(int)Math.ceil((double)total/size),counts);
 }

 @GetMapping("/{id}") Map<String,Object> detail(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id){
  CustomerController.tenant(u); var card=db.sql("select j.*,b.name branch_name,o.bill_number,o.fulfillment_status,c.name customer_name,c.phone customer_phone from amcservice.job_cards j join amcservice.branches b on b.id=j.branch_id left join amcservice.orders o on o.id=j.order_id left join amcservice.customers c on c.id=o.customer_id where j.id=:id and j.tenant_id=:t").param("id",id).param("t",u.tenantId()).query().listOfRows().stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job card not found"));
  var result=new LinkedHashMap<String,Object>(card);
  result.put("chefs",db.sql("select e.id,e.name,e.job_role,e.branch_id from amcservice.job_card_chefs jc join amcservice.employees e on e.id=jc.employee_id where jc.job_card_id=:id order by e.name").param("id",id).query().listOfRows());
  result.put("notesHistory",db.sql("select n.id,n.note,n.created_at,coalesce(e.name,u.display_name) author_name from amcservice.job_card_notes n join amcservice.users u on u.id=n.author_user_id left join amcservice.employees e on e.id=n.author_employee_id where n.job_card_id=:id and n.tenant_id=:t order by n.created_at").param("id",id).param("t",u.tenantId()).query().listOfRows());
  var orderId=(UUID)card.get("order_id"); result.put("images",orderId==null?List.of():db.sql("select id,file_name,content_type,file_size from amcservice.order_reference_images where order_id=:o and tenant_id=:t order by created_at").param("o",orderId).param("t",u.tenantId()).query().listOfRows()); return result;
 }

 @PutMapping("/{id}/chefs") @Transactional void assign(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Assign r){
  CustomerController.tenant(u);editable(u,id); var branch=db.sql("select branch_id from amcservice.job_cards where id=:id and tenant_id=:t").param("id",id).param("t",u.tenantId()).query(UUID.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job card not found"));
  for(var employeeId:r.employeeIds())if(db.sql("select count(*) from amcservice.employees where id=:e and tenant_id=:t and branch_id=:b and job_role='CHEF' and active").param("e",employeeId).param("t",u.tenantId()).param("b",branch).query(Integer.class).single()==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A selected chef is unavailable for this branch");
  db.sql("delete from amcservice.job_card_chefs where job_card_id=:id").param("id",id).update(); for(var employeeId:r.employeeIds())db.sql("insert into amcservice.job_card_chefs(job_card_id,employee_id,assigned_by) values(:j,:e,:u)").param("j",id).param("e",employeeId).param("u",u.userId()).update(); db.sql("update amcservice.job_cards set status=:s,updated_at=now() where id=:id").param("s",r.employeeIds().isEmpty()?"OPEN":"ASSIGNED").param("id",id).update();
 }

 @PostMapping("/{id}/notes") @ResponseStatus(HttpStatus.CREATED) void note(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Note r){CustomerController.tenant(u);editable(u,id);db.sql("insert into amcservice.job_card_notes(id,tenant_id,job_card_id,author_employee_id,author_user_id,note) values(:nid,:t,:j,(select employee_id from amcservice.users where id=:u),:u,:note)").param("nid",UUID.randomUUID()).param("t",u.tenantId()).param("j",id).param("u",u.userId()).param("note",r.note()).update();}

 @PatchMapping("/{id}/status") @Transactional void status(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@RequestParam @Pattern(regexp="OPEN|ASSIGNED|IN_PROGRESS|READY|COMPLETED|CANCELLED") String value){
  CustomerController.tenant(u);editable(u,id); var order=db.sql("select order_id from amcservice.job_cards where id=:id and tenant_id=:t").param("id",id).param("t",u.tenantId()).query(UUID.class).optional(); db.sql("update amcservice.job_cards set status=:s,updated_at=now() where id=:id and tenant_id=:t").param("s",value).param("id",id).param("t",u.tenantId()).update(); if(order.isPresent()){var fulfillment=switch(value){case "IN_PROGRESS"->"IN_PROGRESS";case "READY"->"READY";case "COMPLETED"->"COMPLETED";case "CANCELLED"->"CANCELLED";default->"NEW";};db.sql("update amcservice.orders set fulfillment_status=:s,completed_at=case when :s in ('READY','COMPLETED') then coalesce(completed_at,now()) else completed_at end,updated_at=now() where id=:o and tenant_id=:t").param("s",fulfillment).param("o",order.get()).param("t",u.tenantId()).update();}
 }
 private void editable(CurrentUser u,UUID id){var row=db.sql("select j.id,o.fulfillment_status from amcservice.job_cards j left join amcservice.orders o on o.id=j.order_id where j.id=:id and j.tenant_id=:t").param("id",id).param("t",u.tenantId()).query().listOfRows().stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job card not found"));if("DELIVERED".equals(row.get("fulfillment_status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivered job cards are read-only");}
}
