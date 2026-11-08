package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/customers")
public class CustomerController {
 private final JdbcClient db; public CustomerController(JdbcClient db){this.db=db;}
 public record Customer(UUID id,@NotBlank String name,@Pattern(regexp="[0-9+ -]{10,16}") String phone,String address,LocalDate dob,LocalDate anniversary,boolean marketingOptIn,boolean orderUpdatesOptIn){}
 @GetMapping List<Customer> search(@AuthenticationPrincipal CurrentUser u,@RequestParam(defaultValue="") String q){tenant(u);return db.sql("select id,name,phone,address,dob,anniversary,marketing_opt_in,order_updates_opt_in from amcservice.customers where tenant_id=:t and (lower(name) like lower(:q) or phone like :q) order by lifetime_value desc,name limit 50").param("t",u.tenantId()).param("q","%"+q+"%").query(Customer.class).list();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Customer create(@AuthenticationPrincipal CurrentUser u,@Valid @RequestBody Customer c){tenant(u);var id=UUID.randomUUID();db.sql("insert into amcservice.customers(id,tenant_id,name,phone,address,dob,anniversary,marketing_opt_in,order_updates_opt_in) values(:id,:t,:name,:phone,:address,:dob,:anniversary,:marketing,:updates) on conflict(tenant_id,phone) do update set name=excluded.name,address=excluded.address,dob=excluded.dob,anniversary=excluded.anniversary,marketing_opt_in=excluded.marketing_opt_in,order_updates_opt_in=excluded.order_updates_opt_in").param("id",id).param("t",u.tenantId()).param("name",c.name()).param("phone",c.phone()).param("address",c.address()).param("dob",c.dob()).param("anniversary",c.anniversary()).param("marketing",c.marketingOptIn()).param("updates",c.orderUpdatesOptIn()).update();return find(u,c.phone());}
 @PutMapping("/{id}") Customer update(@AuthenticationPrincipal CurrentUser u,@PathVariable UUID id,@Valid @RequestBody Customer c){tenant(u);int changed=db.sql("update amcservice.customers set name=:name,phone=:phone,address=:address,dob=:dob,anniversary=:anniversary,marketing_opt_in=:marketing,order_updates_opt_in=:updates where id=:id and tenant_id=:t").param("name",c.name()).param("phone",c.phone()).param("address",c.address()).param("dob",c.dob()).param("anniversary",c.anniversary()).param("marketing",c.marketingOptIn()).param("updates",c.orderUpdatesOptIn()).param("id",id).param("t",u.tenantId()).update();if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found");return find(u,c.phone());}
 private Customer find(CurrentUser u,String phone){return db.sql("select id,name,phone,address,dob,anniversary,marketing_opt_in,order_updates_opt_in from amcservice.customers where tenant_id=:t and phone=:phone").param("t",u.tenantId()).param("phone",phone).query(Customer.class).single();}
 static void tenant(CurrentUser u){if(u.tenantId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a tenant before accessing tenant data");}
}
