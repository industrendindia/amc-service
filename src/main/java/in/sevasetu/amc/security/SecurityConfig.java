package in.sevasetu.amc.security;
import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration @EnableMethodSecurity public class SecurityConfig{
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain chain(HttpSecurity h,JwtFilter f)throws Exception{return h.csrf(x->x.disable()).cors(x->{}).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(x->x.requestMatchers("/api/auth/login","/actuator/health").permitAll().anyRequest().authenticated()).addFilterBefore(f,UsernamePasswordAuthenticationFilter.class).build();}
}
