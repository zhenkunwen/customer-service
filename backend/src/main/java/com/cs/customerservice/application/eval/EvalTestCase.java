package com.cs.customerservice.application.eval;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTestCase {
    private String id;
    private String query;
    private List<String> expectedChunkIds;
    private String expectedAnswer;
    private String tenantId;
}
