package com.cs.customerservice.application.ticket;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.entity.TicketEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import com.cs.customerservice.infrastructure.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class TicketAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAssignmentService.class);
    private final AgentRepository agentRepository;
    private final TicketRepository ticketRepository;

    public TicketAssignmentService(AgentRepository agentRepository, TicketRepository ticketRepository) {
        this.agentRepository = agentRepository;
        this.ticketRepository = ticketRepository;
    }

    public Mono<Void> autoAssign(TicketEntity ticket) {
        if (ticket.getPriority() < 3) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            List<AgentEntity> onlineAgents = agentRepository
                    .findByRoleAndStatus("AGENT", "ONLINE");
            if (onlineAgents.isEmpty()) {
                log.warn("No online agent available for L3 ticket id={}", ticket.getId());
                return;
            }
            AgentEntity leastLoaded = onlineAgents.get(0);
            long minLoad = Long.MAX_VALUE;
            for (AgentEntity agent : onlineAgents) {
                long load = ticketRepository.countByAssignedAgentIdAndStatusIn(
                        agent.getId(), List.of("ASSIGNED", "IN_PROGRESS"));
                if (load < minLoad) {
                    minLoad = load;
                    leastLoaded = agent;
                }
            }
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(leastLoaded.getId());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Auto-assigned L3 ticket id={} to agent={} (load={})",
                    ticket.getId(), leastLoaded.getUsername(), minLoad);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
