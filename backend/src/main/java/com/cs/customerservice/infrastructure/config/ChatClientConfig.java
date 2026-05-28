package com.cs.customerservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient summaryChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public RestClient.Builder restClientBuilder(ObjectMapper objectMapper) {
        return RestClient.builder()
                .messageConverters(converters -> {
                    for (int i = 0; i < converters.size(); i++) {
                        if (converters.get(i) instanceof MappingJackson2HttpMessageConverter) {
                            converters.set(i, new MappingJackson2HttpMessageConverter(objectMapper));
                        }
                    }
                });
    }
}
