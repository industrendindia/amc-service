package in.sevasetu.amc.web;

import in.sevasetu.amc.security.CurrentUser;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/tenant")
public class TenantBrandingController {
 private final JdbcClient db; public TenantBrandingController(JdbcClient db){this.db=db;}
 @GetMapping("/logo") ResponseEntity<byte[]> logo(@AuthenticationPrincipal CurrentUser u){CustomerController.tenant(u);return db.sql("select logo_content_type,logo_data from amcservice.tenants where id=:t and logo_data is not null").param("t",u.tenantId()).query((rs,n)->ResponseEntity.ok().contentType(MediaType.parseMediaType(rs.getString(1))).cacheControl(CacheControl.noStore()).body(rs.getBytes(2))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Shop logo not configured"));}
}
