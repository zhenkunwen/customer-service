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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return Mono.fromRunnable(() -> {
            List<AgentEntity> onlineAgents = agentRepository
                    .findByRoleAndStatus("AGENT", "ONLINE");
            if (onlineAgents.isEmpty()) {
                log.warn("No online agent available for ticket id={}", ticket.getId());
                return;
            }

            // 1. 按注册时间倒序排序（最新注册优先）
            onlineAgents.sort(Comparator.comparing(AgentEntity::getCreatedAt).reversed());

            // 2. 查询所有在线客服的当前已分配工单数
            List<Long> agentIds = onlineAgents.stream()
                    .map(AgentEntity::getId)
                    .collect(Collectors.toList());
            Map<Long, Long> loadMap = ticketRepository.countAssignedByAgentIds(agentIds)
                    .stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));

            // 3. 选最新注册且有剩余容量的客服
            AgentEntity selected = onlineAgents.stream()
                    .filter(a -> loadMap.getOrDefault(a.getId(), 0L) < a.getMaxConcurrent())
                    .findFirst()
                    .orElseGet(() -> // 全部满负载？选负载最低的
                            onlineAgents.stream()
                                    .min(Comparator.comparingLong(a -> loadMap.getOrDefault(a.getId(), 0L)))
                                    .orElse(onlineAgents.get(0)));

            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(selected.getId());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Assigned ticket id={} to agent={} (createdAt={}, load={}/{})",
                    ticket.getId(), selected.getUsername(), selected.getCreatedAt(),
                    loadMap.getOrDefault(selected.getId(), 0L), selected.getMaxConcurrent());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
