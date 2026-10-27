package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/orders")
public class OrderController {
 private final JdbcClient db; public OrderController(JdbcClient db){this.db=db;}
 public record Line(@NotNull UUID catalogItemId,@Min(1) int quantity,@DecimalMin("0") BigDecimal discount){}
 public record Create(@NotNull UUID customerId,@NotEmpty List<@Valid Line> items,@Pattern(regexp="UPI|CASH|ZERO|PENDING") String paymentMethod,@DecimalMin("0") BigDecimal initialPaymentAmount,Instant dueAt){}
 public record Created(UUID id,String billNumber,BigDecimal total){}
 public record Payment(@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal amount,@Pattern(regexp="UPI|CASH") String method,String referenceNumber){}
 public record PageResult(List<Map<String,Object>> content,int page,int size,long totalElements,int totalPages){}
 private record Priced(UUID id,String kind,String name,BigDecimal price,Integer months,int qty,BigDecimal discount){}
 private record DocumentConfig(String domainPrefix,String shopCode,String branchCode){}

 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 Created create(@AuthenticationPrincipal CurrentUser u,@Valid @RequestBody Create req){
  CustomerController.tenant(u);
  var customer=db.sql("select name from amcservice.customers where id=:id and tenant_id=:t").param("id",req.customerId()).param("t",u.tenantId()).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found"));
  var priced=new ArrayList<Priced>();
  for(var line:req.items()){
   var p=db.sql("select id,'ITEM' kind,name,price,duration_months from amcservice.catalog_items where id=:id and tenant_id=:t and active union all select id,'PACKAGE',name,price,duration_months from amcservice.packages where id=:id and tenant_id=:t and active").param("id",line.catalogItemId()).param("t",u.tenantId()).query((rs,n)->new Priced(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("name"),rs.getBigDecimal("price"),rs.getObject("duration_months",Integer.class),line.quantity(),line.discount()==null?BigDecimal.ZERO:line.discount())).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Catalog item is unavailable"));
   if(p.discount().compareTo(p.price())>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Discount cannot exceed item price"); priced.add(p);
  }
  var subtotal=priced.stream().map(p->p.price().multiply(BigDecimal.valueOf(p.qty()))).reduce(BigDecimal.ZERO,BigDecimal::add);
  var discount=priced.stream().map(p->p.discount().multiply(BigDecimal.valueOf(p.qty()))).reduce(BigDecimal.ZERO,BigDecimal::add);
  var total="ZERO".equals(req.paymentMethod())?BigDecimal.ZERO:subtotal.subtract(discount).max(BigDecimal.ZERO);
  var paid="PENDING".equals(req.paymentMethod())||"ZERO".equals(req.paymentMethod())?BigDecimal.ZERO:(req.initialPaymentAmount()==null?total:req.initialPaymentAmount());
  if(paid.compareTo(total)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Initial payment cannot exceed the bill total");
  var paymentStatus=total.signum()==0||paid.compareTo(total)==0?"PAID":paid.signum()>0?"PARTIAL":"PENDING";
  var storedMethod=paid.signum()==0&&total.signum()>0?"PENDING":req.paymentMethod();
  var branchId=u.branchId()!=null?u.branchId():db.sql("select id from amcservice.branches where tenant_id=:t and active order by created_at limit 1").param("t",u.tenantId()).query(UUID.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configure an active branch before creating an order"));
  var documentConfig=db.sql("select t.domain_prefix,t.document_code shop_code,b.document_code branch_code from amcservice.tenants t join amcservice.branches b on b.tenant_id=t.id where t.id=:t and b.id=:b").param("t",u.tenantId()).param("b",branchId).query(DocumentConfig.class).single();
  var orderId=UUID.randomUUID(); var documentNumber=db.sql("select nextval('amcservice.business_document_number_seq')").query(Long.class).single(); var bill=documentConfig.domainPrefix()+"-"+documentConfig.shopCode()+documentConfig.branchCode()+"-"+String.format("%06d",documentNumber);
  db.sql("insert into amcservice.orders(id,tenant_id,branch_id,customer_id,bill_number,subtotal,discount,total,payment_method,created_by,due_at,payment_status,amount_paid) values(:id,:t,:branch,:c,:bill,:sub,:disc,:total,:method,:user,:due,:ps,:paid)").param("id",orderId).param("t",u.tenantId()).param("branch",branchId).param("c",req.customerId()).param("bill",bill).param("sub",subtotal).param("disc",discount).param("total",total).param("method",storedMethod).param("user",u.userId()).param("due",req.dueAt()).param("ps",paymentStatus).param("paid",paid).update();
  for(var p:priced)db.sql("insert into amcservice.order_items(id,order_id,item_kind,item_id,description,unit_price,quantity,discount,duration_months,service_expires_on) values(:id,:order,:kind,:item,:name,:price,:qty,:discount,:months,:expiry)").param("id",UUID.randomUUID()).param("order",orderId).param("kind",p.kind()).param("item",p.id()).param("name",p.name()).param("price",p.price()).param("qty",p.qty()).param("discount",p.discount()).param("months",p.months()).param("expiry",p.months()==null?null:LocalDate.now().plusMonths(p.months())).update();
  if(paid.signum()>0||total.signum()==0)addPayment(orderId,u,paid,"ZERO".equals(req.paymentMethod())?"ZERO":req.paymentMethod(),null);
  db.sql("update amcservice.customers set lifetime_value=lifetime_value+:total where id=:id and tenant_id=:t").param("total",total).param("id",req.customerId()).param("t",u.tenantId()).update();
  var jobNumber=documentConfig.domainPrefix()+"-"+documentConfig.shopCode()+documentConfig.branchCode()+"-J-"+String.format("%06d",db.sql("select nextval('amcservice.business_document_number_seq')").query(Long.class).single());
  db.sql("insert into amcservice.job_cards(id,tenant_id,branch_id,order_id,job_number,title,notes,due_at,priority,status,created_by) values(:id,:t,:b,:o,:job,:title,:notes,:due,'NORMAL','OPEN',:u)").param("id",UUID.randomUUID()).param("t",u.tenantId()).param("b",branchId).param("o",orderId).param("job",jobNumber).param("title","Prepare "+bill+" for "+customer).param("notes","Automatically created from the customer order").param("due",req.dueAt()).param("u",u.userId()).update();
  return new Created(orderId,bill,total);
 }

 @GetMapping PageResult list(@AuthenticationPrincipal CurrentUser u,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="10") @Min(1) @Max(50) int size,@RequestParam(defaultValue="") String q){
  CustomerController.tenant(u); var search="%"+q.trim()+"%";
  var count=db.sql("select count(*) from amcservice.orders o join amcservice.customers c on c.id=o.customer_id where o.tenant_id=:t and (o.bill_number ilike :q or c.name ilike :q or c.phone ilike :q)").param("t",u.tenantId()).param("q",search).query(Long.class).single();
  var rows=db.sql("select o.id,o.bill_number,o.total,o.amount_paid,o.payment_method,o.payment_status,o.fulfillment_status,o.due_at,o.completed_at,o.delivered_at,o.created_at,c.name customer_name,c.phone customer_phone,b.name branch_name,(select count(*) from amcservice.order_reference_images ri where ri.order_id=o.id) reference_image_count from amcservice.orders o join amcservice.customers c on c.id=o.customer_id left join amcservice.branches b on b.id=o.branch_id where o.tenant_id=:t and (o.bill_number ilike :q or c.name ilike :q or c.phone ilike :q) order by o.created_at desc limit :size offset :offset").param("t",u.tenantId()).param("q",search).param("size",size).param("offset",page*size).query().listOfRows();
  return new PageResult(rows,page,size,count,(int)Math.ceil((double)count/size));
 }

 @GetMapping("/{id}") Map<String,Object> detail(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id){
  CustomerController.tenant(u); var order=db.sql("select o.*,c.name customer_name,c.phone customer_phone,c.address customer_address,b.name branch_name from amcservice.orders o join amcservice.customers c on c.id=o.customer_id left join amcservice.branches b on b.id=o.branch_id where o.id=:id and o.tenant_id=:t").param("id",id).param("t",u.tenantId()).query().listOfRows().stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found"));
  var result=new LinkedHashMap<String,Object>(order); result.put("items",db.sql("select id,item_kind,description,unit_price,quantity,discount,duration_months,service_expires_on from amcservice.order_items where order_id=:id order by description").param("id",id).query().listOfRows()); result.put("payments",db.sql("select id,amount,method,reference_number,paid_at from amcservice.order_payments where order_id=:id and tenant_id=:t order by paid_at").param("id",id).param("t",u.tenantId()).query().listOfRows()); result.put("images",imageList(u,id)); return result;
 }

 @PostMapping("/{id}/payments") @Transactional Map<String,Object> payment(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Payment p){
  CustomerController.tenant(u); var order=db.sql("select total,amount_paid from amcservice.orders where id=:id and tenant_id=:t for update").param("id",id).param("t",u.tenantId()).query().listOfRows().stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found")); BigDecimal total=(BigDecimal)order.get("total"),current=(BigDecimal)order.get("amount_paid");
  if(current.add(p.amount()).compareTo(total)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment exceeds outstanding amount"); addPayment(id,u,p.amount(),p.method(),p.referenceNumber()); var next=current.add(p.amount()); var status=next.compareTo(total)>=0?"PAID":"PARTIAL"; db.sql("update amcservice.orders set amount_paid=:paid,payment_status=:status,payment_method=:method,updated_at=now() where id=:id").param("paid",next).param("status",status).param("method",p.method()).param("id",id).update(); return Map.of("amountPaid",next,"paymentStatus",status,"balance",total.subtract(next));
 }
 private void addPayment(UUID orderId,CurrentUser u,BigDecimal amount,String method,String reference){db.sql("insert into amcservice.order_payments(id,tenant_id,order_id,amount,method,reference_number,received_by) values(:id,:t,:o,:amount,:method,:reference,:u)").param("id",UUID.randomUUID()).param("t",u.tenantId()).param("o",orderId).param("amount",amount).param("method",method).param("reference",reference).param("u",u.userId()).update();}

 @PostMapping(value="/{id}/reference-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) void images(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@RequestPart("files") List<MultipartFile> files)throws Exception{CustomerController.tenant(u);if(db.sql("select count(*) from amcservice.orders where id=:id and tenant_id=:t").param("id",id).param("t",u.tenantId()).query(Integer.class).single()==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");if(files.isEmpty()||files.size()>10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Upload between 1 and 10 images");for(var file:files){if(file.getContentType()==null||!file.getContentType().startsWith("image/")||file.getSize()>5_000_000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each reference must be an image under 5 MB");db.sql("insert into amcservice.order_reference_images(id,tenant_id,order_id,file_name,content_type,file_size,image_data,uploaded_by) values(:id,:t,:o,:name,:type,:size,:data,:u)").param("id",UUID.randomUUID()).param("t",u.tenantId()).param("o",id).param("name",Optional.ofNullable(file.getOriginalFilename()).orElse("reference")).param("type",file.getContentType()).param("size",file.getSize()).param("data",file.getBytes()).param("u",u.userId()).update();}}
 public record ImageInfo(UUID id,String fileName,String contentType,long fileSize){}
 @GetMapping("/{id}/reference-images") List<ImageInfo> imageList(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id){CustomerController.tenant(u);return db.sql("select id,file_name,content_type,file_size from amcservice.order_reference_images where tenant_id=:t and order_id=:o order by created_at").param("t",u.tenantId()).param("o",id).query(ImageInfo.class).list();}
 @GetMapping("/{orderId}/reference-images/{imageId}") ResponseEntity<byte[]> image(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID orderId,@PathVariable UUID imageId){CustomerController.tenant(u);return db.sql("select content_type,image_data from amcservice.order_reference_images where id=:id and order_id=:o and tenant_id=:t").param("id",imageId).param("o",orderId).param("t",u.tenantId()).query((rs,n)->ResponseEntity.ok().contentType(MediaType.parseMediaType(rs.getString(1))).cacheControl(CacheControl.noStore()).body(rs.getBytes(2))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Image not found"));}
}
