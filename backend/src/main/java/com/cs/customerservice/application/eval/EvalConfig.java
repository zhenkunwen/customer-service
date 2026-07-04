package com.cs.customerservice.application.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalConfig {
    /** 检索 Top K，默认 5 */
    private Integer topK;

    public int getTopKOrDefault() {
        return topK != null ? topK : 5;
    }
}
