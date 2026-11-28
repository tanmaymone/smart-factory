package com.factory.central.access;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ActorUserDetailsService implements UserDetailsService {

    private final ActorRepository actorRepository;

    public ActorUserDetailsService(ActorRepository actorRepository) {
        this.actorRepository = actorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String actorId = username == null ? "" : username.trim();
        ActorEntity actor =
                actorRepository.findById(actorId).orElseThrow(() -> new UsernameNotFoundException(actorId));
        if (actor.getPasswordHash() == null || actor.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException(actorId);
        }
        return new User(
                actor.getActorId(),
                actor.getPasswordHash(),
                actor.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name())));
    }
}