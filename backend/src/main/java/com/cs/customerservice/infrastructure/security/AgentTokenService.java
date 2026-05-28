package com.cs.customerservice.infrastructure.security;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.UUID;

@Service
public class AgentTokenService {

    private static final Logger log = LoggerFactory.getLogger(AgentTokenService.class);
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AgentTokenService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public Mono<String> login(String username, String password) {
        return Mono.fromCallable(() -> {
            Optional<AgentEntity> opt = agentRepository.findByUsername(username);
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            AgentEntity agent = opt.get();
            if (!passwordEncoder.matches(password, agent.getPasswordHash())) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            String token = UUID.randomUUID().toString();
            agent.setToken(token);
            agent.setStatus("ONLINE");
            agentRepository.save(agent);
            log.info("Agent login: username={}, role={}", username, agent.getRole());
            return token;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> logout(String token) {
        return Mono.fromRunnable(() -> {
            agentRepository.findByToken(token).ifPresent(agent -> {
                agent.setToken(null);
                agent.setStatus("OFFLINE");
                agentRepository.save(agent);
            });
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<AgentEntity> validate(String token) {
        return Mono.fromCallable(() ->
                agentRepository.findByToken(token).orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
