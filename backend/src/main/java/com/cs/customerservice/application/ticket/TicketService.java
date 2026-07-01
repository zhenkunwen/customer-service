package com.cs.customerservice.application.ticket;

import com.cs.customerservice.api.dto.ChatRecordResponse;
import com.cs.customerservice.api.dto.TicketResponse;
import com.cs.customerservice.api.dto.TicketStatsResponse;
import com.cs.customerservice.api.dto.TicketUpdateRequest;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.entity.ChatRecord;
import com.cs.customerservice.infrastructure.entity.TicketEntity;
import com.cs.customerservice.infrastructure.entity.ChatRecordRepository;
import com.cs.customerservice.infrastructure.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private final TicketRepository ticketRepository;
    private final ChatRecordRepository chatRecordRepository;
    private final TicketAssignmentService ticketAssignmentService;

    public TicketService(TicketRepository ticketRepository, ChatRecordRepository chatRecordRepository,
                         TicketAssignmentService ticketAssignmentService) {
        this.ticketRepository = ticketRepository;
        this.chatRecordRepository = chatRecordRepository;
        this.ticketAssignmentService = ticketAssignmentService;
    }

    public Mono<Page<TicketResponse>> listByStatus(String status, String tenantId, Pageable pageable) {
        return Mono.fromCallable(() ->
                ticketRepository.findByStatusAndTenantId(status, tenantId, pageable)
                        .map(this::toDto)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Page<TicketResponse>> listByAgent(Long agentId, Pageable pageable) {
        return Mono.fromCallable(() ->
                ticketRepository.findByAssignedAgentId(agentId, pageable)
                        .map(this::toDto)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> findById(Long id) {
        return Mono.fromCallable(() ->
                ticketRepository.findById(id)
                        .map(this::toDto)
                        .orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> claim(Long ticketId, Long agentId) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!"PENDING".equals(ticket.getStatus())) {
                throw new IllegalStateException("工单状态不允许认领");
            }
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(agentId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket claimed: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> assign(Long ticketId, Long agentId) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(agentId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket assigned: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> resolve(Long ticketId, Long agentId, TicketUpdateRequest request, boolean isTeamLead) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!isTeamLead && !ticket.getAssignedAgentId().equals(agentId)) {
                throw new IllegalStateException("只能处理分配给自己的工单");
            }
            ticket.setStatus("RESOLVED");
            ticket.setResolution(request.getResolution());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket resolved: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> close(Long ticketId, Long agentId, boolean isTeamLead) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!isTeamLead && !agentId.equals(ticket.getAssignedAgentId())) {
                throw new IllegalStateException("只能关闭分配给自己的工单");
            }
            ticket.setStatus("CLOSED");
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket closed: id={}, by agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketStatsResponse> stats() {
        return Mono.fromCallable(() -> {
            List<Object[]> rows = ticketRepository.countByStatus();
            long total = 0;
            long pending = 0, assigned = 0, inProgress = 0, resolved = 0;
            for (Object[] row : rows) {
                String status = (String) row[0];
                long count = (Long) row[1];
                total += count;
                switch (status) {
                    case "PENDING" -> pending = count;
                    case "ASSIGNED" -> assigned = count;
                    case "IN_PROGRESS" -> inProgress = count;
                    case "RESOLVED" -> resolved = count;
                }
            }
            return TicketStatsResponse.builder()
                    .pendingCount(pending).assignedCount(assigned)
                    .inProgressCount(inProgress).resolvedCount(resolved)
                    .totalCount(total).build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(Long ticketId) {
        return Mono.fromRunnable(() -> {
            if (!ticketRepository.existsById(ticketId)) {
                throw new IllegalArgumentException("工单不存在");
            }
            ticketRepository.deleteById(ticketId);
            log.info("Ticket deleted: id={}", ticketId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<TicketEntity> save(TicketEntity ticket) {
        return Mono.fromCallable(() -> ticketRepository.save(ticket))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> create(String sessionId, String tenantId, String question,
                                        String emotionLevel, String topic, int priority) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = TicketEntity.builder()
                    .tenantId(tenantId)
                    .sessionId(sessionId)
                    .question(question)
                    .emotionLevel(emotionLevel)
                    .topic(topic)
                    .priority(priority)
                    .status("PENDING")
                    .build();
            TicketEntity saved = ticketRepository.save(ticket);
            log.info("Ticket created: id={}, priority={}", saved.getId(), priority);
            return saved;
        }).flatMap(saved ->
                ticketAssignmentService.autoAssign(saved)
                        .then(Mono.fromCallable(() -> toDto(
                                ticketRepository.findById(saved.getId())
                                        .orElse(saved)
                        )))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<ChatRecordResponse>> getChatHistory(Long ticketId) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            return chatRecordRepository.findBySessionIdOrderByCreatedAtAsc(ticket.getSessionId())
                    .stream().map(this::toChatDto).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Map<String, Object>> addAgentMessage(Long ticketId, AgentEntity agent, String message) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            ChatRecord record = ChatRecord.builder()
                    .sessionId(ticket.getSessionId())
                    .tenantId(ticket.getTenantId())
                    .userId("agent:" + agent.getUsername())
                    .model("__agent__")
                    .question("")
                    .answer(message)
                    .latencyMs(0L)
                    .status("AGENT_MSG")
                    .createdAt(Instant.now())
                    .build();
            chatRecordRepository.save(record);
            log.info("Agent message sent: ticketId={}, agent={}", ticketId, agent.getUsername());
            Map<String, Object> result = new HashMap<>();
            result.put("id", record.getId());
            result.put("message", message);
            result.put("sender", agent.getUsername());
            result.put("createdAt", record.getCreatedAt().toString());
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ChatRecordResponse toChatDto(ChatRecord r) {
        return ChatRecordResponse.builder()
                .id(r.getId()).userId(r.getUserId()).model(r.getModel())
                .question(r.getQuestion()).answer(r.getAnswer())
                .latencyMs(r.getLatencyMs()).status(r.getStatus())
                .createdAt(r.getCreatedAt()).build();
    }

    private TicketResponse toDto(TicketEntity e) {
        return TicketResponse.builder()
                .id(e.getId()).transferEventId(e.getTransferEventId())
                .tenantId(e.getTenantId()).sessionId(e.getSessionId())
                .question(e.getQuestion()).emotionLevel(e.getEmotionLevel())
                .topic(e.getTopic()).priority(e.getPriority())
                .status(e.getStatus()).assignedAgentId(e.getAssignedAgentId())
                .aiAttemptedSolutions(e.getAiAttemptedSolutions())
                .resolution(e.getResolution())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
