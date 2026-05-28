package com.cs.customerservice.application.agent;

import com.cs.customerservice.api.dto.AgentLoadResponse;
import com.cs.customerservice.api.dto.AgentLoginRequest;
import com.cs.customerservice.api.dto.AgentLoginResponse;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import com.cs.customerservice.infrastructure.repository.TicketRepository;
import com.cs.customerservice.infrastructure.security.AgentTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private final AgentRepository agentRepository;
    private final TicketRepository ticketRepository;
    private final AgentTokenService tokenService;

    public AgentService(AgentRepository agentRepository, TicketRepository ticketRepository,
                        AgentTokenService tokenService) {
        this.agentRepository = agentRepository;
        this.ticketRepository = ticketRepository;
        this.tokenService = tokenService;
    }

    public Mono<AgentLoginResponse> login(AgentLoginRequest request) {
        return tokenService.login(request.getUsername(), request.getPassword())
                .flatMap(token -> Mono.fromCallable(() ->
                        agentRepository.findByUsername(request.getUsername()).orElseThrow()
                ).subscribeOn(Schedulers.boundedElastic())
                        .map(agent -> AgentLoginResponse.builder()
                                .token(token)
                                .role(agent.getRole())
                                .username(agent.getUsername())
                                .build()));
    }

    public Mono<Void> logout(String token) {
        return tokenService.logout(token);
    }

    public Mono<List<AgentLoadResponse>> listAgentLoads() {
        return Mono.fromCallable(() -> {
            List<AgentEntity> agents = agentRepository.findAll();
            return agents.stream().map(agent -> {
                long activeCount = ticketRepository.countByAssignedAgentIdAndStatusIn(
                        agent.getId(), List.of("ASSIGNED", "IN_PROGRESS"));
                return AgentLoadResponse.builder()
                        .id(agent.getId())
                        .username(agent.getUsername())
                        .role(agent.getRole())
                        .status(agent.getStatus())
                        .currentLoad(activeCount)
                        .build();
            }).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AgentEntity> register(String username, String rawPassword, String role) {
        return Mono.fromCallable(() -> {
            if (agentRepository.findByUsername(username).isPresent()) {
                throw new IllegalArgumentException("用户名已存在");
            }
            AgentEntity agent = AgentEntity.builder()
                    .username(username)
                    .passwordHash(tokenService.encodePassword(rawPassword))
                    .role(role)
                    .status("OFFLINE")
                    .build();
            agentRepository.save(agent);
            log.info("Agent registered: username={}, role={}", username, role);
            return agent;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
