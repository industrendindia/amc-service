package in.sevasetu.amc.security;
import java.util.UUID;
public record CurrentUser(UUID userId,UUID tenantId,String username,String displayName,String role){}
