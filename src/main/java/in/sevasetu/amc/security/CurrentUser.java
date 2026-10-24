package in.sevasetu.amc.security;
import java.util.UUID;
public record CurrentUser(UUID userId,UUID tenantId,UUID branchId,String username,String displayName,String role){}
