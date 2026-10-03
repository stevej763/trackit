package dev.stevejones.trackit.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal carrying the user id, so request handlers can scope
 * queries without a lookup by username on every call.
 *
 * <p>Spring Session JDBC serialises the security context into Postgres, so this
 * must stay {@link Serializable} and its field layout stable &mdash; changing it
 * invalidates sessions created before the change.
 */
public class AppUserPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String passwordHash;

    public AppUserPrincipal(Long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal of(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
