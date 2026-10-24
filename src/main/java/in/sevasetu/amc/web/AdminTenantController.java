package in.sevasetu.amc.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/admin/tenants") @PreAuthorize("hasRole('ADMIN')")
public class AdminTenantController {
 private final JdbcClient db; private final PasswordEncoder passwords;
 public AdminTenantController(JdbcClient db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;}
 public record BranchInput(UUID id,@NotBlank String code,@NotBlank @Pattern(regexp="[A-Za-z0-9]{1,8}") String documentCode,@NotBlank String name,String address,String phone,Boolean active){}
 public record Create(@NotBlank String code,@NotBlank String name,@NotBlank String domain,@NotBlank @Pattern(regexp="[A-Za-z0-9]{2,8}") String domainPrefix,@NotBlank @Pattern(regexp="[A-Za-z0-9]{2,12}") String documentCode,@NotBlank String themeKey,String logoUrl,@NotEmpty List<@Valid BranchInput> branches,@NotBlank String ownerName,@NotBlank String ownerUsername,@Size(min=8)String ownerPassword){}
 public record Update(@NotBlank String code,@NotBlank String name,@NotBlank String domain,@NotBlank @Pattern(regexp="[A-Za-z0-9]{2,8}") String domainPrefix,@NotBlank @Pattern(regexp="[A-Za-z0-9]{2,12}") String documentCode,@NotBlank String themeKey,String logoUrl,boolean active,@NotEmpty List<@Valid BranchInput> branches){}
 public record Tenant(UUID id,String code,String name,String domain,String domainPrefix,String documentCode,String themeKey,String logoUrl,boolean active,long branches,long employees){}

 @GetMapping List<Tenant> list(){return db.sql("select t.id,t.code,t.name,t.domain,t.domain_prefix,t.document_code,t.theme_key,t.logo_url,t.active,(select count(*) from amcservice.branches b where b.tenant_id=t.id),(select count(*) from amcservice.employees e where e.tenant_id=t.id) from amcservice.tenants t order by t.name").query((rs,n)->new Tenant(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getBoolean(9),rs.getLong(10),rs.getLong(11))).list();}

 @GetMapping("/{id}") Map<String,Object> detail(@PathVariable UUID id){var tenant=db.sql("select id,code,name,domain,domain_prefix,document_code,theme_key,logo_url,active from amcservice.tenants where id=:id").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found"));var result=new LinkedHashMap<String,Object>(tenant);result.put("branches",db.sql("select id,code,document_code,name,address,phone,active from amcservice.branches where tenant_id=:id order by created_at,name").param("id",id).query().listOfRows());return result;}

 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional Tenant create(@Valid @RequestBody Create r){
  var tenantId=UUID.randomUUID();
  try{db.sql("insert into amcservice.tenants(id,code,name,domain,domain_prefix,document_code,theme_key,logo_url) values(:id,:code,:name,:domain,:prefix,:document,:theme,:logo)").param("id",tenantId).param("code",upper(r.code())).param("name",r.name()).param("domain",upper(r.domain())).param("prefix",upper(r.domainPrefix())).param("document",upper(r.documentCode())).param("theme",r.themeKey()).param("logo",r.logoUrl()).update();}catch(Exception e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Company code or document configuration already exists");}
  UUID firstBranch=null; for(var branch:r.branches()){var id=insertBranch(tenantId,branch);if(firstBranch==null)firstBranch=id;}
  var userId=UUID.randomUUID();var employeeId=UUID.randomUUID();
  try{db.sql("insert into amcservice.users(id,tenant_id,branch_id,username,password_hash,display_name,role) values(:id,:t,:b,:username,:password,:name,'OWNER')").param("id",userId).param("t",tenantId).param("b",firstBranch).param("username",r.ownerUsername()).param("password",passwords.encode(r.ownerPassword())).param("name",r.ownerName()).update();}catch(Exception e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Owner user ID already exists");}
  db.sql("insert into amcservice.employees(id,tenant_id,branch_id,user_id,name,employee_type,job_role,date_of_joining) values(:id,:t,:b,:u,:name,'OWNER','OWNER',:joining)").param("id",employeeId).param("t",tenantId).param("b",firstBranch).param("u",userId).param("name",r.ownerName()).param("joining",LocalDate.now()).update();db.sql("update amcservice.users set employee_id=:e where id=:u").param("e",employeeId).param("u",userId).update();return tenant(tenantId);
 }

 @PutMapping("/{id}") @Transactional Tenant update(@PathVariable UUID id,@Valid @RequestBody Update r){
  if(db.sql("update amcservice.tenants set code=:code,name=:name,domain=:domain,domain_prefix=:prefix,document_code=:document,theme_key=:theme,logo_url=:logo,active=:active where id=:id").param("code",upper(r.code())).param("name",r.name()).param("domain",upper(r.domain())).param("prefix",upper(r.domainPrefix())).param("document",upper(r.documentCode())).param("theme",r.themeKey()).param("logo",r.logoUrl()).param("active",r.active()).param("id",id).update()==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found");
  var retained=new HashSet<UUID>(); for(var branch:r.branches()){if(branch.id()==null)retained.add(insertBranch(id,branch));else{if(db.sql("update amcservice.branches set code=:code,document_code=:document,name=:name,address=:address,phone=:phone,active=:active where id=:bid and tenant_id=:t").param("code",upper(branch.code())).param("document",upper(branch.documentCode())).param("name",branch.name()).param("address",branch.address()).param("phone",branch.phone()).param("active",branch.active()==null||branch.active()).param("bid",branch.id()).param("t",id).update()==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Branch does not belong to this company");retained.add(branch.id());}}
  return tenant(id);
 }

 private UUID insertBranch(UUID tenantId,BranchInput branch){var id=UUID.randomUUID();try{db.sql("insert into amcservice.branches(id,tenant_id,code,document_code,name,address,phone,active) values(:id,:t,:code,:document,:name,:address,:phone,:active)").param("id",id).param("t",tenantId).param("code",upper(branch.code())).param("document",upper(branch.documentCode())).param("name",branch.name()).param("address",branch.address()).param("phone",branch.phone()).param("active",branch.active()==null||branch.active()).update();return id;}catch(Exception e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch code or branch document code already exists");}}
 private Tenant tenant(UUID id){return db.sql("select t.id,t.code,t.name,t.domain,t.domain_prefix,t.document_code,t.theme_key,t.logo_url,t.active,(select count(*) from amcservice.branches b where b.tenant_id=t.id),(select count(*) from amcservice.employees e where e.tenant_id=t.id) from amcservice.tenants t where t.id=:id").param("id",id).query((rs,n)->new Tenant(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getBoolean(9),rs.getLong(10),rs.getLong(11))).single();}
 private static String upper(String value){return value.trim().toUpperCase(Locale.ROOT);}
}
